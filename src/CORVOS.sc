// ===========================================================================
//  CORVOS DO GTA V  -  versao 2 (reescrito, corrigido e otimizado)
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
//  COMO COMPILAR:  sh tools/build.sh        (usa o compilador gta3sc)
//                  saida: build/CORVOS.cs
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
CONST_INT   STATE_FLY             2
CONST_INT   STATE_DIE             3

CONST_INT   AUDIO_STOP            0
CONST_INT   AUDIO_PLAY            1

CONST_INT   SPAWN_DELAY           500      // ms entre a chegada de um corvo e outro
CONST_INT   RESPAWN_WAIT          10000    // ms de espera depois que um corvo sai
CONST_INT   LOAD_TIMEOUT          15000    // ms para o modelo/animacao carregarem
CONST_INT   PED_SCAN_TRIES        8        // quantos char a busca no poleiro pode percorrer
CONST_INT   EVENT_WHIZZED          49      // "tiro passou de raspão" (080E)

CONST_FLOAT DESPAWN_DIST          100.0    // distancia em que o corvo "esquece" o jogador
CONST_FLOAT SPAWN_MIN_DIST        8.0      // nao nasce corvo colado no jogador
CONST_FLOAT FLY_SPEED             10.0     // velocidade de cruzeiro
CONST_FLOAT CLIMB_SPEED           10.0     // velocidade de subida
CONST_FLOAT SINK_SPEED            -3.0     // descida (planeio) perto do chao
CONST_FLOAT CRUISE_ALT            25.0     // altitude que o corvo procura
CONST_FLOAT LOW_HEIGHT            8.0      // abaixo disso comeca a descer para pousar
CONST_FLOAT LAND_HEIGHT           2.0      // altura de pouso
CONST_FLOAT SIGHT_DIST            12.0     // distancia da "visao" frontal
CONST_FLOAT PLAYER_ALERT_DIST     10.0     // jogador perto demais
CONST_FLOAT PED_ALERT_DIST        10.0     // pedestre perto demais
CONST_FLOAT FIRE_ALERT_DIST       5.0      // fogo perto demais
CONST_FLOAT CAR_ALERT_DIST        10.0     // carro perto demais
CONST_FLOAT CAR_ALERT_SPEED       5.0      // ... e rapido demais
CONST_FLOAT GROUND_FAR            100.0    // altura falsa sobre a agua (nunca pousa)


// ---------------------------------------------------------------------------
//  VARIAVEIS LOCAIS
//
//   crow[]  handle do corvo          (0 = vaga livre)
//   state[]  STATE_* daquele corvo
//   snd[]    stream de audio 3D ativo (asas voando / grasnado pousado)
//
//  TIMERA e TIMERB sao as variaveis 32@ e 33@ do jogo: o motor soma o tempo
//  de cada quadro nelas, entao servem de cronometro sem travar o script.
// ---------------------------------------------------------------------------
SCRIPT_START
{
LVAR_INT   crow[5]        // tamanho = MAX_CROWS (o gta3sc exige numero fixo)
LVAR_INT   state[5]
LVAR_INT   snd[5]
LVAR_INT   slot, h, player, st, tmp, found
LVAR_FLOAT px, py, pz, fx, fy, fz, ang, gz, rnd, alt


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
slot = 0
WHILE slot < MAX_CROWS
    crow[slot] = 0
    state[slot] = STATE_FREE
    snd[slot] = 0
    slot += 1
ENDWHILE
timera = SPAWN_DELAY
timerb = RESPAWN_WAIT


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
IF timera < SPAWN_DELAY
    GOTO cv_main
ENDIF
IF timerb < RESPAWN_WAIT
    GOTO cv_main
ENDIF
// garante que o jogo nao descarregou o ator especial / a animacao
GOSUB cv_keep_loaded
GOSUB cv_scan
GOTO cv_main


// ---------------------------------------------------------------------------
//  cv_free_slot   ->  slot = primeira vaga livre, ou -1
// ---------------------------------------------------------------------------
cv_free_slot:
slot = -1
tmp = 0
WHILE tmp < MAX_CROWS
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
IF HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
AND HAS_ANIMATION_LOADED "RAVEN"
    RETURN
ENDIF
LOAD_SPECIAL_CHARACTER CROW_SLOT CROW01
REQUEST_ANIMATION "RAVEN"
timera = 0
cv_keep_wait:
WAIT 0
IF HAS_SPECIAL_CHARACTER_LOADED CROW_SLOT
AND HAS_ANIMATION_LOADED "RAVEN"
    RETURN
ENDIF
IF timera < LOAD_TIMEOUT
    GOTO cv_keep_wait
ENDIF
RETURN


// ---------------------------------------------------------------------------
//  cv_tick   um passo do corvo da vaga "slot"
// ---------------------------------------------------------------------------
cv_tick:
h = crow[slot]
IF h = 0
    RETURN
ENDIF
GET_CHAR_HEALTH h tmp
IF tmp <= 0
    GOSUB cv_die
    RETURN
ENDIF
IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D player h DESPAWN_DIST DESPAWN_DIST DESPAWN_DIST 0
    GOSUB cv_release
    RETURN
ENDIF
st = state[slot]
IF st = STATE_PERCH
    GOSUB cv_perch
ENDIF
IF st = STATE_FLY
    GOSUB cv_fly
ENDIF
RETURN


// ---------------------------------------------------------------------------
//  cv_perch   pousado: fica de olho e levanta voo quando algo assusta
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
GET_CHAR_COORDINATES h px py pz
st = 0
tmp = 0
cv_perch_peds:
GET_RANDOM_CHAR_IN_SPHERE_NO_SAVE_RECURSIVE px py pz PED_ALERT_DIST tmp 1 found
IF found = 0
    GOTO cv_perch_fire
ENDIF
IF NOT IS_CHAR_MODEL found CROW_MODEL
    GOTO cv_takeoff
ENDIF
tmp = 1
st += 1
IF st < PED_SCAN_TRIES
    GOTO cv_perch_peds
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

// carro rapido por perto
GET_RANDOM_CAR_IN_SPHERE_NO_SAVE_RECURSIVE px py pz CAR_ALERT_DIST 0 0 found
IF NOT found = 0
    GET_CAR_SPEED found rnd
    IF rnd > CAR_ALERT_SPEED
        GOTO cv_takeoff
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
state[slot] = STATE_FLY
SET_CHAR_COLLISION h FALSE
SET_CHAR_VELOCITY h 0.0 0.0 CLIMB_SPEED
GET_CHAR_HEADING h ang
SET_CHAR_ROTATION h 10.0 0.0 ang
RETURN


// ---------------------------------------------------------------------------
//  cv_fly   voando: procura altitude, desvia de parede, pousa
// ---------------------------------------------------------------------------
cv_fly:
GET_CHAR_HEADING h ang
GET_CHAR_HEIGHT_ABOVE_GROUND h gz
IF IS_CHAR_IN_WATER h
    gz = GROUND_FAR
ENDIF
IF gz <= LAND_HEIGHT
    GOTO cv_land
ENDIF

// altitude alvo (varia de leve para o voo nao ficar mecanico)
GENERATE_RANDOM_FLOAT_IN_RANGE -2.5 2.5 rnd
alt = CRUISE_ALT + rnd
IF gz >= alt
    fz = 0.0                          // ja esta na altura: voo reto
ELSE
    IF gz <= LOW_HEIGHT
        fz = SINK_SPEED                // perto do chao: plana para pousar
    ELSE
        fz = CLIMB_SPEED
    ENDIF
ENDIF

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
GOSUB cv_release
RETURN


// ---------------------------------------------------------------------------
//  cv_release   libera a vaga, o audio e o corpo
// ---------------------------------------------------------------------------
cv_release:
SET_CHAR_COLLISION h TRUE
GOSUB cv_audio_off
MARK_CHAR_AS_NO_LONGER_NEEDED h
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

cv_audio_wings:
GOSUB cv_audio_off
LOAD_3D_AUDIO_STREAM "CLEO/SOUNDS/WINGS.mp3" tmp
IF NOT tmp = 0
    snd[slot] = tmp
    SET_AUDIO_STREAM_LOOPED tmp TRUE
    SET_AUDIO_STREAM_STATE tmp AUDIO_PLAY
    SET_PLAY_3D_AUDIO_STREAM_AT_CHAR tmp h
ENDIF
RETURN

cv_audio_caw:
GOSUB cv_audio_off
LOAD_3D_AUDIO_STREAM "CLEO/SOUNDS/CROW.mp3" tmp
IF NOT tmp = 0
    snd[slot] = tmp
    SET_AUDIO_STREAM_LOOPED tmp TRUE
    SET_AUDIO_STREAM_STATE tmp AUDIO_PLAY
    SET_PLAY_3D_AUDIO_STREAM_AT_CHAR tmp h
ENDIF
RETURN


// ---------------------------------------------------------------------------
//  cv_spawn   cria o corvo na vaga "slot"
//  entrada: px py pz = posicao do poleiro, ang = direcao
// ---------------------------------------------------------------------------
cv_spawn:
IF LOCATE_CHAR_ANY_MEANS_3D player px py pz SPAWN_MIN_DIST SPAWN_MIN_DIST SPAWN_MIN_DIST 0
    RETURN                            // nasceria em cima do jogador
ENDIF
CREATE_CHAR PEDTYPE_CIVMALE CROW_MODEL px py pz h
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
// Tabela gerada por tools/gen_corvos.py a partir de CROW1..CROW5.
// Nao edite a mao: mexa em tools/corvos_logic.sc.txt e rode o gerador.
// 13 areas, 64 poleiros.

// ---- AREA 01   gatilho (-1464.8, -1558.3, 101.8) raio 100   5 poleiro(s)
cv_area_01:
IF LOCATE_CHAR_ANY_MEANS_3D player -1464.7968 -1558.324 101.7578 100.0 100.0 100.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -1465.1469 -1551.7413 101.7578 10.0 10.0 10.0 0
    GOTO cv_area_01_p1
ENDIF
GOTO cv_area_02
cv_area_01_p1:
IF IS_POINT_ON_SCREEN -1466.8788 -1555.0344 101.7578 10.0
    px = -1466.8788
    py = -1555.0344
    pz = 101.7578
    ang = 208.972
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_01_p2:
IF IS_POINT_ON_SCREEN -1464.7902 -1550.6583 101.7578 20.0
    px = -1464.7902
    py = -1550.6583
    pz = 101.7578
    ang = 281.4805
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_01_p3:
IF IS_POINT_ON_SCREEN -1464.8085 -1552.3181 101.7578 20.0
    px = -1464.8085
    py = -1552.3181
    pz = 101.7578
    ang = 265.2596
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_01_p4:
IF IS_POINT_ON_SCREEN -1469.8451 -1553.5935 102.1705 20.0
    px = -1469.8451
    py = -1553.5935
    pz = 102.1705
    ang = 274.0479
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_01_p5:
IF IS_POINT_ON_SCREEN -1466.8971 -1551.9209 103.5408 20.0
    px = -1466.8971
    py = -1551.9209
    pz = 103.5408
    ang = 268.99
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_02

// ---- AREA 02   gatilho (-1055.8, -1184.1, 129.2) raio 100   5 poleiro(s)
cv_area_02:
IF LOCATE_CHAR_ANY_MEANS_3D player -1055.7739 -1184.0836 129.1555 100.0 100.0 100.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -1060.0992 -1182.5345 129.2187 30.0 30.0 30.0 0
    GOTO cv_area_02_p1
ENDIF
GOTO cv_area_03
cv_area_02_p1:
IF IS_POINT_ON_SCREEN -1064.9078 -1157.7363 131.3952 5.0
    px = -1064.9078
    py = -1157.7363
    pz = 131.3952
    ang = 265.9022
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_02_p2:
IF IS_POINT_ON_SCREEN -1033.0579 -1193.5659 130.7096 5.0
    px = -1033.0579
    py = -1193.5659
    pz = 130.7096
    ang = 90.8257
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_02_p3:
IF IS_POINT_ON_SCREEN -1037.8374 -1180.5306 132.4289 10.0
    px = -1037.8374
    py = -1180.5306
    pz = 132.4289
    ang = 110.3424
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_02_p4:
IF IS_POINT_ON_SCREEN -1061.317 -1206.5726 134.2378 5.0
    px = -1061.317
    py = -1206.5726
    pz = 134.2378
    ang = 276.1524
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_02_p5:
IF IS_POINT_ON_SCREEN -1069.6578 -1172.485 151.2312 5.0
    px = -1069.6578
    py = -1172.485
    pz = 151.2312
    ang = 230.3993
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_03

// ---- AREA 03   gatilho (-383.5, -1436.5, 32.3) raio 100   5 poleiro(s)
cv_area_03:
IF LOCATE_CHAR_ANY_MEANS_3D player -383.5046 -1436.4948 32.3389 100.0 100.0 100.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -383.5046 -1436.4948 32.3389 30.0 30.0 30.0 0
    GOTO cv_area_03_p1
ENDIF
GOTO cv_area_04
cv_area_03_p1:
IF IS_POINT_ON_SCREEN -367.7802 -1446.1356 41.4857 5.0
    px = -367.7802
    py = -1446.1356
    pz = 41.4857
    ang = 52.0551
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_03_p2:
IF IS_POINT_ON_SCREEN -372.1537 -1431.8 34.0 5.0
    px = -372.1537
    py = -1431.8
    pz = 34.0
    ang = 87.9493
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_03_p3:
IF IS_POINT_ON_SCREEN -372.2798 -1434.6667 27.3188 5.0
    px = -372.2798
    py = -1434.6667
    pz = 27.3188
    ang = 89.8003
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_03_p4:
IF IS_POINT_ON_SCREEN -386.378 -1418.4358 28.8185 5.0
    px = -386.378
    py = -1418.4358
    pz = 28.8185
    ang = 264.7869
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_03_p5:
IF IS_POINT_ON_SCREEN -383.5046 -1436.4948 32.3389 5.0
    px = -383.5046
    py = -1436.4948
    pz = 32.3389
    ang = 270.5954
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_04

// ---- AREA 04   gatilho (-352.4, -1047.3, 62.3) raio 100   5 poleiro(s)
cv_area_04:
IF LOCATE_CHAR_ANY_MEANS_3D player -352.3501 -1047.2784 62.296 100.0 100.0 100.0 0
AND NOT LOCATE_CHAR_ANY_MEANS_3D player -352.3501 -1047.2784 62.296 30.0 30.0 30.0 0
    GOTO cv_area_04_p1
ENDIF
GOTO cv_area_05
cv_area_04_p1:
IF IS_POINT_ON_SCREEN -352.3501 -1047.2784 62.296 5.0
    px = -352.3501
    py = -1047.2784
    pz = 62.296
    ang = 144.8885
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_04_p2:
IF IS_POINT_ON_SCREEN -380.8538 -1043.7256 62.2499 5.0
    px = -380.8538
    py = -1043.7256
    pz = 62.2499
    ang = 186.899
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_04_p3:
IF IS_POINT_ON_SCREEN -374.588 -1043.1473 61.9892 5.0
    px = -374.588
    py = -1043.1473
    pz = 61.9892
    ang = 190.3457
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_04_p4:
IF IS_POINT_ON_SCREEN -352.4305 -1037.0093 62.8199 5.0
    px = -352.4305
    py = -1037.0093
    pz = 62.8199
    ang = 90.4147
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_04_p5:
IF IS_POINT_ON_SCREEN -373.4202 -1066.0203 60.6072 5.0
    px = -373.4202
    py = -1066.0203
    pz = 60.6072
    ang = 343.277
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_05

// ---- AREA 05   gatilho (-2034.5, -2535.4, 43.3) raio 100   5 poleiro(s)
cv_area_05:
IF LOCATE_CHAR_ANY_MEANS_3D player -2034.4563 -2535.4041 43.3446 100.0 100.0 100.0 0
    GOTO cv_area_05_p1
ENDIF
GOTO cv_area_06
cv_area_05_p1:
IF IS_POINT_ON_SCREEN -2062.801 -2535.3276 34.1357 5.0
    px = -2062.801
    py = -2535.3276
    pz = 34.1357
    ang = 266.9862
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_05_p2:
IF IS_POINT_ON_SCREEN -2056.678 -2507.3511 32.8195 5.0
    px = -2056.678
    py = -2507.3511
    pz = 32.8195
    ang = 240.0392
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_05_p3:
IF IS_POINT_ON_SCREEN -2034.4563 -2535.4041 43.3446 5.0
    px = -2034.4563
    py = -2535.4041
    pz = 43.3446
    ang = 73.971
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_05_p4:
IF IS_POINT_ON_SCREEN -2052.2615 -2539.8887 33.0796 5.0
    px = -2052.2615
    py = -2539.8887
    pz = 33.0796
    ang = 7.857
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_05_p5:
IF IS_POINT_ON_SCREEN -2034.4563 -2535.4041 42.3446 5.0
    px = -2034.4563
    py = -2535.4041
    pz = 42.3446
    ang = 73.971
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_06

// ---- AREA 06   gatilho (-2807.3, -1530.0, 143.8) raio 100   5 poleiro(s)
cv_area_06:
IF LOCATE_CHAR_ANY_MEANS_3D player -2807.282 -1530.0153 143.8001 100.0 100.0 100.0 0
    GOTO cv_area_06_p1
ENDIF
GOTO cv_area_07
cv_area_06_p1:
IF IS_POINT_ON_SCREEN -2807.282 -1530.0153 142.8001 5.0
    px = -2807.282
    py = -1530.0153
    pz = 142.8001
    ang = 268.8192
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_06_p2:
IF IS_POINT_ON_SCREEN -2807.4128 -1527.5131 143.8184 5.0
    px = -2807.4128
    py = -1527.5131
    pz = 143.8184
    ang = 269.7592
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_06_p3:
IF IS_POINT_ON_SCREEN -2807.2031 -1521.8174 143.7891 5.0
    px = -2807.2031
    py = -1521.8174
    pz = 143.7891
    ang = 271.6393
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_06_p4:
IF IS_POINT_ON_SCREEN -2814.1272 -1509.1133 142.3966 5.0
    px = -2814.1272
    py = -1509.1133
    pz = 142.3966
    ang = 0.9401
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_06_p5:
IF IS_POINT_ON_SCREEN -2804.8135 -1514.7708 142.1157 5.0
    px = -2804.8135
    py = -1514.7708
    pz = 142.1157
    ang = 237.799
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_07

// ---- AREA 07   gatilho (-1641.8, -2235.3, 34.5) raio 100   5 poleiro(s)
cv_area_07:
IF LOCATE_CHAR_ANY_MEANS_3D player -1641.8372 -2235.3174 34.4922 100.0 100.0 100.0 0
    GOTO cv_area_07_p1
ENDIF
GOTO cv_area_08
cv_area_07_p1:
IF IS_POINT_ON_SCREEN -1641.9467 -2236.8245 34.4674 5.0
    px = -1641.9467
    py = -2236.8245
    pz = 34.4674
    ang = 92.0977
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_07_p2:
IF IS_POINT_ON_SCREEN -1641.9543 -2239.8064 34.4479 5.0
    px = -1641.9543
    py = -2239.8064
    pz = 34.4479
    ang = 94.941
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_07_p3:
IF IS_POINT_ON_SCREEN -1644.7256 -2238.1343 31.4423 5.0
    px = -1644.7256
    py = -2238.1343
    pz = 31.4423
    ang = 91.2044
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_07_p4:
IF IS_POINT_ON_SCREEN -1641.8873 -2243.5178 34.4345 5.0
    px = -1641.8873
    py = -2243.5178
    pz = 34.4345
    ang = 152.5948
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_07_p5:
IF IS_POINT_ON_SCREEN -1642.4213 -2232.5295 34.4266 5.0
    px = -1642.4213
    py = -2232.5295
    pz = 34.4266
    ang = 62.6673
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_08

// ---- AREA 08   gatilho (-1840.3, -1672.4, 22.1) raio 100   4 poleiro(s)
cv_area_08:
IF LOCATE_CHAR_ANY_MEANS_3D player -1840.3258 -1672.4329 22.0988 100.0 100.0 100.0 0
    GOTO cv_area_08_p1
ENDIF
GOTO cv_area_09
cv_area_08_p1:
IF IS_POINT_ON_SCREEN -1862.5463 -1694.3281 48.2144 5.0
    px = -1862.5463
    py = -1694.3281
    pz = 48.2144
    ang = 70.1355
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_08_p2:
IF IS_POINT_ON_SCREEN -1926.8003 -1733.532 27.0156 5.0
    px = -1926.8003
    py = -1733.532
    pz = 27.0156
    ang = 201.4233
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_08_p3:
IF IS_POINT_ON_SCREEN -1878.55 -1635.6218 29.5635 5.0
    px = -1878.55
    py = -1635.6218
    pz = 29.5635
    ang = 214.8969
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_08_p4:
IF IS_POINT_ON_SCREEN -1849.9385 -1676.7151 34.268 5.0
    px = -1849.9385
    py = -1676.7151
    pz = 34.268
    ang = 91.1291
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_09

// ---- AREA 09   gatilho (-545.6, -187.7, 78.4) raio 100   5 poleiro(s)
cv_area_09:
IF LOCATE_CHAR_ANY_MEANS_3D player -545.5967 -187.7389 78.4062 100.0 100.0 100.0 0
    GOTO cv_area_09_p1
ENDIF
GOTO cv_area_10
cv_area_09_p1:
IF IS_POINT_ON_SCREEN -548.2428 -194.1817 82.5684 5.0
    px = -548.2428
    py = -194.1817
    pz = 82.5684
    ang = 6.58
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_09_p2:
IF IS_POINT_ON_SCREEN -549.7703 -183.5204 82.0659 5.0
    px = -549.7703
    py = -183.5204
    pz = 82.0659
    ang = 181.1084
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_09_p3:
IF IS_POINT_ON_SCREEN -540.1224 -194.9465 79.4888 5.0
    px = -540.1224
    py = -194.9465
    pz = 79.4888
    ang = 5.0367
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_09_p4:
IF IS_POINT_ON_SCREEN -529.4323 -181.4828 83.6983 5.0
    px = -529.4323
    py = -181.4828
    pz = 83.6983
    ang = 212.1286
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_09_p5:
IF IS_POINT_ON_SCREEN -555.7335 -182.8492 79.3574 5.0
    px = -555.7335
    py = -182.8492
    pz = 79.3574
    ang = 182.3618
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_10

// ---- AREA 10   gatilho (-87.7, -23.4, 6.6) raio 100   5 poleiro(s)
cv_area_10:
IF LOCATE_CHAR_ANY_MEANS_3D player -87.6538 -23.3865 6.5942 100.0 100.0 100.0 0
    GOTO cv_area_10_p1
ENDIF
GOTO cv_area_11
cv_area_10_p1:
IF IS_POINT_ON_SCREEN -59.6655 -26.5985 25.9801 5.0
    px = -59.6655
    py = -26.5985
    pz = 25.9801
    ang = 61.0776
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_10_p2:
IF IS_POINT_ON_SCREEN -67.335 15.5959 5.9605 5.0
    px = -67.335
    py = 15.5959
    pz = 5.9605
    ang = 196.1255
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_10_p3:
IF IS_POINT_ON_SCREEN -67.1478 31.8082 11.0083 5.0
    px = -67.1478
    py = 31.8082
    pz = 11.0083
    ang = 161.9718
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_10_p4:
IF IS_POINT_ON_SCREEN -90.1248 -10.4309 12.2726 5.0
    px = -90.1248
    py = -10.4309
    pz = 12.2726
    ang = 251.8992
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_10_p5:
IF IS_POINT_ON_SCREEN -86.0666 -23.8079 11.0097 5.0
    px = -86.0666
    py = -23.8079
    pz = 11.0097
    ang = 344.8876
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_11

// ---- AREA 11   gatilho (2240.3, -76.5, 26.5) raio 100   5 poleiro(s)
cv_area_11:
IF LOCATE_CHAR_ANY_MEANS_3D player 2240.3303 -76.4544 26.5146 100.0 100.0 100.0 0
    GOTO cv_area_11_p1
ENDIF
GOTO cv_area_12
cv_area_11_p1:
IF IS_POINT_ON_SCREEN 2240.8752 -86.2229 27.8548 5.0
    px = 2240.8752
    py = -86.2229
    pz = 27.8548
    ang = 10.4856
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_11_p2:
IF IS_POINT_ON_SCREEN 2251.6724 -72.7849 32.6133 5.0
    px = 2251.6724
    py = -72.7849
    pz = 32.6133
    ang = 90.2181
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_11_p3:
IF IS_POINT_ON_SCREEN 2242.791 -77.0144 27.5148 5.0
    px = 2242.791
    py = -77.0144
    pz = 27.5148
    ang = 90.2417
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_11_p4:
IF IS_POINT_ON_SCREEN 2243.1953 -66.9552 27.7524 5.0
    px = 2243.1953
    py = -66.9552
    pz = 27.7524
    ang = 89.6149
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_11_p5:
IF IS_POINT_ON_SCREEN 2253.7126 -58.7321 29.7597 5.0
    px = 2253.7126
    py = -58.7321
    pz = 29.7597
    ang = 105.4502
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_12

// ---- AREA 12   gatilho (891.0, -1103.2, 23.5) raio 100   5 poleiro(s)
cv_area_12:
IF LOCATE_CHAR_ANY_MEANS_3D player 891.0176 -1103.2471 23.5 100.0 100.0 100.0 0
    GOTO cv_area_12_p1
ENDIF
GOTO cv_area_13
cv_area_12_p1:
IF IS_POINT_ON_SCREEN 897.6763 -1079.8077 26.0933 5.0
    px = 897.6763
    py = -1079.8077
    pz = 26.0933
    ang = 172.107
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_12_p2:
IF IS_POINT_ON_SCREEN 893.562 -1117.6823 27.3605 5.0
    px = 893.562
    py = -1117.6823
    pz = 27.3605
    ang = 1.9887
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_12_p3:
IF IS_POINT_ON_SCREEN 872.3456 -1086.2764 26.1268 5.0
    px = 872.3456
    py = -1086.2764
    pz = 26.1268
    ang = 210.175
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_12_p4:
IF IS_POINT_ON_SCREEN 914.5113 -1108.8202 26.8215 5.0
    px = 914.5113
    py = -1108.8202
    pz = 26.8215
    ang = 338.3062
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_12_p5:
IF IS_POINT_ON_SCREEN 866.8079 -1112.1425 25.6876 5.0
    px = 866.8079
    py = -1112.1425
    pz = 25.6876
    ang = 36.9002
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_area_13

// ---- AREA 13   gatilho (-362.0, -1671.3, 27.5) raio 100   5 poleiro(s)
cv_area_13:
IF LOCATE_CHAR_ANY_MEANS_3D player -361.9819 -1671.2762 27.4701 100.0 100.0 100.0 0
    GOTO cv_area_13_p1
ENDIF
GOTO cv_scan_end
cv_area_13_p1:
IF IS_POINT_ON_SCREEN -350.883 -1672.2433 27.4166 5.0
    px = -350.883
    py = -1672.2433
    pz = 27.4166
    ang = 151.9915
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_13_p2:
IF IS_POINT_ON_SCREEN -354.9198 -1662.9735 28.1631 5.0
    px = -354.9198
    py = -1662.9735
    pz = 28.1631
    ang = 226.8789
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_13_p3:
IF IS_POINT_ON_SCREEN -371.0529 -1667.2954 27.2982 5.0
    px = -371.0529
    py = -1667.2954
    pz = 27.2982
    ang = 155.1249
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_13_p4:
IF IS_POINT_ON_SCREEN -360.7076 -1675.0261 28.7048 5.0
    px = -360.7076
    py = -1675.0261
    pz = 28.7048
    ang = 80.2375
    GOSUB cv_spawn
    RETURN
ENDIF
cv_area_13_p5:
IF IS_POINT_ON_SCREEN -370.0551 -1678.3247 26.5342 5.0
    px = -370.0551
    py = -1678.3247
    pz = 26.5342
    ang = 5.6634
    GOSUB cv_spawn
    RETURN
ENDIF
GOTO cv_scan_end

cv_scan_end:
RETURN

}
SCRIPT_END
