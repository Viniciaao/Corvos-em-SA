#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Desmontador (disassembler) minimo de SCM do GTA SA, feito para conferir o
CORVOS.cs gerado pelo gta3sc.

Le a tabela de opcodes dos XML do proprio compilador e decodifica os
parametros pelo byte de tipo. Os enderecos sao os de INICIO de instrucao
(a mesma convencao do SCRLog) e os saltos (GOTO / GOTO_IF_FALSE) sao
mostrados em endereco absoluto, conferidos contra os limites das instrucoes.

Uso:
    python3 tools/scm_disasm.py arquivo.cs            # desmonta
    python3 tools/scm_disasm.py arquivo.cs --alvos    # so o mapa de saltos
"""
import sys, os, struct, xml.etree.ElementTree as ET

# O desmontador usa a tabela de opcodes do gta3sc (config/gtasa). Aponte
# GTA3SC_DIR para a pasta do compilador; o padrao e a cache do build.sh.
CFG_DIR = os.path.join(os.environ.get('GTA3SC_DIR', os.path.expanduser('~/.cache/gta3sc')),
                       'config', 'gtasa')
FILES = ['commands.xml', 'cleo.xml', 'sounds.xml', 'default.xml']

# tamanho do payload por byte de tipo (SA)
PAYLOAD = {
    0x01: ('int32', 4), 0x02: ('gvar', 2), 0x03: ('lvar', 2),
    0x04: ('int8', 1), 0x05: ('int16', 2), 0x06: ('float', 4),
    0x07: ('gvar_arr', 6), 0x08: ('lvar_arr', 6),
    0x09: ('str8', 8), 0x0A: ('gvar_str', 2), 0x0B: ('lvar_str', 2),
    0x0C: ('gvar_strarr', 6), 0x0D: ('lvar_strarr', 6),
    0x0E: ('pascal', 0), 0x0F: ('str16', 16),
}

GOTO_OPS = (0x0002, 0x004D, 0x0050)   # GOTO / GOTO_IF_FALSE / GOSUB


def load_commands():
    table = {}
    for f in FILES:
        p = os.path.join(CFG_DIR, f)
        if not os.path.exists(p):
            continue
        for cmd in ET.parse(p).getroot().iter('Command'):
            cid = cmd.get('ID')
            if not cid:
                continue
            args = [a.get('Type') for a in cmd.findall('Args/Arg')]
            table[int(cid, 16)] = (cmd.get('Name'), args)
    return table


def read_param(data, pos):
    """Le um parametro do script. Devolve (texto, novo_pos, tipo, valor_int32)."""
    t = data[pos]
    pos += 1
    if t == 0x0E:                      # string pascal (tamanho + texto)
        n = data[pos]
        pos += 1
        return repr(data[pos:pos + n].decode('latin1')), pos + n, t, None
    if t == 0x0F:                      # string longa (16 bytes)
        return (repr(data[pos:pos + 16].rstrip(b'\x00').decode('latin1')),
                pos + 16, t, None)
    kind, size = PAYLOAD.get(t, ('?%02X' % t, 0))
    raw = data[pos:pos + size]
    pos += size
    if kind in ('int8', 'int16', 'int32'):
        v = int.from_bytes(raw, 'little', signed=True)
        return '%d' % v, pos, t, v
    if kind == 'float':
        return '%.4f' % struct.unpack('<f', raw)[0], pos, t, None
    if kind == 'lvar':
        return '%d@' % int.from_bytes(raw, 'little'), pos, t, None
    if kind == 'gvar':
        return '$%d' % int.from_bytes(raw, 'little'), pos, t, None
    if kind in ('lvar_arr', 'gvar_arr'):
        b = int.from_bytes(raw[0:2], 'little')
        i = int.from_bytes(raw[2:4], 'little')
        n = raw[4]
        t2 = raw[5]
        et = {0: 'i', 1: 'f', 2: 's', 3: 'S'}.get(t2 & 0x7F, '?')
        g = 'g' if (t2 & 0x80) else ''
        return ('%d%s[%d@%s]%s(%d)'
                % (b, '$' if kind == 'gvar_arr' else '@', i, g, et, n), pos, t, None)
    if kind == 'str8':
        return repr(raw.rstrip(b'\x00').decode('latin1')), pos, t, None
    return '%s:%s' % (kind, raw.hex()), pos, t, None


def conta_formatos(fmt):
    """Conta os %d/%.0f/%s de uma string de formato (o %% duplo nao conta).

    O compilador usa justamente essa contagem para saber quantos parametros
    extras a instrucao leva, entao aqui tambem e' o jeito certo de saber onde a
    lista termina. Devolve None quando a string de formato nao e' literal.
    """
    if not fmt:
        return None
    n = 0
    i = 0
    while i < len(fmt):
        if fmt[i] == '%':
            if i + 1 < len(fmt) and fmt[i + 1] == '%':
                i += 2
                continue
            n += 1
        i += 1
    return n


def walk(data, table):
    """Percorre o arquivo e devolve [(inicio, fim, op, negado, nome, args_txt, salto_bruto)].

    O ultimo argumento declarado como PARAM no XML do gta3sc pode ser DUAS
    coisas:

    * um parametro so', de qualquer tipo (por exemplo o destino do READ_MEMORY,
      0A8D) -- o compilador escreve um unico valor e nenhum terminador;
    * uma lista (o texto formatado do CLEO, 0ACE / 0AD1 / 0AD3), aí o numero de
      valores sai da propria string de formato e o compilador fecha a lista com
      o marcador de fim (tipo 0x00).

    Tratar os dois casos como lista (ou como um valor so') desalinha a leitura:
    foi o que aconteceu com o READ_MEMORY antes desta correcao.
    """
    pos = 0
    out = []
    while pos < len(data):
        ini = pos
        raw_op = data[pos] | (data[pos + 1] << 8)
        pos += 2
        neg = bool(raw_op & 0x8000)
        op = raw_op & 0x7FFF
        name, args = table.get(op, ('<desconhecido %04X>' % op, []))
        if neg:
            name = 'NOT ' + name
        variadic = bool(args) and args[-1] == 'PARAM'
        fixos = args[:-1] if variadic else args
        vals = []
        jump = None
        fmt = None
        for tipo in fixos:
            if pos >= len(data):
                break
            txt, pos, t, v = read_param(data, pos)
            if op in GOTO_OPS and t == 0x01:
                jump = v
                txt = '->%d' % v             # resolvido depois
            if tipo == 'STRING' and t in (0x0E, 0x0F, 0x09):
                fmt = txt.strip("'")          # ultima string fixa = formato
            vals.append(txt)
        if variadic:
            n = conta_formatos(fmt)
            if fmt is not None and n is None:
                # string de formato nao literal: a lista vai ate o terminador
                while pos < len(data) and data[pos] != 0x00:
                    txt, pos, t, v = read_param(data, pos)
                    vals.append(txt)
                if pos < len(data) and data[pos] == 0x00:
                    pos += 1
            elif n:
                for _ in range(n):           # um valor por % da string
                    if pos >= len(data):
                        break
                    txt, pos, t, v = read_param(data, pos)
                    vals.append(txt)
                if pos < len(data) and data[pos] == 0x00:
                    pos += 1                 # marcador de fim da lista
            elif pos < len(data) and data[pos] == 0x00:
                pos += 1                     # formato sem nenhum %: lista vazia
            elif pos < len(data):
                # PARAM de um valor so' (0A8D READ_MEMORY, 0x4F START_NEW_SCRIPT,
                # 0xA92 STREAM_CUSTOM_SCRIPT...): um valor, sem terminador
                txt, pos, t, v = read_param(data, pos)
                vals.append(txt)
        out.append([ini, pos, op, neg, name, vals, jump])
    return out


def resolve(insns):
    """Resolve os saltos. Devolve (base_escolhida, [avisos])."""
    inicios = {i[0] for i in insns}
    fins = {i[1] for i in insns}
    # No bytecode do gta3sc o operando do salto e o NEGATIVO do endereco
    # absoluto do alvo (conferido contra o log do SCRLog no jogo: GOTO -938 ->
    # alvo 938 = 0x3AA, GOTO_IF_FALSE -344 -> alvo 344 = 0x158, GOSUB -647 ->
    # alvo 647 = 0x287). As outras duas bases ficam como conferencia.
    bases = {'negativo': 0, 'fim': 0, 'inicio': 0}
    avisos = []
    for ins in insns:
        if ins[6] is None:
            continue
        ini, fim, v = ins[0], ins[1], ins[6]
        ok = None
        for nome, alvo in (('negativo', -v), ('fim', fim + v), ('inicio', ini + v)):
            if alvo in inicios or alvo in fins:
                ok = nome
                bases[nome] += 1
                break
        if ok is None:
            avisos.append('salto em %06X nao cai em limite de instrucao (valor %d)' % (ini, v))
    escolhida = max(bases, key=lambda k: bases[k]) if any(bases.values()) else 'negativo'
    for ins in insns:
        if ins[6] is None:
            continue
        alvo = {'negativo': -ins[6], 'fim': ins[1] + ins[6], 'inicio': ins[0] + ins[6]}[escolhida]
        ins[5] = [('%06X' % alvo) if x.startswith('->') else x for x in ins[5]]
    return escolhida, bases, avisos


def main():
    path = sys.argv[1]
    so_alvos = '--alvos' in sys.argv
    data = open(path, 'rb').read()
    table = load_commands()
    insns = walk(data, table)
    base, votos, avisos = resolve(insns)
    if not so_alvos:
        print('; %s - %d bytes, %d instrucoes (saltos relativos ao %s da instrucao)'
              % (path, len(data), len(insns),
                 {'negativo': 'ALVO ABSOLUTO NEGADO', 'fim': 'FIM', 'inicio': 'INICIO'}[base]))
        for ins in insns:
            print('%06X  %04X  %-34s %s' % (ins[0], ins[2], ins[4], ' '.join(ins[5])))
    print('; votos de base: %s' % votos)
    for a in avisos[:20]:
        print('; AVISO: %s' % a)
    if avisos:
        print('; %d salto(s) suspeito(s)' % len(avisos))


if __name__ == '__main__':
    main()
