#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Desmontador (disassembler) minimo de SCM do GTA SA, feito para conferir o
CORVOS.cs gerado pelo gta3sc. Le a tabela de opcodes dos XML do proprio
compilador e decodifica os parametros pelo byte de tipo.
"""
import sys, os, xml.etree.ElementTree as ET

# O desmontador usa a tabela de opcodes do gta3sc (config/gtasa). Aponte
# GTA3SC_DIR para a pasta do compilador; o padrao e a cache do build.sh.
CFG_DIR = os.path.join(os.environ.get('GTA3SC_DIR', os.path.expanduser('~/.cache/gta3sc')), 'config', 'gtasa')
FILES = ['commands.xml', 'cleo.xml', 'sounds.xml', 'default.xml']

# tamanho do payload por byte de tipo (SA)
PAYLOAD = {
    0x01: ('int32', 4), 0x02: ('gvar', 2), 0x03: ('lvar', 2),
    0x04: ('int8', 1), 0x05: ('int16', 2), 0x06: ('float', 4),
    0x07: ('gvar_arr', 6), 0x08: ('lvar_arr', 6),
    0x09: ('str8', 8), 0x0A: ('gvar_str', 2), 0x0B: ('lvar_str', 2),
    0x0C: ('gvar_strarr', 6), 0x0D: ('lvar_strarr', 6),
    0x0E: ('lvar_str_long', 2), 0x0F: ('elemtype_pad', 1),
}


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


def decode(data, table, start=0, end=None, base_off=0):
    """Desmonta [start,end) e devolve (linhas, bytes_nao_decodificados)."""
    end = len(data) if end is None else end
    pos = start
    out = []
    while pos < end:
        raw_op = data[pos] | (data[pos + 1] << 8)
        pos += 2
        neg = bool(raw_op & 0x8000)
        op = raw_op & 0x7FFF
        name, args = table.get(op, ('<desconhecido %04X>' % op, []))
        if neg:
            name = 'NOT ' + name
        vals = []
        for _ in args:
            if pos >= len(data):
                break
            t = data[pos]; pos += 1
            if t == 0x0E:                      # pascal string
                n = data[pos]; pos += 1
                vals.append(repr(data[pos:pos + n].decode('latin1'))); pos += n
                continue
            if t == 0x0F:                      # long string (16 bytes)
                vals.append(repr(data[pos:pos + 16].rstrip(b'\x00').decode('latin1'))); pos += 16
                continue
            kind, size = PAYLOAD.get(t, ('?%02X' % t, 0))
            raw = data[pos:pos + size]
            pos += size
            if kind in ('int8', 'int16', 'int32'):
                v = int.from_bytes(raw, 'little', signed=False)
                if len(raw) == 4 and v >= 0x80000000 and op in (0x0002, 0x004D):
                    v = v - 0x100000000 - 2   # GOTO/GOTO_IF_FALSE: offset relativo ao fim da instrucao
                    vals.append('-> %06X' % (pos + v))
                else:
                    vals.append('%d' % v)
            elif kind == 'float':
                import struct
                vals.append('%.4f' % struct.unpack('<f', raw)[0])
            elif kind == 'lvar':
                vals.append('%d@' % int.from_bytes(raw, 'little'))
            elif kind == 'gvar':
                vals.append('$%d' % int.from_bytes(raw, 'little'))
            elif kind in ('lvar_arr', 'gvar_arr'):
                b = int.from_bytes(raw[0:2], 'little')
                i = int.from_bytes(raw[2:4], 'little')
                n = raw[4]
                t2 = raw[5]
                et = {0: 'i', 1: 'f', 2: 's', 3: 'S'}.get(t2 & 0x7F, '?')
                g = 'g' if (t2 & 0x80) else ''
                vals.append('%d%s[%d@%s]%s(%d)' % (b, '$' if kind == 'gvar_arr' else '@', i, g, et, n))
            elif kind == 'str8':
                vals.append(repr(raw.rstrip(b'\x00').decode('latin1')))
            else:
                vals.append('%s:%s' % (kind, raw.hex()))
        out.append('%06X  %04X  %-34s %s' % (pos, op, name, ' '.join(vals)))
    return out


def main():
    path = sys.argv[1]
    data = open(path, 'rb').read()
    table = load_commands()
    lines = decode(data, table)
    print('; %s - %d bytes, %d instrucoes' % (path, len(data), len(lines)))
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
