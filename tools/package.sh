#!/bin/sh
# ---------------------------------------------------------------------------
#  Monta a pasta pronta para instalar (dist/), juntando:
#     dist/CLEO/CORVOS.cs                 script novo compilado
#     dist/CLEO/CORVOS.ini                configuracao (editavel pelo jogador)
#     dist/CLEO/sounds/CROW.mp3           sons originais do mod
#     dist/CLEO/sounds/WINGS.mp3
#     dist/gta3img/CROW01.dff             modelo/textura/animacao originais
#     dist/gta3img/CROW01.txd
#     dist/gta3img/raven.ifp
#     dist/LEIAME.txt                     instrucoes
#
#  Rode tools/build.sh antes (para gerar build/CLEO/CORVOS.cs).
# ---------------------------------------------------------------------------
set -e

REPO=$(cd "$(dirname "$0")/.." && pwd)
ORIG="$REPO/extracted/Corvos_do_GTA_V"
DIST="$REPO/dist"

if [ ! -f "$REPO/build/CLEO/CORVOS.cs" ]; then
    echo "erro: build/CLEO/CORVOS.cs nao existe - rode tools/build.sh primeiro" >&2
    exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST/CLEO/sounds" "$DIST/gta3img"

cp "$REPO/build/CLEO/CORVOS.cs" "$DIST/CLEO/CORVOS.cs"
cp "$REPO/build/CLEO/CORVOS.ini" "$DIST/CLEO/CORVOS.ini"
cp "$ORIG/CLEO/sounds/CROW.mp3" "$DIST/CLEO/sounds/CROW.mp3"
cp "$ORIG/CLEO/sounds/WINGS.mp3" "$DIST/CLEO/sounds/WINGS.mp3"
cp "$ORIG/gtaimg/CROW01.dff" "$DIST/gta3img/CROW01.dff"
cp "$ORIG/gtaimg/CROW01.txd" "$DIST/gta3img/CROW01.txd"
cp "$ORIG/gtaimg/raven.ifp" "$DIST/gta3img/raven.ifp"
cp "$REPO/docs/LEIAME.txt" "$DIST/LEIAME.txt"

echo "== dist/ pronta:"
find "$DIST" -type f | sed "s|$REPO/||" | sort
