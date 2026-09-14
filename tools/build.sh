#!/bin/sh
# ---------------------------------------------------------------------------
#  Compila o mod: src/CORVOS.sc  ->  build/CLEO/CORVOS.cs
#
#  O compilador usado e o gta3sc (https://github.com/thelink2012/gta3sc), que
#  compila GTA3script e gera o .cs no formato que o CLEO 4 le (codigo SCM puro,
#  sem cabecalho - o CLEO le o arquivo inteiro como corpo do script).
#
#  Uso:
#     sh tools/build.sh              # compila
#     GTA3SC=/caminho/gta3sc sh tools/build.sh   # usa um binario ja compilado
#
#  Se o binario nao existir, o script baixa e compila o gta3sc em
#  $GTA3SC_CACHE (padrao: ~/.cache/gta3sc). Precisa de g++ com C++17 e acesso
#  ao github.com. O gta3sc le a pasta config/ que fica ao lado do binario.
# ---------------------------------------------------------------------------
set -e

REPO=$(cd "$(dirname "$0")/.." && pwd)
GTA3SC_DIR="${GTA3SC_CACHE:-$HOME/.cache/gta3sc}"   # fora do repo: e so cache
GTA3SC_BIN="${GTA3SC:-$GTA3SC_DIR/build/gta3sc}"

# 1) o compilador existe?
if [ ! -x "$GTA3SC_BIN" ]; then
    echo "== compilador gta3sc nao encontrado, baixando o codigo fonte"
    mkdir -p "$GTA3SC_DIR"
    if [ ! -d "$GTA3SC_DIR/src" ]; then
        git clone --depth 1 https://github.com/thelink2012/gta3sc "$GTA3SC_DIR"
    fi

    echo "== compilando o gta3sc com g++"
    mkdir -p "$GTA3SC_DIR/build"
    cd "$GTA3SC_DIR/build"
    cat > git-sha1.cpp <<'EOF'
#include <stdinc.h>
const char* GTA3SC_GIT_SHA1 = "local";
const char* GTA3SC_GIT_BRANCH = "master";
const char* GTA3SC_GIT_DESCRIBE_TAG = "";
EOF
    g++ -std=c++17 -O2 -DGTA3SC_USING_GIT_DESCRIBE -Wno-placement-new \
        -Isrc -I../src -Ideps -I../deps -I../deps/rapidxml -I../deps/cppformat \
        -I../deps/cppformat/cppformat -I../deps/optional/include -I../deps/expected/include \
        -I../deps/any/include -I../deps/variant/include -I../deps/SmallVector -I../deps/string_view \
        ../src/*.cpp git-sha1.cpp ../deps/cppformat/cppformat/format.cc -o gta3sc
    cp -r ../config .
    GTA3SC_BIN="$GTA3SC_DIR/build/gta3sc"
fi

# 2) gera o src/CORVOS.sc (tabela de areas/poleiros + logica) e compila
cd "$REPO"
python3 tools/gen_corvos.py

echo "== compilando src/CORVOS.sc"
mkdir -p build/CLEO
"$GTA3SC_BIN" compile src/CORVOS.sc --config=gtasa --cs --guesser \
    -fno-entity-tracking -fbreak-continue -o build/CLEO/CORVOS.cs

echo "== pronto: build/CLEO/CORVOS.cs ($(wc -c < build/CLEO/CORVOS.cs) bytes)"
