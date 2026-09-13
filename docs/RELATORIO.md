# Corvos do GTA V — análise e reescrita

Relatório técnico da reescrita do mod **Corvos do GTA V** (autor original: Dakurlz).

* Entrada analisada: `extracted/Corvos_do_GTA_V/` — os cinco scripts decompilados
  `CROW1.txt` … `CROW5.txt` (627–636 linhas cada), o modelo `CROW01.dff`/`.txd`,
  a animação `raven.ifp` e os sons `CROW.mp3` / `WINGS.mp3`.
* Saída: `src/CORVOS.sc` (fonte única) → `build/CLEO/CORVOS.cs` (compilado) →
  pacote pronto em `dist/`.

---

## 1. Como o mod original funcionava

Cada script (`CROW1` … `CROW5`) é um thread CLEO praticamente igual; o que muda são
as coordenadas. Cada um:

1. espera o jogador entrar numa das **13 áreas** (`00FE` esfera de 100 m, com
   esfera de exclusão nas áreas 1–4) e escolhe um **poleiro** que esteja na tela
   (`00C2`, raios 5/10/20/100 m) — 5 pontos por área, um por script;
2. carrega o ator especial (`023C load_special_actor 'CROW01' as 1`, modelo 290) e
   a animação (`04ED "RAVEN"`);
3. carrega os dois audiostreams 3D (`CROW.mp3` = grasnado, `WINGS.mp3` = asas);
4. cria o corvo (`009A`, pedtype CIVMALE, vida 50, ângulo do ponto), toca o
   grasnado em loop e liga o som ao ator;
5. fica pousado com `IDLE_raven` e vigia ameaças (jogador a 10 m, tiro levado,
   água, pedestre a 10 m, fogo a 5 m, tiro passando, carro a 10 m a mais de 5.0);
6. quando assustado, troca para `FLY_raven` + `WINGS.mp3`, sobe a 10.0/s até uma
   altitude sorteada (15–35), nivela (`FLY_idle`/`:ANGL`), desvia de obstáculos,
   plana (−3.0) e pousa (`:KOKO` → `:I`), virando 180°;
7. ao morrer, cai com `DIE_fly` e, ao encostar no chão, toca `DIE_raven and_dies`;
8. em qualquer saída (`:END`) para/libera os dois streams, descarrega o ator
   especial (`0296`) e a animação (`04EF`), espera 10 s e recomeça.

Somando os cinco scripts: até 5 corvos no mundo, com a mesma lógica replicada
cinco vezes (~3100 linhas de script) e cinco buscas de poleiro por quadro.

---

## 2. Defeitos encontrados

Referências de linha = `CLEO/CROW1.txt` (os demais arquivos têm os mesmos defeitos,
mudando só as coordenadas).

| # | Defeito | Onde | Efeito | Correção |
|---|---------|------|--------|----------|
| 1 | Bloco "voar na direção do jogador" usa `000F` (subtração **inteira**) onde a intenção era `0063` (float) e lê as variáveis **24@, 25@, 26@ que nunca foram escritas** | L413–415, L478–480, L540–542 (o próprio autor deixou o comentário *"the incorrect math opcode was used here"*) | O resultado (`27@`, `28@`) não é usado em lugar nenhum: o bloco é código morto que só gasta CPU | Removido; o desvio agora é feito só com o teste de linha de visão + curva aleatória |
| 2 | Teste de obstáculo compara com **registradores errados**: monta o segundo ponto em `11@ 21@ 31@` mas chama `06BD ... and 11@ 12@ 13@` | L492–493 | O teste usava coordenadas velhas: o corvo podia atravessar parede ou desviar sem motivo | Um único teste frontal com as variáveis corretas (`SIGHT_DIST` = 12 m) |
| 3 | Ponto de nascimento da **área 8** copiado da **área 7** (`:START_8` = `:START_7`, em `-1641.9,-2236.8`) enquanto o gatilho da área 8 fica em `-1840.3,-1672.4` | L134–138 x L152–156 | O corvo nascia a ~500 m do gatilho, junto do poleiro da área 7 (ponto duplicado) | Ponto duplicado descartado; a área 8 continua com 4 poleiros (os dos outros scripts) → 64 pontos únicos |
| 4 | `3@ += 1` num registrador que guarda a **coordenada Z** do corvo | L486 | Lixo silencioso (sem efeito prático, mas sintoma da reutilização de registradores) | Cada valor tem sua variável própria |
| 5 | `:UGOL2` inalcançável (ninguém salta para lá) | L449–459 | Código morto | Removido |
| 6 | `:I` e `:IDLE` fazem a mesma coisa: `0209 random 0..10` desvia 1 em 10 vezes para um rótulo que toca **a mesma animação** | L275–291 | Código morto + custo por quadro | Removido |
| 7 | Reutilização dos mesmos registradores para posição, distância e ângulo (`1@ 2@ 3@` = posição **e** distância **e** ângulo; `13@` = velocidade) | todo o `:LOPA`/`:ANGL`/`:LOPA1` | É a origem dos defeitos 1–4; qualquer alteração quebra outra parte | Variáveis nomeadas, uma função por variável |
| 8 | 40 × `wait 0` + 4 × `wait 100` no mesmo thread, com laços de voo separados (`:LOPA`, `:OPA`, `:ANGL`, `:FALL`, `:UGOL1`, `:OLAL1`, `:NEEZ`, `:NEEZ1`) que repetem o mesmo `wait 0` | script inteiro | 5 scripts girando a 60 fps fazem trabalho repetido | Um `wait 0` por quadro, máquina de estados (pousado / voando / morrendo) |
| 9 | Descarrega o ator especial e a animação a cada saída do corvo (`0296`, `04EF`) | L622–623 | Recarrega modelo + IFP toda vez que um corvo morre/sai (engasgo e trabalho de I/O desnecessário) | Modelo e animação ficam na memória; só recarrega se o jogo descarregar |
| 10 | Nada impede o corvo de nascer **em cima do jogador** | `:KOKA` | Corvo aparecendo do nada a menos de 1 m | `cv_spawn` só cria se o jogador estiver a mais de 8 m |
| 11 | Efeito colateral do desenho: cada script faz sua própria varredura de poleiros por quadro e o cooldown de 10 s é **por script** | `:END` | Cinco varreduras simultâneas; corvos podendo aparecer em rajada | Uma varredura por quadro, fila de espera de 500 ms entre corvos e 10 s de pausa única |

Nada disso era um "erro de digitação": era um script longo, com variáveis recicladas
e trechos abandonados no meio do caminho.

---

## 3. O que a versão 2 muda

* **Um script só** (`CORVOS.cs`) no lugar de cinco, atendendo até **5 corvos**
  simultâneos (`MAX_CROWS`), com vagas (`crow[]`, `state[]`, `snd[]`) em vez de
  código replicado.
* **Máquina de estados por corvo**: `STATE_FREE` / `STATE_PERCH` / `STATE_FLY` /
  `STATE_DIE`. Cada quadro: um passo por corvo, uma tentativa de nascimento, um
  `wait 0`.
* **Cronômetro pelo próprio motor**: `TIMERA`/`TIMERB` (locais 32@/33@) são
  somados pelo jogo a cada quadro, então não há `wait 10000` travando o thread.
* **Poleiros preservados**: os mesmos **13 gatilhos** e **64 pontos** autorais
  (extraídos automaticamente dos scripts originais por `tools/gen_corvos.py`;
  a tabela está em `analysis/spots.md` e `analysis/spots.csv`), com a mesma regra
  de "o ponto tem que estar na tela".
* **Voo refeito**: sobe a 10.0/s até ~25 m (com variação suave), nivela, olha 12 m
  à frente com `06BD` e faz curva leve (−1.5°…+1.5°) ou firme (4°…7°) quando há
  obstáculo; perto do chão plana (−3.0) e pousa; sobre água nunca pousa.
* **Áudio 3D correto**: um stream ativo por corvo (`WINGS.mp3` voando,
  `CROW.mp3` pousado), preso ao corpo com `0AC4`; sempre parado (`0AAD`) e
  liberado (`0AAE`) antes de trocar ou soltar o corvo — sem stream vazando.
* **Colisão**: desligada só durante o voo (`SET_CHAR_COLLISION FALSE`) e religada
  ao pousar/morrer.
* **Recarga de segurança**: antes de cada nascimento, confere `023D`/`04EE` e
  recarrega modelo/animação se o jogo tiver soltado.
* **Mensagem de erro**: se `CROW01.dff`/`raven.ifp` não carregarem em 15 s, o
  script avisa na tela e se encerra em vez de ficar tentando para sempre.

Tudo que costuma ser ajustado está no bloco `CONFIGURACAO` do `src/CORVOS.sc`
(vida, número de corvos, tempos, velocidades, altitudes e distâncias de alerta).

---

## 4. Correção do crash da versão 2.0 (v2.1)

**O que aconteceu em jogo** (relato do jogador, com log do SCRLog): travamento em
`IS_CHAR_MODEL -1`, dentro do trecho que procura pedestres perto do poleiro.

**Causa**: `0AE1` (procurar alguém perto do ponto) **não grava 0 quando não acha
ninguém — grava -1**. O código testava `IF found = 0` para detectar a falha; com
-1 o teste passava como "achou alguém", o -1 ia direto para `IS_CHAR_MODEL -1 290`
e o jogo caía (esse opcode não confere se o handle é válido antes de usar).
O mod **original** usava a própria condição do opcode (`if 0AE1: ... jf @EV`) e
por isso não tinha o problema — foi a reescrita que trocou isso por uma
comparação e criou o defeito.

**O que mudou na 2.1**:

* as buscas de pedestre e de carro (`0AE1`/`0AE2`) voltaram a testar a
  **condição do opcode**, com uma trava extra de valor (`found > 0`) — nada é
  usado quando a busca falha, valendo 0, -1 ou qualquer outro retorno;
* `CREATE_CHAR` também devolve -1 quando falha (pool de ped cheio, modelo ainda
  chegando) e passou a ser conferido com `DOES_CHAR_EXIST` antes de qualquer uso;
* `cv_tick` confere `DOES_CHAR_EXIST` em cada corvo a cada quadro: se o jogo
  soltar o corpo, a vaga é liberada (`cv_forget`) em vez de mexer em handle morto;
* `cv_die`, `cv_release`, `cv_takeoff` e o áudio nunca são chamados com handle
  inválido (todos passam pela conferência do `cv_tick`).

**Como isso foi conferido**: o log do SCRLog provou que os endereços do script no
jogo são os mesmos offsets do arquivo compilado (269 = 0x10D, 647 = 0x287,
938 = 0x3AA, 1045 = 0x415), o que permitiu **calibrar o desmontador**
(`tools/scm_disasm.py`) contra valores reais do motor: no bytecode do gta3sc o
operando do salto é o **negativo do endereço absoluto do alvo** (no log:
`GOTO -938` → alvo 938, `GOTO_IF_FALSE -344` → alvo 344, `GOSUB -647` → alvo 647).
Com a regra certa, os 185 saltos do script resolvem 100% em limites de instrução
(antes o desmontador imprimia números sem sentido). A correção foi então
conferida no bytecode novo: `0AE1` seguido de `GOTO_IF_FALSE` (falhou → pula
direto para a checagem de fogo), `IS_CHAR_MODEL` só alcançável dentro do bloco da
busca, e as duas guardas `056D DOES_CHAR_EXIST` (uma por quadro no `cv_tick`, uma
depois do `009A CREATE_CHAR`).


## 5. Verificação feita

Não é possível rodar o GTA neste ambiente, então a verificação foi estática — mas
foi até o byte:

1. **Compilação**: `gta3sc` (compilador de GTA3script, C++17, compilado aqui a
   partir do fonte) sem erros nem avisos. O `.cs` gerado tem 8.768 bytes / 947
   instruções (contra ~15 KB de cada script original).
2. **Formato do arquivo**: o CLEO 4 lê o `.cs` inteiro como corpo do script
   (`CCustomScript::CCustomScript`, `CScriptEngine.cpp`) — e é exatamente assim
   que o arquivo sai do compilador: sem cabeçalho, começando direto no primeiro
   opcode.
3. **Desmontagem própria**: `tools/scm_disasm.py` decodifica o `.cs` gerado
   opcode por opcode. Conferências que passaram:
   * `023C 1 'CROW01'` — a ordem real dos parâmetros do jogo é (slot, nome),
     confirmada pela assinatura `opcode_023c(args, ScriptInt, ScriptString)` do
     openrw e pelo texto do SASCM.INI (`load_special_actor %2d% as %1d%`);
   * strings longas (`04ED`, `0AC1`, `0ACD`) saem como *Pascal string*
     (tamanho + texto) e nomes de animação/recurso como *short string* de 8
     bytes — os dois formatos que o motor lê (`CRunningScript::ReadTextLabelFromScript`);
   * acesso a vetor dinâmico (`crow[slot]`, `snd[slot]`) sai como
     `SCRIPT_PARAM_LOCAL_NUMBER_ARRAY` com a estrutura `scm::ArrayAccess`
     (base, variável de índice, tamanho, tipo, flag global), exatamente o que
     `StoreArg.hpp`/`GetAtIPFromArray` esperam (6 bytes de payload);
   * `TIMERA`/`TIMERB` caem em 32@/33@, os mesmos índices que o motor incrementa
     (`TheScripts.cpp`, `SCRIPT_VAR_TIMERA/SCRIPT_VAR_TIMERB`);
   * nenhum opcode usado depende do "NOT flag" errado: `8104`/`80FE` saem como
     `NOT LOCATE_...` (bit 0x8000 do opcode), que é como o SB sempre escreveu.
4. **Nomes das animações**: `raven.ifp` tem `IDLE_raven`, `FLY_raven`, `FLY_idle`,
   `DIE_raven`, `DIE_fly` (lidos do binário). O mod original pedia `IDLE_RAVEN`,
   `FLY_RAVEN`, `FLY_IDLE`, `DIE_FLY` — funciona porque o SA compara o nome das
   animações sem diferenciar maiúsculas (`CAnimManager::GetAnimation` →
   `CKeyGen::GetUppercaseKey`). A versão 2 usa os nomes exatos do arquivo.
5. **Opcodes**: todos os usados existem no jogo/CLEO com a mesma ordem e tipos de
   parâmetros da biblioteca do Sanny Builder (`sblib/sa/sa.json`) e do SASCM.INI
   moderno; os opcodes de áudio (0AC0/0AC1/0AC4) foram conferidos no fonte do
   CLEO 4 (`CCustomOpcodeSystem.cpp`, L1885+), que é quem os implementa.

6. **Confronto com o jogo de verdade**: o log do SCRLog (da primeira versão
   jogada) foi usado como gabarito — os offsets, os saltos e os opcodes relatados
   pelo motor batem com a desmontagem do arquivo compilado, o que valida tanto o
   compilador quanto o desmontador. Foi esse confronto que apontou o defeito da
   seção 4.

O que **não** foi testado: comportamento dentro do jogo (spawn, voo, som). O
script compila e o bytecode foi conferido, mas o teste final é jogar.

---

## 6. Como recompilar

```sh
sh tools/build.sh      # gera src/CORVOS.sc e compila para build/CLEO/CORVOS.cs
sh tools/package.sh    # monta a pasta dist/ pronta para instalar
```

`tools/build.sh` baixa e compila o `gta3sc` na primeira execução (precisa de
`g++` com C++17 e acesso ao GitHub). Depois disso é só rodar `tools/build.sh`.

Arquivos do projeto:

```
src/CORVOS.sc              fonte único (logica + tabela de poleiros gerada)
tools/corvos_logic.sc.txt  a lógica escrita à mão (o gerador injeta a tabela)
tools/gen_corvos.py        extrai áreas/poleiros dos CROW1..5 e monta o .sc
tools/build.sh             compila (baixa o gta3sc se preciso)
tools/package.sh           monta o dist/
tools/scm_disasm.py        desmontador usado na conferência do .cs
analysis/spots.csv|.md     tabela de áreas e poleiros (conferência)
dist/                      pacote final (CLEO + gta3img + LEIAME)
```

---

## 7. Créditos

* **Dakurlz** — mod original e scripts `CROW1` … `CROW5`.
* **JuniorDjjr**, **MixMods**, **BrModStudio** — divulgação/ferramentas citadas no
  LEIAME original.
* **thelink2012** — compilador `gta3sc`.
* Modelo, textura, animação e sons: do pacote original.
