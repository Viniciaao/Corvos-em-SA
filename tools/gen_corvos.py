#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Monta o script novo (src/CORVOS.sc) a partir de duas partes:

  1. tools/corvos_logic.sc.txt  -> a logica escrita a mao, com o marcador
                                   {{PERCH_TABLE}} onde entra a tabela de areas;
  2. extracted/Corvos_do_GTA_V/CLEO/CROW1..CROW5.txt
                                -> os cinco scripts originais do mod, de onde a
                                   tabela (13 gatilhos + 64 poleiros) e extraida.

Tambem gera os relatorios de apoio:
  analysis/spots.csv   - a tabela em CSV (para planilha / conferencia)
  analysis/spots.md    - a mesma tabela em Markdown
  build/CLEO/CORVOS.ini- arquivo de configuracao que vai junto no pacote
"""
import csv
import os
import re
import shutil
import statistics
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIG = os.path.join(ROOT, 'extracted', 'Corvos_do_GTA_V', 'CLEO')
LOGIC = os.path.join(ROOT, 'tools', 'corvos_logic.sc.txt')
OUT_SC = os.path.join(ROOT, 'src', 'CORVOS.sc')
OUT_CSV = os.path.join(ROOT, 'analysis', 'spots.csv')
OUT_MD = os.path.join(ROOT, 'analysis', 'spots.md')
INI = os.path.join(ROOT, 'tools', 'CORVOS.ini')
OUT_INI = os.path.join(ROOT, 'build', 'CLEO', 'CORVOS.ini')

NUM = r'-?\d+\.?\d*'
FILES = ['CROW%d.txt' % i for i in range(1, 6)]
# areas 5..12 tem o gatilho longe dos poleiros: se um ponto ficar a mais de
# 300 m do centro do gatilho, ele veio de copy/paste errado (foi o caso do
# CROW1 na area 8) e e descartado.
STRICT_AREAS = (5, 6, 7, 8, 9, 10, 11, 12)
STRICT_DIST = 300.0


def parse_script(path):
    """Le um CROWn.txt e devolve {area: {in, not_in, onscreen, spawn, angle}}."""
    txt = open(path, encoding='latin1').read().replace('\r\n', '\n')
    areas = {}

    # blocos :NEAR_n  (gatilho + checagem de tela)
    blocks = re.split(r'^:(NEAR_\d+)\s*$', txt, flags=re.M)
    for i in range(1, len(blocks), 2):
        n = int(blocks[i].split('_')[1])
        body = blocks[i + 1]
        ins = re.search(r'00FE:\s+actor \$PLAYER_ACTOR sphere 0 in_sphere\s+(%s)\s+(%s)\s+(%s) radius (%s)' % (NUM, NUM, NUM, NUM), body)
        ex = re.search(r'80FE:\s+not actor \$PLAYER_ACTOR sphere 0 in_sphere\s+(%s)\s+(%s)\s+(%s) radius (%s)' % (NUM, NUM, NUM, NUM), body)
        sc = re.search(r'00C2:\s+sphere_onscreen\s+(%s)\s+(%s)\s+(%s)\s+radius (%s)' % (NUM, NUM, NUM, NUM), body)
        areas[n] = {
            'in': [float(x) for x in ins.groups()] if ins else None,
            'not_in': [float(x) for x in ex.groups()] if ex else None,
            'onscreen': [float(x) for x in sc.groups()] if sc else None,
            'spawn': None,
            'angle': None,
        }

    # blocos :START_n  (posicao do corvo)
    blocks = re.split(r'^:(START_\d+)\s*$', txt, flags=re.M)
    for i in range(1, len(blocks), 2):
        n = int(blocks[i].split('_')[1])
        m = re.search(r'16@\s*=\s*(%s)\s*\n17@\s*=\s*(%s)\s*\n18@\s*=\s*(%s)\s*\n20@\s*=\s*(%s)' % (NUM, NUM, NUM, NUM), blocks[i + 1])
        if m and n in areas:
            areas[n]['spawn'] = [float(x) for x in m.groups()[:3]]
            areas[n]['angle'] = float(m.group(4))
    return areas


def fmt(v):
    """Float com 4 casas e no minimo uma casa decimal (o gta3sc exige o ponto)."""
    s = '%.4f' % v
    if '.' in s:
        s = s.rstrip('0')
        if s.endswith('.'):
            s += '0'
    return '0.0' if s in ('-0.0', '') else s


def collect():
    """Devolve a lista de areas (gatilho, exclusao e poleiros)."""
    scripts = {}
    for name in FILES:
        path = os.path.join(ORIG, name)
        if not os.path.exists(path):
            sys.exit('nao achei %s' % path)
        scripts[name] = parse_script(path)

    areas = []
    for n in range(1, 14):
        ins = [s[n]['in'] for s in scripts.values() if n in s and s[n]['in']]
        if not ins:
            continue
        cen = [round(statistics.mean(c[i] for c in ins), 4) for i in range(3)]
        rad = max(c[3] for c in ins)
        ex = next((s[n]['not_in'] for s in scripts.values() if n in s and s[n]['not_in']), None)
        perches = []
        for name, s in scripts.items():
            a = s.get(n)
            if not a or not a['spawn']:
                continue
            p = {
                'src': name,
                'x': a['spawn'][0], 'y': a['spawn'][1], 'z': a['spawn'][2],
                'angle': a['angle'],
                'vis_r': a['onscreen'][3] if a['onscreen'] else 15.0,
            }
            d2 = (p['x'] - cen[0]) ** 2 + (p['y'] - cen[1]) ** 2
            if n in STRICT_AREAS and d2 > STRICT_DIST ** 2:
                print('  [aviso] %s area %d: poleiro a %.0f m do gatilho -> descartado'
                      % (name, n, d2 ** 0.5))
                continue
            perches.append(p)
        seen, uniq = set(), []
        for p in perches:
            key = (round(p['x'], 2), round(p['y'], 2), round(p['z'], 2), round(p['angle'], 2))
            if key in seen:
                continue
            seen.add(key)
            uniq.append(p)
        areas.append(dict(id=n, center=cen, radius=rad, exclude=ex, perches=uniq))
    return areas


def perch_table(areas):
    """Tabela de areas/poleiros na sintaxe do gta3sc (vai para dentro de cv_scan)."""
    lines = []
    lines.append('// Tabela gerada por tools/gen_corvos.py a partir de CROW1..CROW5.')
    lines.append('// Nao edite a mao: mexa em tools/corvos_logic.sc.txt e rode o gerador.')
    total = sum(len(a['perches']) for a in areas)
    lines.append('// %d areas, %d poleiros.' % (len(areas), total))
    for idx, a in enumerate(areas):
        nid = a['id']
        nxt = 'cv_area_%02d' % areas[idx + 1]['id'] if idx + 1 < len(areas) else 'cv_scan_end'
        lines.append('')
        lines.append('// ---- AREA %02d   gatilho (%.1f, %.1f, %.1f) raio %.0f   %d poleiro(s)'
                     % (nid, a['center'][0], a['center'][1], a['center'][2], a['radius'], len(a['perches'])))
        lines.append('cv_area_%02d:' % nid)
        cen, rad = a['center'], a['radius']
        lines.append('IF LOCATE_CHAR_ANY_MEANS_3D player %s %s %s %s %s %s 0'
                     % (fmt(cen[0]), fmt(cen[1]), fmt(cen[2]), fmt(rad), fmt(rad), fmt(rad)))
        if a['exclude']:
            e = a['exclude']
            lines.append('AND NOT LOCATE_CHAR_ANY_MEANS_3D player %s %s %s %s %s %s 0'
                         % (fmt(e[0]), fmt(e[1]), fmt(e[2]), fmt(e[3]), fmt(e[3]), fmt(e[3])))
        lines.append('    GOTO cv_area_%02d_p1' % nid)
        lines.append('ENDIF')
        lines.append('GOTO %s' % nxt)
        for j, p in enumerate(a['perches'], start=1):
            label = 'cv_area_%02d_p%d' % (nid, j)
            lines.append('%s:' % label)
            lines.append('IF IS_POINT_ON_SCREEN %s %s %s %s'
                         % (fmt(p['x']), fmt(p['y']), fmt(p['z']), fmt(p['vis_r'])))
            lines.append('    px = %s' % fmt(p['x']))
            lines.append('    py = %s' % fmt(p['y']))
            lines.append('    pz = %s' % fmt(p['z']))
            lines.append('    ang = %s' % fmt(p['angle']))
            lines.append('    GOSUB cv_spawn')
            lines.append('    RETURN')           # um corvo por quadro, no maximo
            lines.append('ENDIF')
        lines.append('GOTO %s' % nxt)
    lines.append('')
    return '\n'.join(lines)


def main():
    areas = collect()

    os.makedirs(os.path.dirname(OUT_CSV), exist_ok=True)
    with open(OUT_CSV, 'w', newline='', encoding='utf-8') as fh:
        w = csv.writer(fh, delimiter=';')
        w.writerow(['area', 'gatilho_x', 'gatilho_y', 'gatilho_z', 'gatilho_raio',
                    'exclusao_x', 'exclusao_y', 'exclusao_z', 'exclusao_raio',
                    'poleiro_x', 'poleiro_y', 'poleiro_z', 'poleiro_angulo',
                    'raio_visao', 'origem'])
        for a in areas:
            ex = a['exclude'] or ['', '', '', '']
            for p in a['perches']:
                w.writerow([a['id'], fmt(a['center'][0]), fmt(a['center'][1]), fmt(a['center'][2]), fmt(a['radius'])]
                           + [fmt(v) if v != '' else '' for v in ex]
                           + [fmt(p['x']), fmt(p['y']), fmt(p['z']), fmt(p['angle']), fmt(p['vis_r']), p['src']])

    # o arquivo de configuracao vai junto no pacote (dist/CLEO/CORVOS.ini);
    # se ele nao existir no jogo, o script cria um igualzinho na primeira vez
    os.makedirs(os.path.dirname(OUT_INI), exist_ok=True)
    shutil.copyfile(INI, OUT_INI)

    logic = open(LOGIC, encoding='utf-8').read()
    if '{{PERCH_TABLE}}' not in logic:
        sys.exit('marcador {{PERCH_TABLE}} nao encontrado em %s' % LOGIC)
    table = perch_table(areas)
    script = logic.replace('{{PERCH_TABLE}}', table)
    os.makedirs(os.path.dirname(OUT_SC), exist_ok=True)
    open(OUT_SC, 'w', encoding='utf-8', newline='\n').write(script)

    with open(OUT_MD, 'w', encoding='utf-8') as fh:
        fh.write('# Areas e poleiros do CORVOS.sc\n\n')
        fh.write('Extraido automaticamente dos cinco scripts originais (CROW1..CROW5).\n\n')
        fh.write('| area | gatilho (x, y, z) | raio | exclusao | poleiros | origens |\n')
        fh.write('|---|---|---|---|---|---|\n')
        for a in areas:
            ex = a['exclude']
            exs = ('(%.1f, %.1f, %.1f) r%.0f' % (ex[0], ex[1], ex[2], ex[3])) if ex else '-'
            fh.write('| %d | (%.1f, %.1f, %.1f) | %.0f | %s | %d | %s |\n'
                     % (a['id'], a['center'][0], a['center'][1], a['center'][2], a['radius'],
                        exs, len(a['perches']), ', '.join(p['src'] for p in a['perches'])))
        fh.write('\nTotal: %d poleiros em %d areas.\n' % (sum(len(a['perches']) for a in areas), len(areas)))

    print('gerado: %s (%d linhas)' % (OUT_SC, len(script.splitlines())))
    print('gerado: %s' % OUT_CSV)
    print('gerado: %s' % OUT_MD)
    print('gerado: %s' % OUT_INI)
    print('areas: %d   poleiros: %d' % (len(areas), sum(len(a['perches']) for a in areas)))


if __name__ == '__main__':
    main()
