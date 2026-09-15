# Corvos do GTA V — versão 2.6 (reescrita)

Reescrita do mod **Corvos do GTA V** (original de *Dakurlz*): corvos do GTA V
pousados pelo mapa de San Andreas que levantam voo quando o jogador (ou uma
ameaça) chega perto.

Esta versão junta os cinco scripts originais (`CROW1` … `CROW5`) em **um único
script**, corrige os erros de lógica do original e usa o áudio 3D do CLEO.

* **2.6 (correção)** — o corvo deixou de usar o **slot 1** de ator especial
  (`#SPECIAL01`, modelo 290). Esse é o slot que as missões do jogo usam para
  carregar personagens como o Sweet; como quem manda no slot é o último script
  que carrega, os corvos criados depois apareciam com o corpo do outro
  personagem (o clássico *"Sweet voando batendo asas"*). Agora o corvo usa o
  **slot 7** (`#SPECIAL07`, modelo 296), o menos usado, como recomenda o
  [tutorial do Junior_Djjr](https://forum.mixmods.com.br/f141-gta3script-cleo/t551-criacao-de-carros-pedestres-objetos-uso-de-modelos).
  Ver a seção 10 do relatório.
* **2.5 (correção)** — o corvo **pousado** estava recebendo o código de voo (a
  conta do grasnado deixava a variável de rascunho com o valor de "voando"), e
  saía do poleiro deslizando, sem bater asas, até cair no chão. Agora a rotina
  de cada corvo é escolhida lendo o estado da vaga direto, sem chance de
  confusão.
* **2.5** — **modo debug** ligado pelo `debug = 1` do `CLEO/CORVOS.ini`
  (mostra na tela em que região de corvos você está, a região mais próxima e a
  distância, a trava, o clima, se o modelo está carregado, quantos corvos
  existem, o estado de cada um e os cronômetros); cada corvo passou a ter o
  **próprio relógio de grasnado** (antes todos começavam do zero no nascimento e
  grasnavam quase juntos); e o poleiro do `CROW2` da área 1 foi movido. Ver a
  seção 9 do relatório.
* **2.4** — os corvos não reaparecem mais logo depois de irem embora: depois que
  os corvos de um lugar vão embora, **nenhum outro nasce até o jogador se afastar
  200 m dali** (a área de ativação passou de 100 m para 200 m; no mod original o
  script esperava 10 s e recomeçava — e o corvo aparecia nas costas do jogador).
  Ver a seção 8 do relatório.
* **2.3** — voo corrigido de vez (com a colisão desligada o motor do jogo não
  move o ped: o corvo ficava parado no ar batendo as asas); o corvo escolhe o
  poleiro mais perto que esteja livre, **de preferência fora da câmera** e nunca
  a menos de 25 m do jogador (antes ele só nascia se o ponto estivesse na tela,
  ou seja, aparecia na frente do jogador e sempre no mesmo lugar); dois corvos
  pousados ficam a pelo menos 4 m um do outro; o som só toca a menos de 30 m e o
  grasnado ficou mais raro. Ver a seção 7 do relatório.
* **2.2** — corrige o travamento do script esperando o modelo carregar (a demora
  depois do *fast travel*), o corvo que "virava NPC" ao se afastar, o grasnado em
  laço, a demora para nascer e o nascimento na chuva / tempestade de areia. Som,
  clima e tempos são configurados em **`CLEO/CORVOS.ini`**. Ver a seção 6.
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

## Compatibilidade com missões e outros mods

O corvo é um **ator especial** (`023C load_special_actor`), e os dez slots desses
atores (modelos 290–299) são estado global do jogo, compartilhado com as missões:
**o último script que carrega é quem manda no slot**. O mod original (e as versões
2.0–2.5 desta reescrita) usava o **slot 1** — o mesmo que as missões usam para
carregar o Sweet e outros personagens —, então depois de uma dessas missões o
corvo nascia com o corpo do outro personagem e continuava batendo asas. A versão
2.6 passou a usar o **slot 7** (modelo 296), que é o menos usado; se outro mod
ainda assim disputar esse slot, dá para trocar por outro número: basta mudar
`CROW_SLOT` e `CROW_MODEL` (`CROW_MODEL = 289 + CROW_SLOT`) no bloco
`CONFIGURACAO` do `src/CORVOS.sc` e recompilar (`sh tools/build.sh`).

## Requisitos no jogo

GTA San Andreas 1.0 (PC) + CLEO 4.3 ou superior (o áudio 3D usa opcodes do CLEO).
O modelo `CROW01.dff`/`.txd` e a animação `raven.ifp` precisam estar no `gta3.img`
(ou no ModLoader).
