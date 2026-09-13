# Corvos do GTA V — versão 2.2 (reescrita)

Reescrita do mod **Corvos do GTA V** (original de *Dakurlz*): corvos do GTA V
pousados pelo mapa de San Andreas que levantam voo quando o jogador (ou uma
ameaça) chega perto.

Esta versão junta os cinco scripts originais (`CROW1` … `CROW5`) em **um único
script**, corrige os erros de lógica do original e usa o áudio 3D do CLEO.

* **2.2** — corrige o voo (o corvo subia e **ficava parado no ar**: com a colisão
  desligada o motor do jogo não move o corpo), dois corvos no mesmo poleiro, o
  corvo que "virava NPC" ao se afastar, o grasnado em laço, a demora de alguns
  segundos para nascer depois de um *fast travel* e o nascimento na chuva /
  tempestade de areia. Som, clima e tempos agora são configurados em
  **`CLEO/CORVOS.ini`**. Ver a seção 6 do relatório.
* **2.1** — corrige o travamento relatado em jogo na procura de pedestres no
  poleiro (o `0AE1` devolve **-1** quando não acha ninguém; o teste antigo não
  pegava esse valor). Ver a seção 4 do relatório.

* **Pacote pronto para instalar:** [`dist/`](dist/) — `CLEO/CORVOS.cs`,
  `CLEO/CORVOS.ini`, `CLEO/sounds/*.mp3`, `gta3img/*` e o `LEIAME.txt`.
* **Fonte:** [`src/CORVOS.sc`](src/CORVOS.sc) (sintaxe GTA3script, compilável com
  o `gta3sc`); a tabela de áreas/poleiros é gerada por `tools/gen_corvos.py`.
* **Análise técnica e lista de defeitos corrigidos:**
  [`docs/RELATORIO.md`](docs/RELATORIO.md).
* **Instalação:** [`docs/LEIAME.txt`](docs/LEIAME.txt).

## Compilar

```sh
sh tools/build.sh      # baixa/compila o gta3sc (1a vez) e gera build/CLEO/CORVOS.cs
sh tools/package.sh    # monta a pasta dist/ pronta para instalar
```

Requisitos para compilar: `g++` com C++17, `git`, `python3` e acesso ao
github.com (para baixar o [gta3sc](https://github.com/thelink2012/gta3sc)).

## Estrutura

```
src/CORVOS.sc              fonte único do script (lógica + tabela de poleiros)
tools/corvos_logic.sc.txt  a lógica escrita à mão (o gerador injeta a tabela)
tools/gen_corvos.py        extrai áreas/poleiros dos CROW1..5 e monta o .sc
tools/CORVOS.ini           configuração que vai para o pacote (CLEO/CORVOS.ini)
tools/build.sh             compila o script (e o gta3sc, se preciso)
tools/package.sh           monta o pacote final em dist/
tools/scm_disasm.py        desmontador de SCM usado para conferir o .cs
analysis/spots.csv|.md     tabela de áreas e poleiros (64 pontos, 13 áreas)
extracted/                 conteúdo do pacote original (tar) usado como referência
docs/LEIAME.txt            instruções de instalação
docs/RELATORIO.md          análise do mod original e do que foi corrigido
```

## Requisitos no jogo

GTA San Andreas 1.0 (PC) + CLEO 4.3 ou superior (o áudio 3D usa opcodes do CLEO).
O modelo `CROW01.dff`/`.txd` e a animação `raven.ifp` precisam estar no `gta3.img`
(ou no ModLoader).
