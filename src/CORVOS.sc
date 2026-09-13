// ===========================================================================
//  CORVOS DO GTA V  -  versao 2.5 (reescrito, corrigido e otimizado)
//
//  Mod original: Dakurlz
//  Creditos originais: JuniorDjjr (blog), MixMods, BrModStudio
//  Esta versao: um unico script no lugar dos cinco antigos (CROW1..CROW5)
//
//  INSTALACAO (veja o LEIAME.txt do pacote):
//    1. copie a pasta CLEO (com CORVOS.cs e a pasta sounds) para a pasta do GTA
//    2. coloque CROW01.dff, CROW01.txd e raven.ifp no gta3.img
//       (ou na pasta gta3img do ModLoader)
//    3. apague os scripts antigos CROW1.cs ... CROW5.cs
//
//  REQUISITOS
//    - GTA San Andreas 1.0 (PC)
//    - CLEO 4.3 ou superior. Os opcodes de audio 3D (0AC1/0AC4) e as esperas
//      por stream de audio sao do CLEO, nao do jogo.
//
//  CONFIGURACAO: tudo o que se costuma ajustar esta no arquivo
//                CLEO/CORVOS.ini (numero de corvos, som, clima, tempos).
//                O script cria o arquivo com os valores padrao se ele nao existir.
//
//  COMO COMPILAR:  sh tools/build.sh        (usa o compilador gta3sc)
//                  saida: build/CORVOS.cs
//
//  HISTORICO
//   2.5 - modo debug (mostra na tela em que area o jogador esta, a trava, os
//         corvos e o clima; ligado pelo "debug" do CLEO/CORVOS.ini),
//         grasnado de cada corvo com relogio proprio (antes todos comecavam
//         juntos e grasnavam quase ao mesmo tempo) e o poleiro do CROW2 da
//         area 1 movido para onde o jogador pediu
//         correcao: o corvo pousado estava recebendo o codigo de voo, porque o
//         relogio do grasnado deixava a variavel de estado com o valor de
//         "voando" e o cv_tick usava esse valor para escolher a sub. Agora o
//         cv_tick_state le o estado de novo, direto do state[slot]
//   2.4 - os corvos nao reaparecem mais logo depois de irem embora: quando um
//         corvo sai de uma regiao, ela fica "gasta" e nenhum outro corvo nasce
//         ate o jogador se afastar 200 m dela (o original esperava 10 s e
//         comecava tudo de novo, e o corvo nascia nas costas do jogador). A
//         area de ativacao passou de 100 m para 200 m
//   2.3 - voo consertado (com a colisao desligada o GTA nao move o ped: o
//         corvo ficava parado no ar batendo as asas), escolha dos poleiros
//         (o mais perto do jogador, de preferencia escondido e sempre a mais
//         de 25 m dele -- no lugar do antigo "precisa estar na tela", que
//         fazia o corvo nascer bem na frente do jogador e sempre no mesmo
//         ponto), som so perto do jogador (30 m), grasnado mais raro, tempos
//         de espera menores e tudo isso ajustavel no CLEO/CORVOS.ini
//   2.2 - voo corrigido (colisao volta a ficar ligada: desligada o ped nao se
//         move), poleiros nao repetem (corvos nao nascem um dentro do outro),
//         corvo longe do jogador desaparece em vez de virar npc, um corvo por
//         quadro, grasnado sem loop + volume, clima (chuva/tempestade) e
//         configuracao no CLEO/CORVOS.ini
//   2.1 - corrigido o crash da busca de pedestre (0AE1 devolve -1)
//   2.0 - um script no lugar dos cinco originais
// ===========================================================================


// ---------------------------------------------------------------------------
//  CONFIGURACAO
// ---------------------------------------------------------------------------
CONST_INT   MAX_CROWS             5        // corvos ao mesmo tempo
CONST_INT   CROW_MODEL            290      // #SPECIAL01 (023C carrega no slot 1)
CONST_INT   CROW_SLOT             1        // slot do ator especial (1..10)
CONST_INT   CROW_HEALTH           50       // vida do corvo (a 0223 usa inteiro)

CONST_INT   STATE_FREE            0
CONST_INT   STATE_PERCH           1
CONST_INT   STATE_CLIMB           2   // subindo (so vira cruzeiro depois)
CONST_INT   STATE_FLY             3   // cruzeiro (altura ja conquistada)
CONST_INT   STATE_DIE             4

CONST_INT   AUDIO_STOP            0
CONST_INT   AUDIO_PLAY            1

CONST_INT   SPAWN_DELAY           250      // ms entre a chegada de um corvo e outro
CONST_INT   RESPAWN_WAIT          3000     // ms de espera depois que um corvo sai
CONST_INT   LOAD_TIMEOUT          15000    // ms para o modelo/animacao carregarem
CONST_INT   PED_SCAN_TRIES        8        // quantos char a busca no poleiro pode percorrer
CONST_INT   EVENT_WHIZZED          49      // "tiro passou de raspão" (080E)
CONST_INT   CAW_DEFAULT_SEC        12      // "grasnado_a_cada" padrao (segundos)

CONST_FLOAT DESPAWN_DIST          100.0    // distancia em que o corvo "esquece" o jogador
CONST_FLOAT SPAWN_MIN_DIST        25.0     // nao nasce corvo mais perto que isso
CONST_FLOAT SPAWN_MAX_DIST        90.0     // nem mais longe (senao ja seria descartado por DESPAWN_DIST)
CONST_FLOAT SND_RANGE             30.0     // o som so toca a menos disso do jogador
CONST_FLOAT FLY_SPEED             10.0     // velocidade de cruzeiro
CONST_FLOAT CLIMB_SPEED           10.0     // velocidade de subida
CONST_FLOAT SINK_SPEED            -3.0     // descida (planeio) perto do chao
CONST_FLOAT CRUISE_ALT_MIN        22.5     // altitude que o corvo procura (minimo)
CONST_FLOAT CRUISE_ALT_MAX        27.5     // ... e maximo (o alvo e sorteado entre os dois)
CONST_FLOAT LOW_HEIGHT            8.0      // abaixo disso comeca a descer para pousar
CONST_FLOAT LAND_HEIGHT           2.0      // altura de pouso
CONST_FLOAT SIGHT_DIST            12.0     // distancia da "visao" frontal
CONST_FLOAT PLAYER_ALERT_DIST     10.0     // jogador perto demais
CONST_FLOAT PED_ALERT_DIST        10.0     // pedestre perto demais
CONST_FLOAT FIRE_ALERT_DIST       5.0      // fogo perto demais
CONST_FLOAT CAR_ALERT_DIST        10.0     // carro perto demais
CONST_FLOAT CAR_ALERT_SPEED       5.0      // ... e rapido demais
CONST_FLOAT GROUND_FAR            100.0    // altura falsa sobre a agua (nunca pousa)
CONST_FLOAT PERCH_MIN_DIST         4.0     // distancia minima entre dois corvos pousados

// Clima do GTA SA 1.0, lido da memoria com 0A8D READ_MEMORY (1 byte).
// 0xC8131C = CWeather::NewWeatherType
CONST_INT   ADDR_WEATHER_TYPE     0xC8131C
CONST_INT   ADDR_RAIN             0xC81324   // CWeather::Rain (float; so o debug usa)


// ---------------------------------------------------------------------------
//  VARIAVEIS LOCAIS
//
//   crow[]  handle do corvo          (0 = vaga livre)
//   state[]  STATE_* daquele corvo
//   snd[]    stream de audio 3D ativo (asas voando / grasnado pousado)
//   lockarea area de onde os corvos foram embora; enquanto ela nao for 0,
//            nenhum corvo nasce (a varredura nem comeca). Volta a 0 quando o
//            jogador sai da esfera daquela area (200 m). E' isso que impede o
//            corvo de reaparecer logo depois de ir embora -- o mod original
//            esperava 10 s e comecava tudo de novo.
//
//  TIMERA e TIMERB sao as variaveis 32@ e 33@ do jogo: o motor soma o tempo
//  de cada quadro nelas, entao servem de cronometro sem travar o script.
// ---------------------------------------------------------------------------
SCRIPT_START
{
LVAR_INT   crow[5]        // tamanho = MAX_CROWS (o gta3sc exige numero fixo)
LVAR_INT   state[5]
LVAR_INT   snd[5]
LVAR_INT   lockarea       // area (1..13) que ja "gastou" o corvo desta visita
LVAR_INT   slot, h, player, st, tmp, found
LVAR_INT   cfg_misc        // bits 0/1 = nasce na chuva / na tempestade de areia
                           // (bits 2+) / 4 = segundos entre grasnados
LVAR_FLOAT px, py, pz, fx, fy, fz, ang, gz, rnd


// ---------------------------------------------------------------------------
//  1) CARREGA O MODELO E A ANIMACAO
// ---------------------------------------------------------------------------
cv_load:
WAIT 0
LOAD_SPECIAL_CHARACTER CROW_SLOT CROW01
REQUEST_ANIMATION "RAVEN"
timera = 0

cv_load_wait:
WAIT 0
IF HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
AND HAS_ANIMATION_LOADED "RAVEN"
    GOTO cv_ready
ENDIF
IF timera < LOAD_TIMEOUT
    GOTO cv_load_wait
ENDIF
PRINT_STRING_NOW "~r~CORVOS~w~: CROW01.dff e raven.ifp nao estao no gta3.img (veja o LEIAME)" 9000
TERMINATE_THIS_CUSTOM_SCRIPT


// ---------------------------------------------------------------------------
//  2) ZERA A TABELA E ENTRA NO LOOP
// ---------------------------------------------------------------------------
cv_ready:
GOSUB cv_ini_load
slot = 0
WHILE slot < MAX_CROWS
    crow[slot] = 0
    state[slot] = STATE_FREE
    snd[slot] = 0
    slot += 1
ENDWHILE
lockarea = 0
// cronometros "grandes" de proposito: o primeiro corvo nao precisa esperar
timera = 60000
timerb = 60000


// ---------------------------------------------------------------------------
//  3) LOOP PRINCIPAL
// ---------------------------------------------------------------------------
cv_main:
WAIT 0
IF NOT IS_PLAYER_PLAYING 0
    GOTO cv_main
ENDIF
GET_PLAYER_CHAR 0 player

// ---- roda a cabeca de cada corvo vivo --------------------------------
slot = 0
WHILE slot < MAX_CROWS
    GOSUB cv_tick
    slot += 1
ENDWHILE

// ---- cabe um corvo novo? ---------------------------------------------
GOSUB cv_free_slot
IF slot < 0
    GOTO cv_main
ENDIF
// as esperas (tempo_entre_corvos / tempo_para_renascer) sao conferidas
// dentro do cv_spawn, onde sao lidas do CLEO/CORVOS.ini
// garante que o jogo nao descarregou o ator especial / a animacao
GOSUB cv_keep_loaded
GOSUB cv_scan
GOSUB cv_debug                      // na pratica nao faz nada: sai na primeira
                                    // linha quando "debug = 0" no INI
GOTO cv_main


// ---------------------------------------------------------------------------
//  cv_free_slot   ->  slot = primeira vaga livre, ou -1
// ---------------------------------------------------------------------------
cv_free_slot:
// "numero_de_corvos" do INI: quantas vagas podem ser usadas (1..MAX_CROWS).
// Vem para "found" porque o script ja usa as 32 variaveis locais do jogo e
// nao sobra nenhuma para guardar um cfg_max separado.
slot = -1
tmp = 0
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "numero_de_corvos" found
IF found < 1
    found = MAX_CROWS
ENDIF
IF found > MAX_CROWS
    found = MAX_CROWS
ENDIF
WHILE tmp < found
    IF crow[tmp] = 0
        slot = tmp
        BREAK
    ENDIF
    tmp += 1
ENDWHILE
RETURN


// ---------------------------------------------------------------------------
//  cv_keep_loaded   o jogo pode soltar o ator especial se ninguem usar
// ---------------------------------------------------------------------------
cv_keep_loaded:
// pede o modelo/animacao de volta, mas NAO fica esperando: esperar aqui
// travava o script inteiro (corvo demorava segundos para nascer)
IF HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
AND HAS_ANIMATION_LOADED "RAVEN"
    RETURN
ENDIF
LOAD_SPECIAL_CHARACTER CROW_SLOT CROW01
REQUEST_ANIMATION "RAVEN"
RETURN


// ---------------------------------------------------------------------------
//  cv_ini_load   le a configuracao do CLEO/CORVOS.ini
//                (cria o arquivo com os valores padrao se ele nao existir)
// ---------------------------------------------------------------------------
cv_ini_load:
IF DOES_FILE_EXIST "CLEO/CORVOS.ini"
    GOTO cv_ini_read
ENDIF
WRITE_INT_TO_INI_FILE MAX_CROWS "CLEO/CORVOS.ini" "corvos" "numero_de_corvos"
WRITE_INT_TO_INI_FILE 0 "CLEO/CORVOS.ini" "corvos" "nascer_na_chuva"
WRITE_INT_TO_INI_FILE 0 "CLEO/CORVOS.ini" "corvos" "nascer_na_tempestade_de_areia"
WRITE_INT_TO_INI_FILE CAW_DEFAULT_SEC "CLEO/CORVOS.ini" "corvos" "grasnado_a_cada"
WRITE_INT_TO_INI_FILE 0 "CLEO/CORVOS.ini" "corvos" "desligar_som"
WRITE_FLOAT_TO_INI_FILE 0.6 "CLEO/CORVOS.ini" "corvos" "volume_grasnado"
WRITE_FLOAT_TO_INI_FILE 0.4 "CLEO/CORVOS.ini" "corvos" "volume_asas"
WRITE_INT_TO_INI_FILE SPAWN_DELAY "CLEO/CORVOS.ini" "corvos" "tempo_entre_corvos"
WRITE_INT_TO_INI_FILE RESPAWN_WAIT "CLEO/CORVOS.ini" "corvos" "tempo_para_renascer"
WRITE_INT_TO_INI_FILE 0 "CLEO/CORVOS.ini" "corvos" "debug"
IF DOES_FILE_EXIST "CLEO/CORVOS.ini"
    GOTO cv_ini_read
ENDIF
// a pasta do jogo esta protegida e o arquivo nao pode ser criado: segue com os
// valores padrao do proprio script
cfg_misc = CAW_DEFAULT_SEC
cfg_misc *= 4
RETURN

cv_ini_read:
// "numero_de_corvos" nao e' lido aqui: o cv_free_slot le direto do INI (a
// validacao de 1..MAX_CROWS esta la)
// cfg_misc = bit0 (nasce na chuva) + bit1 (nasce na tempestade de areia)
//            + 4 * segundos entre grasnados
cfg_misc = 0
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "nascer_na_chuva" tmp
IF NOT tmp = 0
    cfg_misc = 1
ENDIF
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "nascer_na_tempestade_de_areia" tmp
IF NOT tmp = 0
    cfg_misc += 2
ENDIF
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "grasnado_a_cada" tmp
IF tmp < 0
    tmp = 0
ENDIF
IF tmp > 120
    tmp = 120
ENDIF
tmp *= 4
cfg_misc += tmp
RETURN


// ---------------------------------------------------------------------------
//  cv_perch_busy   st = 1 quando ja tem corvo perto do poleiro (px py pz)
//                  usado para dois corvos nao nascerem no mesmo ponto
// ---------------------------------------------------------------------------
cv_perch_busy:
st = 0
tmp = 0
cv_busy_loop:
IF crow[tmp] = 0
    GOTO cv_busy_next
ENDIF
h = crow[tmp]
IF DOES_CHAR_EXIST h
    IF LOCATE_CHAR_ANY_MEANS_3D h px py pz PERCH_MIN_DIST PERCH_MIN_DIST PERCH_MIN_DIST 0
        st = 1
        GOTO cv_busy_done
    ENDIF
ENDIF
cv_busy_next:
tmp += 1
IF tmp < MAX_CROWS
    GOTO cv_busy_loop
ENDIF
cv_busy_done:
RETURN


// ---------------------------------------------------------------------------
//  cv_area_lock   marca como "gasta" a area em que esta o corvo da vaga
//
//  A area e' descoberta pela posicao do corpo (sub gerada cv_area_of_char),
//  entao nao e' preciso guardar a area de cada corvo. A trava so cai quando o
//  jogador sai da area -- ver cv_area_unlock.
// ---------------------------------------------------------------------------
cv_area_lock:
h = crow[slot]
GOSUB cv_area_of_char
IF st = 0
    RETURN
ENDIF
lockarea = st
RETURN


// ---------------------------------------------------------------------------
//  cv_tick   um passo do corvo da vaga "slot"
// ---------------------------------------------------------------------------
cv_tick:
h = crow[slot]
IF h = 0
    RETURN
ENDIF
IF NOT DOES_CHAR_EXIST h
    GOSUB cv_forget                   // o jogo soltou o corpo: so libera a vaga
    RETURN
ENDIF
GET_CHAR_HEALTH h tmp
IF tmp <= 0
    GOSUB cv_die
    RETURN
ENDIF
IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D player h DESPAWN_DIST DESPAWN_DIST DESPAWN_DIST 0
    // longe do jogador: so desaparece se nao estiver na tela -- se o jogador
    // estiver olhando (camera de noclip, por exemplo) o corvo continua vivo
    GET_CHAR_COORDINATES h px py pz
    IF NOT IS_POINT_ON_SCREEN px py pz 5.0
        GOSUB cv_area_lock        // os corvos desta area foram embora: gasta a area
        st = 1                    // 1 = apaga o corpo (nao vira npc comum)
        GOSUB cv_release
        RETURN
    ENDIF
ENDIF
// Uma sub por quadro, escolhida pelo estado da vaga. A escolha LE o estado
// direto do "state[slot]", e nao uma copia guardada numa variavel de trabalho:
// as subs usam essas variaveis como rascunho, e uma copia chegava sobrescrita
// na hora de escolher. Foi o que quebrou na 2.5: o trecho do grasnado deixava
// o "st" = 3 (o mesmo numero de "voando") e o corvo POUSADO recebia o codigo de
// voo -- saia do poleiro deslizando, sem bater as asas, e ia parar no chao.
// O "GOTO" dentro do cv_tick_state nao muda o caminho de volta: a sub devolve
// direto para o cv_tick, como o cv_perch ja fazia com o cv_takeoff.
GOSUB cv_tick_state
GOSUB cv_audio_range
RETURN


// ---------------------------------------------------------------------------
//  cv_tick_state   manda o corvo para a sub do estado em que ele esta
//
//  Entrada: slot (a vaga). Uma sub por quadro, sempre lendo o estado de novo.
// ---------------------------------------------------------------------------
cv_tick_state:
IF state[slot] = STATE_PERCH
    GOTO cv_perch
ENDIF
IF state[slot] = STATE_CLIMB
    GOTO cv_fly_climb
ENDIF
IF state[slot] = STATE_FLY
    GOTO cv_fly
ENDIF
RETURN


// ---------------------------------------------------------------------------
//  cv_perch   pousado: fica de olho e levanta voo quando algo assusta
//
//  ATENCAO: aqui as variaveis de trabalho ("st", "tmp", "found", "rnd", as
//  coordenadas) sao rascunho -- o "st" inclusive termina com o numero da area
//  (o cv_takeoff chama o cv_area_lock). Ninguem pode depender do valor delas
//  depois deste trecho: quem escolhe a proxima sub e' o cv_tick_state, que le
//  o "state[slot]".
// ---------------------------------------------------------------------------
cv_perch:
IF LOCATE_CHAR_ANY_MEANS_CHAR_2D player h PLAYER_ALERT_DIST PLAYER_ALERT_DIST 0
    GOTO cv_takeoff
ENDIF
IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR h player
    GOTO cv_takeoff
ENDIF
IF IS_CHAR_IN_WATER h
    GOTO cv_takeoff
ENDIF

// pedestres por perto (outros corvos nao contam)
//
// IMPORTANTE: 0AE1 grava -1 (nao 0) quando nao acha ninguem, e o teste tem que
// ser pela propria condicao do opcode (como no mod original) -- comparar o
// valor com 0 deixava passar -1 e o jogo batia em IS_CHAR_MODEL -1 (crash).
GET_CHAR_COORDINATES h px py pz
st = 0
tmp = 0
cv_perch_peds:
IF GET_RANDOM_CHAR_IN_SPHERE_NO_SAVE_RECURSIVE px py pz PED_ALERT_DIST tmp 1 found
    IF NOT found > 0
        GOTO cv_perch_fire          // nao achou ninguem: pula para a checagem de fogo
    ENDIF
    IF NOT IS_CHAR_MODEL found CROW_MODEL
        GOTO cv_takeoff
    ENDIF
    tmp = 1
    st += 1
    IF st < PED_SCAN_TRIES
        GOTO cv_perch_peds
    ENDIF
ENDIF

// fogo por perto
cv_perch_fire:
GET_NUMBER_OF_FIRES_IN_RANGE px py pz FIRE_ALERT_DIST tmp
IF tmp > 0
    GOTO cv_takeoff
ENDIF

// tiro passando de raspão
GET_CHAR_HIGHEST_PRIORITY_EVENT h tmp
IF tmp = EVENT_WHIZZED
    GOTO cv_takeoff
ENDIF

// carro rapido por perto (0AE2 tambem grava -1 quando nao acha nada)
IF GET_RANDOM_CAR_IN_SPHERE_NO_SAVE_RECURSIVE px py pz CAR_ALERT_DIST 0 0 found
    IF found > 0
        GET_CAR_SPEED found rnd
        IF rnd > CAR_ALERT_SPEED
            GOTO cv_takeoff
        ENDIF
    ENDIF
ENDIF

// grasnado.
//
// Cada corvo tem o PROPRIO relogio: a fase sai do numero do corpo dele (o
// "handle", que muda de corvo para corvo) somado ao relogio do motor, e o
// periodo e' o "grasnado_a_cada" do INI. Antes o teste era igual para todos e
// comecava do zero no nascimento, entao os corvos grasnavam quase juntos.
// Uma deriva lenta faz o intervalo nunca sair sempre igual, e o grasnado so
// toca com o jogador por perto (SND_RANGE).
st = cfg_misc / 4                      // segundos
IF st > 0
    IF LOCATE_CHAR_ANY_MEANS_CHAR_3D player h SND_RANGE SND_RANGE SND_RANGE 0
        // ainda esta tocando? entao espera (0AB9 devolve -1 quando terminou)
        IF NOT snd[slot] = 0
            GET_AUDIO_STREAM_STATE snd[slot] tmp
            IF tmp > 0
                RETURN
            ENDIF
            GOSUB cv_audio_off         // terminou: libera o stream
        ENDIF
        st *= 1000                     // periodo em milissegundos
        tmp = crow[slot] * 1237        // fase daquele corvo
        tmp += timerb
        found = timerb / 1000
        found *= 271                   // deriva lenta (~0,27 s por segundo)
        tmp += found
        found = tmp / st
        found *= st
        tmp = tmp - found              // quanto falta dentro do periodo
        // (o "st" sai daqui com o periodo em milissegundos: pode, o
        //  cv_tick_state nao usa mais o valor dele)
        IF tmp < 300                   // janela do grasnado (~0,3 s)
            GOSUB cv_audio_caw
        ENDIF
    ENDIF
ENDIF
RETURN


// ---------------------------------------------------------------------------
//  cv_takeoff
// ---------------------------------------------------------------------------
cv_takeoff:
TASK_PLAY_ANIM_NON_INTERRUPTABLE h "FLY_raven" "RAVEN" 4.0 TRUE TRUE TRUE TRUE -1
CLEAR_CHAR_LAST_WEAPON_DAMAGE h
CLEAR_CHAR_LAST_DAMAGE_ENTITY h
GOSUB cv_audio_wings
state[slot] = STATE_CLIMB
// A COLISAO TEM QUE FICAR LIGADA. Com 0619 FALSE o GTA nao aplica velocidade
// nenhuma no ped (o SET_CHAR_VELOCITY nao faz efeito) e o corvo fica parado no
// ar batendo as asas. O mod original desligava a colisao so no primeiro
// quadro da decolagem e religava logo em seguida.
SET_CHAR_COLLISION h TRUE
SET_CHAR_VELOCITY h 0.0 0.0 CLIMB_SPEED
GET_CHAR_HEADING h ang
SET_CHAR_ROTATION h 10.0 0.0 ang
GOSUB cv_area_lock                  // levantou voo: a area fica gasta ate o jogador sair
RETURN


// ---------------------------------------------------------------------------
//  cv_fly   voando: procura altitude, desvia de parede, pousa
// ---------------------------------------------------------------------------
cv_fly:
// A colisao TEM que ficar ligada voando. Com ela desligada (0619 FALSE) o
// ApplyMoveSpeed do motor nao roda e o corvo fica parado no ar, so batendo
// as asas -- o mod original desligava a colisao so no primeiro quadro do voo.
SET_CHAR_COLLISION h TRUE
GET_CHAR_HEADING h ang
GET_CHAR_HEIGHT_ABOVE_GROUND h gz
IF IS_CHAR_IN_WATER h
    gz = GROUND_FAR
ENDIF
IF gz <= LAND_HEIGHT
    GOTO cv_land                      // encostou no chao: pousa
ENDIF
fz = 0.0                              // cruzeiro: voo reto
IF gz <= LOW_HEIGHT
    fz = SINK_SPEED                   // chegou perto do chao: plana para pousar
ENDIF
GOTO cv_fly_go


// ---------------------------------------------------------------------------
//  cv_fly_climb   subindo: e o "LOPA" do mod original
//
//  Enquanto nao chega na altura de cruzeiro o corvo sobe direto (Z = +10).
//  Antes a subida e o cruzeiro eram o mesmo trecho e o corvo que decolava de um
//  poleiro baixo (menos de 8 m do chao) caia na regra do "plana para pousar":
//  ele descia, pousava no chao e ficava pulando de lugar -- o corvo parecia
//  andar no chao em vez de ir embora.
// ---------------------------------------------------------------------------
cv_fly_climb:
SET_CHAR_COLLISION h TRUE
GET_CHAR_HEADING h ang
GET_CHAR_HEIGHT_ABOVE_GROUND h gz
IF IS_CHAR_IN_WATER h
    gz = GROUND_FAR
ENDIF
// altitude alvo (varia de leve para o voo nao ficar mecanico)
GENERATE_RANDOM_FLOAT_IN_RANGE CRUISE_ALT_MIN CRUISE_ALT_MAX rnd
IF gz >= rnd
    state[slot] = STATE_FLY           // subiu o bastante: vira cruzeiro
ENDIF
fz = CLIMB_SPEED

// daqui para baixo e o voo em si (igual para subida e cruzeiro)
cv_fly_go:
// o que tem na frente?
GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS h 0.0 0.0 0.0 px py pz
GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS h 0.0 SIGHT_DIST 0.0 fx fy rnd
IF NOT IS_LINE_OF_SIGHT_CLEAR px py pz fx fy rnd 1 1 0 1 0
    GENERATE_RANDOM_FLOAT_IN_RANGE 4.0 7.0 rnd   // obstaculo: curva firme
ELSE
    GENERATE_RANDOM_FLOAT_IN_RANGE -1.5 1.5 rnd  // ceu livre: curva de leve
ENDIF
ang += rnd
SET_CHAR_ROTATION h 10.0 0.0 ang

// velocidade: FLY_SPEED para a frente e "fz" para cima/baixo
GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS h 0.0 FLY_SPEED 0.0 fx fy rnd
fx -= px
fy -= py
SET_CHAR_VELOCITY h fx fy fz
RETURN


// ---------------------------------------------------------------------------
//  cv_land   pousa na altura de pouso
// ---------------------------------------------------------------------------
cv_land:
TASK_PLAY_ANIM_NON_INTERRUPTABLE h "IDLE_raven" "RAVEN" 4.0 TRUE TRUE TRUE TRUE -1
SET_CHAR_VELOCITY h 0.0 0.0 0.0
SET_CHAR_COLLISION h TRUE
GET_CHAR_HEADING h ang
ang += 180.0                          // vira de frente para o lado oposto
SET_CHAR_HEADING h ang
state[slot] = STATE_PERCH
GOSUB cv_audio_caw
RETURN


// ---------------------------------------------------------------------------
//  cv_die   cai com "DIE_fly" e, encostando no chao, toca "DIE_raven"
// ---------------------------------------------------------------------------
cv_die:
IF state[slot] = STATE_DIE
    GOTO cv_die_ground
ENDIF
state[slot] = STATE_DIE
SET_CHAR_COLLISION h TRUE
GOSUB cv_audio_off
TASK_PLAY_ANIM_NON_INTERRUPTABLE h "DIE_fly" "RAVEN" 4.0 TRUE TRUE TRUE TRUE -1
cv_die_ground:
GET_CHAR_HEIGHT_ABOVE_GROUND h gz
IF gz > LAND_HEIGHT
    RETURN
ENDIF
TASK_DIE_NAMED_ANIM h "DIE_raven" "RAVEN" 4.0 0
st = 0                             // 0 = deixa o corpo no chao
GOSUB cv_release
RETURN


// ---------------------------------------------------------------------------
//  cv_release   libera a vaga, o audio e o corpo
// ---------------------------------------------------------------------------
// cv_release   entrada: st = 0 deixa o corpo no chao (morreu)
//                        st = 1 apaga o corpo (saiu de perto, viraria npc)
cv_release:
SET_CHAR_COLLISION h TRUE
GOSUB cv_audio_off
IF st = 1
    DELETE_CHAR h
ELSE
    MARK_CHAR_AS_NO_LONGER_NEEDED h
ENDIF
crow[slot] = 0
state[slot] = STATE_FREE
snd[slot] = 0
timerb = 0
RETURN


// cv_forget   o corpo sumiu sozinho (o jogo liberou o ped): nao da para tocar
//             nele, entao so limpa a vaga e o audio
cv_forget:
GOSUB cv_audio_off
GOSUB cv_area_lock                  // o corvo desta area foi embora
crow[slot] = 0
state[slot] = STATE_FREE
snd[slot] = 0
timerb = 0
RETURN


// ---------------------------------------------------------------------------
//  AUDIO 3D   (0AC1 carrega, 0AC0 poe em loop, 0AAD toca/para, 0AAE libera,
//              0AC4 amarra o som no corpo do corvo)
// ---------------------------------------------------------------------------
cv_audio_off:
IF NOT snd[slot] = 0
    SET_AUDIO_STREAM_STATE snd[slot] AUDIO_STOP
    REMOVE_AUDIO_STREAM snd[slot]
    snd[slot] = 0
ENDIF
RETURN


// cv_audio_play   carrega e toca um dos dois sons (asas ou grasnado)
//                 entrada: found = 1 (asas) ou 2 (grasnado), h = o corvo
//                 volumes e "desligar_som" vem do CLEO/CORVOS.ini
cv_audio_play:
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "desligar_som" tmp
IF NOT tmp = 0
    RETURN                            // som desligado no INI
ENDIF
IF found = 2
    LOAD_3D_AUDIO_STREAM "CLEO/SOUNDS/CROW.mp3" tmp
    IF tmp = 0
        RETURN
    ENDIF
    snd[slot] = tmp
    SET_AUDIO_STREAM_LOOPED tmp FALSE    // grasnado toca uma vez (nao fica repetindo)
    READ_FLOAT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "volume_grasnado" rnd
ELSE
    LOAD_3D_AUDIO_STREAM "CLEO/SOUNDS/WINGS.mp3" tmp
    IF tmp = 0
        RETURN
    ENDIF
    snd[slot] = tmp
    SET_AUDIO_STREAM_LOOPED tmp TRUE     // asas ficam batendo enquanto voa
    READ_FLOAT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "volume_asas" rnd
ENDIF
SET_AUDIO_STREAM_STATE tmp AUDIO_PLAY
SET_PLAY_3D_AUDIO_STREAM_AT_CHAR tmp h
IF rnd > 0.0
    SET_AUDIO_STREAM_VOLUME tmp rnd
ENDIF
RETURN


// cv_audio_range   o som so toca perto do jogador
//
// O corvo pode estar a 100 m (e ficar por la): sem isso o grasnado e as asas
// continuavam tocando longe, e com varios corvos virava aquele barulho todo.
// Fora do alcance o stream e parado e liberado; quando o jogador chega perto
// de novo, o som e religado conforme o estado do corvo.
cv_audio_range:
IF LOCATE_CHAR_ANY_MEANS_CHAR_3D player h SND_RANGE SND_RANGE SND_RANGE 0
    IF snd[slot] = 0
        GOTO cv_audio_range_start
    ENDIF
    RETURN
ENDIF
IF NOT snd[slot] = 0
    GOSUB cv_audio_off
ENDIF
RETURN

cv_audio_range_start:
// so as asas (voo) religam sozinhas: o grasnado do corvo pousado tem hora
// marcada no cv_perch e nao deve ser disparado so porque o jogador chegou perto
st = state[slot]
IF st = STATE_PERCH
    RETURN
ENDIF
IF st = STATE_CLIMB
    GOSUB cv_audio_wings
    RETURN
ENDIF
IF st = STATE_FLY
    GOSUB cv_audio_wings
ENDIF
RETURN


cv_audio_wings:
GOSUB cv_audio_off
found = 1
GOSUB cv_audio_play
RETURN


cv_audio_caw:
GOSUB cv_audio_off
found = 2
GOSUB cv_audio_play
RETURN


// ---------------------------------------------------------------------------
//  cv_debug   mostra na tela o que o mod esta pensando
//
//  Ligado pelo "debug = 1" no CLEO/CORVOS.ini (padrao 0). Desligado, o custo
//  e' ler essa chave do INI uma vez por quadro.
//
//  PARA TIRAR O MODO DEBUG (quando nao for mais preciso):
//    1. apague esta sub inteira;
//    2. apague a linha "GOSUB cv_debug" no fim do cv_main;
//    3. apague as subs geradas cv_dbg_area e cv_dbg_nearest (e os blocos que
//       as geram, no tools/gen_corvos.py);
//    4. tire a chave "debug" do tools/CORVOS.ini.
//
//  O que aparece na tela:
//    area ........... em que area de corvos o jogador esta (0 = nenhuma)
//    proxima ........ numero da area mais proxima
//    dist ........... distancia ate o centro dessa area
//    trava .......... area "gasta" (os corvos de la so voltam quando o jogador
//                     sai da esfera dela; 0 = nenhuma)
//    clima / chuva .. tipo de clima do jogo e intensidade da chuva
//    modelo ......... 1 quando o CROW01.dff e a raven.ifp estao carregados
//    corvos ......... quantos corvos existem / maximo do INI
//    est ............ estado de cada vaga (0 livre, 1 pousado, 2 subindo,
//                     3 voando, 4 morto)
//    caw ............ "grasnado_a_cada" (segundos)
//    t .............. TIMERA e TIMERB, os cronometros do motor
// ---------------------------------------------------------------------------
cv_debug:
tmp = 0
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "debug" tmp
IF tmp = 0
    RETURN
ENDIF
h = player
GOSUB cv_dbg_area                   // st = area do jogador, px/py/pz = centro
GOSUB cv_dbg_nearest                // found = area mais proxima, gz = distancia
READ_MEMORY ADDR_WEATHER_TYPE 1 0 tmp
READ_MEMORY ADDR_RAIN 4 0 rnd
h = 0
IF HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
AND HAS_ANIMATION_LOADED "RAVEN"
    h = 1
ENDIF
slot = 0
IF NOT crow[0] = 0
    slot += 1
ENDIF
IF NOT crow[1] = 0
    slot += 1
ENDIF
IF NOT crow[2] = 0
    slot += 1
ENDIF
IF NOT crow[3] = 0
    slot += 1
ENDIF
IF NOT crow[4] = 0
    slot += 1
ENDIF
PRINT_HELP_FORMATTED "~y~CORVOS DEBUG~w~  area %d  proxima %d  dist %.0f m  trava %d  clima %d  chuva %.0f  modelo %d" st found gz lockarea tmp rnd h
tmp = cfg_misc / 4
// ATENCAO: os argumentos do texto formatado tem que ser numeros ou variaveis --
// passar o nome de uma constante faz o gta3sc mandar um "text label", e o jogo
// imprimiria lixo. Por isso o 5 (MAX_CROWS) aqui e' literal.
PRINT_FORMATTED_NOW "corvos %d/%d  est %d%d%d%d%d  caw %d s  t %d %d" 500 slot 5 state[0] state[1] state[2] state[3] state[4] tmp timera timerb
RETURN


// ---------------------------------------------------------------------------
//  cv_spawn   cria o corvo na vaga "slot"
//  entrada: px py pz = posicao do poleiro, ang = direcao
// ---------------------------------------------------------------------------
cv_spawn:
// esperas configuradas no CLEO/CORVOS.ini (0 = sem espera)
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "tempo_entre_corvos" st
IF timera < st
    RETURN                            // ainda e cedo para outro corvo
ENDIF
READ_INT_FROM_INI_FILE "CLEO/CORVOS.ini" "corvos" "tempo_para_renascer" st
IF timerb < st
    RETURN                            // acabou de sair um corvo
ENDIF
// a distancia minima do jogador e o "poleiro ja ocupado" sao conferidos na
// propria tabela de areas (cv_scan), que escolhe o melhor ponto para nascer
// so cria quando o modelo e a animacao estao mesmo carregados (sem esperar:
// no proximo quadro a varredura tenta de novo)
IF NOT HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
    RETURN
ENDIF
IF NOT HAS_ANIMATION_LOADED "RAVEN"
    RETURN
ENDIF
CREATE_CHAR PEDTYPE_CIVMALE CROW_MODEL px py pz h
IF NOT DOES_CHAR_EXIST h
    RETURN                            // pool cheio ou modelo ainda chegando: tenta no proximo quadro
ENDIF
SET_CHAR_HEALTH h CROW_HEALTH
SET_CHAR_HEADING h ang
crow[slot] = h
state[slot] = STATE_PERCH
snd[slot] = 0
TASK_PLAY_ANIM_NON_INTERRUPTABLE h "IDLE_raven" "RAVEN" 4.0 TRUE TRUE TRUE TRUE -1
GOSUB cv_audio_caw
timera = 0
RETURN


// ---------------------------------------------------------------------------
//  cv_scan   procura um poleiro visivel perto do jogador
//
//  Cada area tem um gatilho (o jogador precisa estar dentro da esfera, e nas
//  areas 1 a 4 fora da esfera de exclusao) e ate 5 poleiros autorais.
//  O poleiro so e usado se estiver na tela (00C2), como no mod original.
//  Estes 13 gatilhos e 64 pontos vieram dos cinco scripts originais.
// ---------------------------------------------------------------------------
cv_scan:
// os corvos daqui ja foram embora? entao nada nasce enquanto o jogador nao
// sair da area de ativacao (200 m) -- ver cv_area_unlock
IF NOT lockarea = 0
    GOSUB cv_area_unlock
    IF NOT lockarea = 0
        RETURN
    ENDIF
ENDIF

// clima: por padrao o corvo nao nasce na chuva nem na tempestade de areia
// (o CLEO/CORVOS.ini manda; o tipo de clima e lido da memoria do GTA SA 1.0)
//
// tmp = os dois bits do INI:  0 = nao nasce em nenhum dos dois (padrao)
//                             1 = pode nascer na chuva
//                             2 = pode nascer na tempestade de areia
//                             3 = pode nascer nos dois
st = cfg_misc / 4
st *= 4
tmp = cfg_misc - st
IF NOT tmp = 0
    READ_MEMORY ADDR_WEATHER_TYPE 1 0 st
    IF st = WEATHER_SANDSTORM_DESERT
    AND tmp < 2
        RETURN
    ENDIF
    IF st = WEATHER_RAINY_SF
    AND NOT tmp = 3
        IF NOT tmp = 1
            RETURN
        ENDIF
    ENDIF
    IF st = WEATHER_RAINY_COUNTRYSIDE
    AND NOT tmp = 3
        IF NOT tmp = 1
            RETURN
        ENDIF
    ENDIF
ENDIF
// Tabela gerada por tools/gen_corvos.py a partir de CROW1..CROW5.
// Nao edite a mao: mexa em tools/corvos_logic.sc.txt e rode o gerador.
// 13 areas, 64 poleiros.

// ---- AREA 01   gatilho (-1464.8, -1558.3, 101.8) raio 200   5 poleiro(s)
cv_area_01:
IF LOCATE_CHAR_ANY_MEANS_3D player -1464.7968 -1558.324 101.7578 200.0 200.0 200.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -1465.1469 -1551.7413 101.7578 10.0 10.0 10.0 0
    GOTO cv_area_01_sel
ENDIF
GOTO cv_area_02
cv_area_01_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-1466.8788, -1555.0344, 101.7578) CROW1.txt
px = -1466.8788
py = -1555.0344
pz = 101.7578
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1466.8788 -1555.0344 101.7578 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1466.8788 -1555.0344 101.7578 10.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-1437.8623, -1518.4524, 117.6562) CROW2.txt (ajustado)
px = -1437.8623
py = -1518.4524
pz = 117.6562
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1437.8623 -1518.4524 117.6562 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1437.8623 -1518.4524 117.6562 20.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-1466.9965, -1554.3170, 101.7578) CROW3.txt (ajustado)
px = -1466.9965
py = -1554.317
pz = 101.7578
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1466.9965 -1554.317 101.7578 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1466.9965 -1554.317 101.7578 20.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-1469.8451, -1553.5935, 102.1705) CROW4.txt
px = -1469.8451
py = -1553.5935
pz = 102.1705
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1469.8451 -1553.5935 102.1705 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1469.8451 -1553.5935 102.1705 20.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-1466.8971, -1551.9209, 103.5408) CROW5.txt
px = -1466.8971
py = -1551.9209
pz = 103.5408
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1466.8971 -1551.9209 103.5408 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1466.8971 -1551.9209 103.5408 20.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -1466.8788
    py = -1555.0344
    pz = 101.7578
    ang = 208.972
ENDIF
IF found = 2
    px = -1437.8623
    py = -1518.4524
    pz = 117.6562
    ang = 281.4805
ENDIF
IF found = 3
    px = -1466.9965
    py = -1554.317
    pz = 101.7578
    ang = 265.2596
ENDIF
IF found = 4
    px = -1469.8451
    py = -1553.5935
    pz = 102.1705
    ang = 274.0479
ENDIF
IF found = 5
    px = -1466.8971
    py = -1551.9209
    pz = 103.5408
    ang = 268.99
ENDIF
IF found = 0
    GOTO cv_area_02
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 02   gatilho (-1055.8, -1184.1, 129.2) raio 200   5 poleiro(s)
cv_area_02:
IF LOCATE_CHAR_ANY_MEANS_3D player -1055.7739 -1184.0836 129.1555 200.0 200.0 200.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -1060.0992 -1182.5345 129.2187 30.0 30.0 30.0 0
    GOTO cv_area_02_sel
ENDIF
GOTO cv_area_03
cv_area_02_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-1064.9078, -1157.7363, 131.3952) CROW1.txt
px = -1064.9078
py = -1157.7363
pz = 131.3952
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1064.9078 -1157.7363 131.3952 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1064.9078 -1157.7363 131.3952 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-1033.0579, -1193.5659, 130.7096) CROW2.txt
px = -1033.0579
py = -1193.5659
pz = 130.7096
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1033.0579 -1193.5659 130.7096 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1033.0579 -1193.5659 130.7096 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-1037.8374, -1180.5306, 132.4289) CROW3.txt
px = -1037.8374
py = -1180.5306
pz = 132.4289
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1037.8374 -1180.5306 132.4289 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1037.8374 -1180.5306 132.4289 10.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-1061.3170, -1206.5726, 134.2378) CROW4.txt
px = -1061.317
py = -1206.5726
pz = 134.2378
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1061.317 -1206.5726 134.2378 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1061.317 -1206.5726 134.2378 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-1069.6578, -1172.4850, 151.2312) CROW5.txt
px = -1069.6578
py = -1172.485
pz = 151.2312
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1069.6578 -1172.485 151.2312 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1069.6578 -1172.485 151.2312 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -1064.9078
    py = -1157.7363
    pz = 131.3952
    ang = 265.9022
ENDIF
IF found = 2
    px = -1033.0579
    py = -1193.5659
    pz = 130.7096
    ang = 90.8257
ENDIF
IF found = 3
    px = -1037.8374
    py = -1180.5306
    pz = 132.4289
    ang = 110.3424
ENDIF
IF found = 4
    px = -1061.317
    py = -1206.5726
    pz = 134.2378
    ang = 276.1524
ENDIF
IF found = 5
    px = -1069.6578
    py = -1172.485
    pz = 151.2312
    ang = 230.3993
ENDIF
IF found = 0
    GOTO cv_area_03
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 03   gatilho (-383.5, -1436.5, 32.3) raio 200   5 poleiro(s)
cv_area_03:
IF LOCATE_CHAR_ANY_MEANS_3D player -383.5046 -1436.4948 32.3389 200.0 200.0 200.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -383.5046 -1436.4948 32.3389 30.0 30.0 30.0 0
    GOTO cv_area_03_sel
ENDIF
GOTO cv_area_04
cv_area_03_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-367.7802, -1446.1356, 41.4857) CROW1.txt
px = -367.7802
py = -1446.1356
pz = 41.4857
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -367.7802 -1446.1356 41.4857 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -367.7802 -1446.1356 41.4857 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-372.1537, -1431.8000, 34.0000) CROW2.txt
px = -372.1537
py = -1431.8
pz = 34.0
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -372.1537 -1431.8 34.0 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -372.1537 -1431.8 34.0 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-372.2798, -1434.6667, 27.3188) CROW3.txt
px = -372.2798
py = -1434.6667
pz = 27.3188
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -372.2798 -1434.6667 27.3188 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -372.2798 -1434.6667 27.3188 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-386.3780, -1418.4358, 28.8185) CROW4.txt
px = -386.378
py = -1418.4358
pz = 28.8185
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -386.378 -1418.4358 28.8185 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -386.378 -1418.4358 28.8185 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-383.5046, -1436.4948, 32.3389) CROW5.txt
px = -383.5046
py = -1436.4948
pz = 32.3389
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -383.5046 -1436.4948 32.3389 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -383.5046 -1436.4948 32.3389 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -367.7802
    py = -1446.1356
    pz = 41.4857
    ang = 52.0551
ENDIF
IF found = 2
    px = -372.1537
    py = -1431.8
    pz = 34.0
    ang = 87.9493
ENDIF
IF found = 3
    px = -372.2798
    py = -1434.6667
    pz = 27.3188
    ang = 89.8003
ENDIF
IF found = 4
    px = -386.378
    py = -1418.4358
    pz = 28.8185
    ang = 264.7869
ENDIF
IF found = 5
    px = -383.5046
    py = -1436.4948
    pz = 32.3389
    ang = 270.5954
ENDIF
IF found = 0
    GOTO cv_area_04
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 04   gatilho (-352.4, -1047.3, 62.3) raio 200   5 poleiro(s)
cv_area_04:
IF LOCATE_CHAR_ANY_MEANS_3D player -352.3501 -1047.2784 62.296 200.0 200.0 200.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -352.3501 -1047.2784 62.296 30.0 30.0 30.0 0
    GOTO cv_area_04_sel
ENDIF
GOTO cv_area_05
cv_area_04_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-352.3501, -1047.2784, 62.2960) CROW1.txt
px = -352.3501
py = -1047.2784
pz = 62.296
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -352.3501 -1047.2784 62.296 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -352.3501 -1047.2784 62.296 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-380.8538, -1043.7256, 62.2499) CROW2.txt
px = -380.8538
py = -1043.7256
pz = 62.2499
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -380.8538 -1043.7256 62.2499 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -380.8538 -1043.7256 62.2499 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-374.5880, -1043.1473, 61.9892) CROW3.txt
px = -374.588
py = -1043.1473
pz = 61.9892
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -374.588 -1043.1473 61.9892 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -374.588 -1043.1473 61.9892 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-352.4305, -1037.0093, 62.8199) CROW4.txt
px = -352.4305
py = -1037.0093
pz = 62.8199
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -352.4305 -1037.0093 62.8199 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -352.4305 -1037.0093 62.8199 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-373.4202, -1066.0203, 60.6072) CROW5.txt
px = -373.4202
py = -1066.0203
pz = 60.6072
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -373.4202 -1066.0203 60.6072 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -373.4202 -1066.0203 60.6072 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -352.3501
    py = -1047.2784
    pz = 62.296
    ang = 144.8885
ENDIF
IF found = 2
    px = -380.8538
    py = -1043.7256
    pz = 62.2499
    ang = 186.899
ENDIF
IF found = 3
    px = -374.588
    py = -1043.1473
    pz = 61.9892
    ang = 190.3457
ENDIF
IF found = 4
    px = -352.4305
    py = -1037.0093
    pz = 62.8199
    ang = 90.4147
ENDIF
IF found = 5
    px = -373.4202
    py = -1066.0203
    pz = 60.6072
    ang = 343.277
ENDIF
IF found = 0
    GOTO cv_area_05
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 05   gatilho (-2034.5, -2535.4, 43.3) raio 200   5 poleiro(s)
cv_area_05:
IF LOCATE_CHAR_ANY_MEANS_3D player -2034.4563 -2535.4041 43.3446 200.0 200.0 200.0 0
    GOTO cv_area_05_sel
ENDIF
GOTO cv_area_06
cv_area_05_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-2062.8010, -2535.3276, 34.1357) CROW1.txt
px = -2062.801
py = -2535.3276
pz = 34.1357
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2062.801 -2535.3276 34.1357 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2062.801 -2535.3276 34.1357 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-2056.6780, -2507.3511, 32.8195) CROW2.txt
px = -2056.678
py = -2507.3511
pz = 32.8195
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2056.678 -2507.3511 32.8195 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2056.678 -2507.3511 32.8195 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-2034.4563, -2535.4041, 43.3446) CROW3.txt
px = -2034.4563
py = -2535.4041
pz = 43.3446
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2034.4563 -2535.4041 43.3446 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2034.4563 -2535.4041 43.3446 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-2052.2615, -2539.8887, 33.0796) CROW4.txt
px = -2052.2615
py = -2539.8887
pz = 33.0796
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2052.2615 -2539.8887 33.0796 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2052.2615 -2539.8887 33.0796 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-2034.4563, -2535.4041, 42.3446) CROW5.txt
px = -2034.4563
py = -2535.4041
pz = 42.3446
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2034.4563 -2535.4041 42.3446 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2034.4563 -2535.4041 42.3446 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -2062.801
    py = -2535.3276
    pz = 34.1357
    ang = 266.9862
ENDIF
IF found = 2
    px = -2056.678
    py = -2507.3511
    pz = 32.8195
    ang = 240.0392
ENDIF
IF found = 3
    px = -2034.4563
    py = -2535.4041
    pz = 43.3446
    ang = 73.971
ENDIF
IF found = 4
    px = -2052.2615
    py = -2539.8887
    pz = 33.0796
    ang = 7.857
ENDIF
IF found = 5
    px = -2034.4563
    py = -2535.4041
    pz = 42.3446
    ang = 73.971
ENDIF
IF found = 0
    GOTO cv_area_06
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 06   gatilho (-2807.3, -1530.0, 143.8) raio 200   5 poleiro(s)
cv_area_06:
IF LOCATE_CHAR_ANY_MEANS_3D player -2807.282 -1530.0153 143.8001 200.0 200.0 200.0 0
    GOTO cv_area_06_sel
ENDIF
GOTO cv_area_07
cv_area_06_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-2807.2820, -1530.0153, 142.8001) CROW1.txt
px = -2807.282
py = -1530.0153
pz = 142.8001
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2807.282 -1530.0153 142.8001 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2807.282 -1530.0153 142.8001 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-2807.4128, -1527.5131, 143.8184) CROW2.txt
px = -2807.4128
py = -1527.5131
pz = 143.8184
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2807.4128 -1527.5131 143.8184 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2807.4128 -1527.5131 143.8184 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-2807.2031, -1521.8174, 143.7891) CROW3.txt
px = -2807.2031
py = -1521.8174
pz = 143.7891
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2807.2031 -1521.8174 143.7891 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2807.2031 -1521.8174 143.7891 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-2814.1272, -1509.1133, 142.3966) CROW4.txt
px = -2814.1272
py = -1509.1133
pz = 142.3966
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2814.1272 -1509.1133 142.3966 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2814.1272 -1509.1133 142.3966 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-2804.8135, -1514.7708, 142.1157) CROW5.txt
px = -2804.8135
py = -1514.7708
pz = 142.1157
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -2804.8135 -1514.7708 142.1157 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -2804.8135 -1514.7708 142.1157 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -2807.282
    py = -1530.0153
    pz = 142.8001
    ang = 268.8192
ENDIF
IF found = 2
    px = -2807.4128
    py = -1527.5131
    pz = 143.8184
    ang = 269.7592
ENDIF
IF found = 3
    px = -2807.2031
    py = -1521.8174
    pz = 143.7891
    ang = 271.6393
ENDIF
IF found = 4
    px = -2814.1272
    py = -1509.1133
    pz = 142.3966
    ang = 0.9401
ENDIF
IF found = 5
    px = -2804.8135
    py = -1514.7708
    pz = 142.1157
    ang = 237.799
ENDIF
IF found = 0
    GOTO cv_area_07
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 07   gatilho (-1641.8, -2235.3, 34.5) raio 200   5 poleiro(s)
cv_area_07:
IF LOCATE_CHAR_ANY_MEANS_3D player -1641.8372 -2235.3174 34.4922 200.0 200.0 200.0 0
    GOTO cv_area_07_sel
ENDIF
GOTO cv_area_08
cv_area_07_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-1641.9467, -2236.8245, 34.4674) CROW1.txt
px = -1641.9467
py = -2236.8245
pz = 34.4674
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1641.9467 -2236.8245 34.4674 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1641.9467 -2236.8245 34.4674 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-1641.9543, -2239.8064, 34.4479) CROW2.txt
px = -1641.9543
py = -2239.8064
pz = 34.4479
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1641.9543 -2239.8064 34.4479 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1641.9543 -2239.8064 34.4479 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-1644.7256, -2238.1343, 31.4423) CROW3.txt
px = -1644.7256
py = -2238.1343
pz = 31.4423
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1644.7256 -2238.1343 31.4423 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1644.7256 -2238.1343 31.4423 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-1641.8873, -2243.5178, 34.4345) CROW4.txt
px = -1641.8873
py = -2243.5178
pz = 34.4345
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1641.8873 -2243.5178 34.4345 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1641.8873 -2243.5178 34.4345 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-1642.4213, -2232.5295, 34.4266) CROW5.txt
px = -1642.4213
py = -2232.5295
pz = 34.4266
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1642.4213 -2232.5295 34.4266 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1642.4213 -2232.5295 34.4266 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -1641.9467
    py = -2236.8245
    pz = 34.4674
    ang = 92.0977
ENDIF
IF found = 2
    px = -1641.9543
    py = -2239.8064
    pz = 34.4479
    ang = 94.941
ENDIF
IF found = 3
    px = -1644.7256
    py = -2238.1343
    pz = 31.4423
    ang = 91.2044
ENDIF
IF found = 4
    px = -1641.8873
    py = -2243.5178
    pz = 34.4345
    ang = 152.5948
ENDIF
IF found = 5
    px = -1642.4213
    py = -2232.5295
    pz = 34.4266
    ang = 62.6673
ENDIF
IF found = 0
    GOTO cv_area_08
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 08   gatilho (-1840.3, -1672.4, 22.1) raio 200   4 poleiro(s)
cv_area_08:
IF LOCATE_CHAR_ANY_MEANS_3D player -1840.3258 -1672.4329 22.0988 200.0 200.0 200.0 0
    GOTO cv_area_08_sel
ENDIF
GOTO cv_area_09
cv_area_08_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-1862.5463, -1694.3281, 48.2144) CROW2.txt
px = -1862.5463
py = -1694.3281
pz = 48.2144
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1862.5463 -1694.3281 48.2144 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1862.5463 -1694.3281 48.2144 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-1926.8003, -1733.5320, 27.0156) CROW3.txt
px = -1926.8003
py = -1733.532
pz = 27.0156
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1926.8003 -1733.532 27.0156 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1926.8003 -1733.532 27.0156 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-1878.5500, -1635.6218, 29.5635) CROW4.txt
px = -1878.55
py = -1635.6218
pz = 29.5635
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1878.55 -1635.6218 29.5635 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1878.55 -1635.6218 29.5635 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-1849.9385, -1676.7151, 34.2680) CROW5.txt
px = -1849.9385
py = -1676.7151
pz = 34.268
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -1849.9385 -1676.7151 34.268 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -1849.9385 -1676.7151 34.268 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -1862.5463
    py = -1694.3281
    pz = 48.2144
    ang = 70.1355
ENDIF
IF found = 2
    px = -1926.8003
    py = -1733.532
    pz = 27.0156
    ang = 201.4233
ENDIF
IF found = 3
    px = -1878.55
    py = -1635.6218
    pz = 29.5635
    ang = 214.8969
ENDIF
IF found = 4
    px = -1849.9385
    py = -1676.7151
    pz = 34.268
    ang = 91.1291
ENDIF
IF found = 0
    GOTO cv_area_09
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 09   gatilho (-545.6, -187.7, 78.4) raio 200   5 poleiro(s)
cv_area_09:
IF LOCATE_CHAR_ANY_MEANS_3D player -545.5967 -187.7389 78.4062 200.0 200.0 200.0 0
    GOTO cv_area_09_sel
ENDIF
GOTO cv_area_10
cv_area_09_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-548.2428, -194.1817, 82.5684) CROW1.txt
px = -548.2428
py = -194.1817
pz = 82.5684
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -548.2428 -194.1817 82.5684 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -548.2428 -194.1817 82.5684 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-549.7703, -183.5204, 82.0659) CROW2.txt
px = -549.7703
py = -183.5204
pz = 82.0659
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -549.7703 -183.5204 82.0659 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -549.7703 -183.5204 82.0659 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-540.1224, -194.9465, 79.4888) CROW3.txt
px = -540.1224
py = -194.9465
pz = 79.4888
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -540.1224 -194.9465 79.4888 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -540.1224 -194.9465 79.4888 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-529.4323, -181.4828, 83.6983) CROW4.txt
px = -529.4323
py = -181.4828
pz = 83.6983
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -529.4323 -181.4828 83.6983 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -529.4323 -181.4828 83.6983 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-555.7335, -182.8492, 79.3574) CROW5.txt
px = -555.7335
py = -182.8492
pz = 79.3574
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -555.7335 -182.8492 79.3574 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -555.7335 -182.8492 79.3574 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -548.2428
    py = -194.1817
    pz = 82.5684
    ang = 6.58
ENDIF
IF found = 2
    px = -549.7703
    py = -183.5204
    pz = 82.0659
    ang = 181.1084
ENDIF
IF found = 3
    px = -540.1224
    py = -194.9465
    pz = 79.4888
    ang = 5.0367
ENDIF
IF found = 4
    px = -529.4323
    py = -181.4828
    pz = 83.6983
    ang = 212.1286
ENDIF
IF found = 5
    px = -555.7335
    py = -182.8492
    pz = 79.3574
    ang = 182.3618
ENDIF
IF found = 0
    GOTO cv_area_10
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 10   gatilho (-87.7, -23.4, 6.6) raio 200   5 poleiro(s)
cv_area_10:
IF LOCATE_CHAR_ANY_MEANS_3D player -87.6538 -23.3865 6.5942 200.0 200.0 200.0 0
    GOTO cv_area_10_sel
ENDIF
GOTO cv_area_11
cv_area_10_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-59.6655, -26.5985, 25.9801) CROW1.txt
px = -59.6655
py = -26.5985
pz = 25.9801
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -59.6655 -26.5985 25.9801 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -59.6655 -26.5985 25.9801 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-67.3350, 15.5959, 5.9605) CROW2.txt
px = -67.335
py = 15.5959
pz = 5.9605
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -67.335 15.5959 5.9605 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -67.335 15.5959 5.9605 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-67.1478, 31.8082, 11.0083) CROW3.txt
px = -67.1478
py = 31.8082
pz = 11.0083
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -67.1478 31.8082 11.0083 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -67.1478 31.8082 11.0083 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-90.1248, -10.4309, 12.2726) CROW4.txt
px = -90.1248
py = -10.4309
pz = 12.2726
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -90.1248 -10.4309 12.2726 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -90.1248 -10.4309 12.2726 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-86.0666, -23.8079, 11.0097) CROW5.txt
px = -86.0666
py = -23.8079
pz = 11.0097
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -86.0666 -23.8079 11.0097 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -86.0666 -23.8079 11.0097 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -59.6655
    py = -26.5985
    pz = 25.9801
    ang = 61.0776
ENDIF
IF found = 2
    px = -67.335
    py = 15.5959
    pz = 5.9605
    ang = 196.1255
ENDIF
IF found = 3
    px = -67.1478
    py = 31.8082
    pz = 11.0083
    ang = 161.9718
ENDIF
IF found = 4
    px = -90.1248
    py = -10.4309
    pz = 12.2726
    ang = 251.8992
ENDIF
IF found = 5
    px = -86.0666
    py = -23.8079
    pz = 11.0097
    ang = 344.8876
ENDIF
IF found = 0
    GOTO cv_area_11
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 11   gatilho (2240.3, -76.5, 26.5) raio 200   5 poleiro(s)
cv_area_11:
IF LOCATE_CHAR_ANY_MEANS_3D player 2240.3303 -76.4544 26.5146 200.0 200.0 200.0 0
    GOTO cv_area_11_sel
ENDIF
GOTO cv_area_12
cv_area_11_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (2240.8752, -86.2229, 27.8548) CROW1.txt
px = 2240.8752
py = -86.2229
pz = 27.8548
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 2240.8752 -86.2229 27.8548 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 2240.8752 -86.2229 27.8548 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (2251.6724, -72.7849, 32.6133) CROW2.txt
px = 2251.6724
py = -72.7849
pz = 32.6133
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 2251.6724 -72.7849 32.6133 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 2251.6724 -72.7849 32.6133 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (2242.7910, -77.0144, 27.5148) CROW3.txt
px = 2242.791
py = -77.0144
pz = 27.5148
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 2242.791 -77.0144 27.5148 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 2242.791 -77.0144 27.5148 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (2243.1953, -66.9552, 27.7524) CROW4.txt
px = 2243.1953
py = -66.9552
pz = 27.7524
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 2243.1953 -66.9552 27.7524 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 2243.1953 -66.9552 27.7524 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (2253.7126, -58.7321, 29.7597) CROW5.txt
px = 2253.7126
py = -58.7321
pz = 29.7597
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 2253.7126 -58.7321 29.7597 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 2253.7126 -58.7321 29.7597 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = 2240.8752
    py = -86.2229
    pz = 27.8548
    ang = 10.4856
ENDIF
IF found = 2
    px = 2251.6724
    py = -72.7849
    pz = 32.6133
    ang = 90.2181
ENDIF
IF found = 3
    px = 2242.791
    py = -77.0144
    pz = 27.5148
    ang = 90.2417
ENDIF
IF found = 4
    px = 2243.1953
    py = -66.9552
    pz = 27.7524
    ang = 89.6149
ENDIF
IF found = 5
    px = 2253.7126
    py = -58.7321
    pz = 29.7597
    ang = 105.4502
ENDIF
IF found = 0
    GOTO cv_area_12
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 12   gatilho (891.0, -1103.2, 23.5) raio 200   5 poleiro(s)
cv_area_12:
IF LOCATE_CHAR_ANY_MEANS_3D player 891.0176 -1103.2471 23.5 200.0 200.0 200.0 0
    GOTO cv_area_12_sel
ENDIF
GOTO cv_area_13
cv_area_12_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (897.6763, -1079.8077, 26.0933) CROW1.txt
px = 897.6763
py = -1079.8077
pz = 26.0933
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 897.6763 -1079.8077 26.0933 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 897.6763 -1079.8077 26.0933 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (893.5620, -1117.6823, 27.3605) CROW2.txt
px = 893.562
py = -1117.6823
pz = 27.3605
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 893.562 -1117.6823 27.3605 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 893.562 -1117.6823 27.3605 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (872.3456, -1086.2764, 26.1268) CROW3.txt
px = 872.3456
py = -1086.2764
pz = 26.1268
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 872.3456 -1086.2764 26.1268 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 872.3456 -1086.2764 26.1268 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (914.5113, -1108.8202, 26.8215) CROW4.txt
px = 914.5113
py = -1108.8202
pz = 26.8215
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 914.5113 -1108.8202 26.8215 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 914.5113 -1108.8202 26.8215 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (866.8079, -1112.1425, 25.6876) CROW5.txt
px = 866.8079
py = -1112.1425
pz = 25.6876
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D 866.8079 -1112.1425 25.6876 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN 866.8079 -1112.1425 25.6876 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = 897.6763
    py = -1079.8077
    pz = 26.0933
    ang = 172.107
ENDIF
IF found = 2
    px = 893.562
    py = -1117.6823
    pz = 27.3605
    ang = 1.9887
ENDIF
IF found = 3
    px = 872.3456
    py = -1086.2764
    pz = 26.1268
    ang = 210.175
ENDIF
IF found = 4
    px = 914.5113
    py = -1108.8202
    pz = 26.8215
    ang = 338.3062
ENDIF
IF found = 5
    px = 866.8079
    py = -1112.1425
    pz = 25.6876
    ang = 36.9002
ENDIF
IF found = 0
    GOTO cv_area_13
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---- AREA 13   gatilho (-362.0, -1671.3, 27.5) raio 200   5 poleiro(s)
cv_area_13:
IF LOCATE_CHAR_ANY_MEANS_3D player -361.9819 -1671.2762 27.4701 200.0 200.0 200.0 0
    GOTO cv_area_13_sel
ENDIF
GOTO cv_scan_end
cv_area_13_sel:
GET_CHAR_COORDINATES player fx fy fz
rnd = 0.0                       // melhor distancia ate agora
found = 0                       // numero do poleiro escolhido
// candidato 1: (-350.8830, -1672.2433, 27.4166) CROW1.txt
px = -350.883
py = -1672.2433
pz = 27.4166
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -350.883 -1672.2433 27.4166 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -350.883 -1672.2433 27.4166 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 1
        ENDIF
    ENDIF
ENDIF
// candidato 2: (-354.9198, -1662.9735, 28.1631) CROW2.txt
px = -354.9198
py = -1662.9735
pz = 28.1631
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -354.9198 -1662.9735 28.1631 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -354.9198 -1662.9735 28.1631 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 2
        ENDIF
    ENDIF
ENDIF
// candidato 3: (-371.0529, -1667.2954, 27.2982) CROW3.txt
px = -371.0529
py = -1667.2954
pz = 27.2982
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -371.0529 -1667.2954 27.2982 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -371.0529 -1667.2954 27.2982 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 3
        ENDIF
    ENDIF
ENDIF
// candidato 4: (-360.7076, -1675.0261, 28.7048) CROW4.txt
px = -360.7076
py = -1675.0261
pz = 28.7048
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -360.7076 -1675.0261 28.7048 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -360.7076 -1675.0261 28.7048 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 4
        ENDIF
    ENDIF
ENDIF
// candidato 5: (-370.0551, -1678.3247, 26.5342) CROW5.txt
px = -370.0551
py = -1678.3247
pz = 26.5342
GOSUB cv_perch_busy
IF st = 0
    GET_DISTANCE_BETWEEN_COORDS_3D -370.0551 -1678.3247 26.5342 fx fy fz gz
    IF gz >= SPAWN_MIN_DIST
    AND gz <= SPAWN_MAX_DIST
        IF IS_POINT_ON_SCREEN -370.0551 -1678.3247 26.5342 5.0
            gz *= 2.0            // na tela: perde para um escondido
        ENDIF
        IF gz > rnd
            rnd = gz
            found = 5
        ENDIF
    ENDIF
ENDIF
IF found = 1
    px = -350.883
    py = -1672.2433
    pz = 27.4166
    ang = 151.9915
ENDIF
IF found = 2
    px = -354.9198
    py = -1662.9735
    pz = 28.1631
    ang = 226.8789
ENDIF
IF found = 3
    px = -371.0529
    py = -1667.2954
    pz = 27.2982
    ang = 155.1249
ENDIF
IF found = 4
    px = -360.7076
    py = -1675.0261
    pz = 28.7048
    ang = 80.2375
ENDIF
IF found = 5
    px = -370.0551
    py = -1678.3247
    pz = 26.5342
    ang = 5.6634
ENDIF
IF found = 0
    GOTO cv_scan_end
ENDIF
GOSUB cv_spawn
RETURN                          // um corvo por quadro, no maximo

// ---------------------------------------------------------------------------
//  cv_area_of_char   st = area (1..13) em que esta o personagem h
//
//  Usada quando um corvo vai embora (levantar voo, morrer longe, ser
//  solto): a area descoberta aqui e guardada em lockarea. A area e
//  descoberta pela posicao do corpo, entao nao e preciso guardar a area
//  de cada corvo (o script usa as 32 variaveis locais do jogo).
//  0 = fora de todas as areas.
// ---------------------------------------------------------------------------
cv_area_of_char:
st = 0
IF NOT DOES_CHAR_EXIST h
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1464.7968 -1558.324 101.7578 200.0 200.0 200.0 0
    st = 1
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1055.7739 -1184.0836 129.1555 200.0 200.0 200.0 0
    st = 2
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -383.5046 -1436.4948 32.3389 200.0 200.0 200.0 0
    st = 3
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -352.3501 -1047.2784 62.296 200.0 200.0 200.0 0
    st = 4
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -2034.4563 -2535.4041 43.3446 200.0 200.0 200.0 0
    st = 5
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -2807.282 -1530.0153 143.8001 200.0 200.0 200.0 0
    st = 6
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1641.8372 -2235.3174 34.4922 200.0 200.0 200.0 0
    st = 7
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1840.3258 -1672.4329 22.0988 200.0 200.0 200.0 0
    st = 8
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -545.5967 -187.7389 78.4062 200.0 200.0 200.0 0
    st = 9
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -87.6538 -23.3865 6.5942 200.0 200.0 200.0 0
    st = 10
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h 2240.3303 -76.4544 26.5146 200.0 200.0 200.0 0
    st = 11
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h 891.0176 -1103.2471 23.5 200.0 200.0 200.0 0
    st = 12
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -361.9819 -1671.2762 27.4701 200.0 200.0 200.0 0
    st = 13
    RETURN
ENDIF
RETURN

// ---------------------------------------------------------------------------
//  MODO DEBUG (so usado pelo cv_debug; para tirar o debug, apague as duas
//  subs abaixo junto com o cv_debug e a chamada dele no cv_main)
// ---------------------------------------------------------------------------

// cv_dbg_area   st = area em que esta o personagem h e px/py/pz = centro
cv_dbg_area:
st = 0
IF NOT DOES_CHAR_EXIST h
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1464.7968 -1558.324 101.7578 200.0 200.0 200.0 0
    st = 1
    px = -1464.7968
    py = -1558.324
    pz = 101.7578
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1055.7739 -1184.0836 129.1555 200.0 200.0 200.0 0
    st = 2
    px = -1055.7739
    py = -1184.0836
    pz = 129.1555
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -383.5046 -1436.4948 32.3389 200.0 200.0 200.0 0
    st = 3
    px = -383.5046
    py = -1436.4948
    pz = 32.3389
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -352.3501 -1047.2784 62.296 200.0 200.0 200.0 0
    st = 4
    px = -352.3501
    py = -1047.2784
    pz = 62.296
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -2034.4563 -2535.4041 43.3446 200.0 200.0 200.0 0
    st = 5
    px = -2034.4563
    py = -2535.4041
    pz = 43.3446
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -2807.282 -1530.0153 143.8001 200.0 200.0 200.0 0
    st = 6
    px = -2807.282
    py = -1530.0153
    pz = 143.8001
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1641.8372 -2235.3174 34.4922 200.0 200.0 200.0 0
    st = 7
    px = -1641.8372
    py = -2235.3174
    pz = 34.4922
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -1840.3258 -1672.4329 22.0988 200.0 200.0 200.0 0
    st = 8
    px = -1840.3258
    py = -1672.4329
    pz = 22.0988
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -545.5967 -187.7389 78.4062 200.0 200.0 200.0 0
    st = 9
    px = -545.5967
    py = -187.7389
    pz = 78.4062
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -87.6538 -23.3865 6.5942 200.0 200.0 200.0 0
    st = 10
    px = -87.6538
    py = -23.3865
    pz = 6.5942
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h 2240.3303 -76.4544 26.5146 200.0 200.0 200.0 0
    st = 11
    px = 2240.3303
    py = -76.4544
    pz = 26.5146
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h 891.0176 -1103.2471 23.5 200.0 200.0 200.0 0
    st = 12
    px = 891.0176
    py = -1103.2471
    pz = 23.5
    RETURN
ENDIF
IF LOCATE_CHAR_ANY_MEANS_3D h -361.9819 -1671.2762 27.4701 200.0 200.0 200.0 0
    st = 13
    px = -361.9819
    py = -1671.2762
    pz = 27.4701
    RETURN
ENDIF
RETURN

// cv_dbg_nearest   found = area mais proxima do jogador, gz = distancia
cv_dbg_nearest:
GET_CHAR_COORDINATES player fx fy fz
found = 0
rnd = 100000.0
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -1464.7968 -1558.324 101.7578 gz
IF gz < rnd
    rnd = gz
    found = 1
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -1055.7739 -1184.0836 129.1555 gz
IF gz < rnd
    rnd = gz
    found = 2
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -383.5046 -1436.4948 32.3389 gz
IF gz < rnd
    rnd = gz
    found = 3
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -352.3501 -1047.2784 62.296 gz
IF gz < rnd
    rnd = gz
    found = 4
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -2034.4563 -2535.4041 43.3446 gz
IF gz < rnd
    rnd = gz
    found = 5
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -2807.282 -1530.0153 143.8001 gz
IF gz < rnd
    rnd = gz
    found = 6
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -1641.8372 -2235.3174 34.4922 gz
IF gz < rnd
    rnd = gz
    found = 7
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -1840.3258 -1672.4329 22.0988 gz
IF gz < rnd
    rnd = gz
    found = 8
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -545.5967 -187.7389 78.4062 gz
IF gz < rnd
    rnd = gz
    found = 9
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -87.6538 -23.3865 6.5942 gz
IF gz < rnd
    rnd = gz
    found = 10
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz 2240.3303 -76.4544 26.5146 gz
IF gz < rnd
    rnd = gz
    found = 11
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz 891.0176 -1103.2471 23.5 gz
IF gz < rnd
    rnd = gz
    found = 12
ENDIF
GET_DISTANCE_BETWEEN_COORDS_3D fx fy fz -361.9819 -1671.2762 27.4701 gz
IF gz < rnd
    rnd = gz
    found = 13
ENDIF
RETURN

cv_area_unlock:
IF lockarea = 1
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -1464.7968 -1558.324 101.7578 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 2
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -1055.7739 -1184.0836 129.1555 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 3
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -383.5046 -1436.4948 32.3389 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 4
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -352.3501 -1047.2784 62.296 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 5
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -2034.4563 -2535.4041 43.3446 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 6
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -2807.282 -1530.0153 143.8001 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 7
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -1641.8372 -2235.3174 34.4922 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 8
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -1840.3258 -1672.4329 22.0988 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 9
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -545.5967 -187.7389 78.4062 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 10
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -87.6538 -23.3865 6.5942 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 11
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player 2240.3303 -76.4544 26.5146 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 12
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player 891.0176 -1103.2471 23.5 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
IF lockarea = 13
    IF NOT LOCATE_CHAR_ANY_MEANS_3D player -361.9819 -1671.2762 27.4701 200.0 200.0 200.0 0
        lockarea = 0
    ENDIF
    RETURN
ENDIF
lockarea = 0                    // area desconhecida: libera logo
RETURN

cv_scan_end:
RETURN

}
SCRIPT_END
