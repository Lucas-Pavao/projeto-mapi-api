# Cemaden PED — relatório da carga de 5 anos (2026-11-09)

DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.

Carga da fonte `CEMADEN_PED` (ADR 0021) e da chuva por ponto (ADR 0022) no `mapi_db`. Pedido: 2021-09-01 a 2026-10-08 UTC (a janela das tabelas de eventos, 2021-10-01 a 2026-10-01, com folga para o histórico antecedente). Números das consultas de `station_month_check`, `point_rain_hourly`, `ped_fetch_window`, `ingestion_quarantine` e `observation_current`, rodadas em 2026-11-09 depois do run `384c9d7b`.

## 1. Execuções

| Run | Modo | Status | Build | Brutos novos | Válidos | Quarentena | Motivo do fim |
|---|---|---|---|---|---|---|---|
| `34e03560` | BACKFILL | FAILED | sem commit | 0 | 0 | 0 | parado à mão: build com `codeVersion = unknown-build-without-git` (corrigido em `6efd51c`) |
| `4d183ebc` | BACKFILL | PARTIAL | `6efd51c` | 0 | 0 | 0 | parado à mão para corrigir a leitura de `blocks_covered_min` (`53dd332`) |
| `7c26dc3f` | BACKFILL | PARTIAL | `53dd332` | 285 137 | 280 617 | 4 520 | *connect timeout* ao emitir token, sem nova tentativa (`34d342a`) |
| `1485e5da` | BACKFILL | PARTIAL | `34d342a` | 0 | 0 | 0 | parado à mão: token pedido a cada requisição (`33b6e23`) |
| `9077d564` | BACKFILL | PARTIAL | `33b6e23` | 0 | 0 | 0 | parado à mão: consultas de chuva recente varrendo o histórico (`ce1e3c1`) |
| `cd542417` | BACKFILL | PARTIAL | `ce1e3c1` | 356 618 | 352 068 | 4 550 | HTTP 400 "Token informado é inválido" sem renovação (`53ea69a`) |
| `d880aea6` | BACKFILL | **OK** | `53ea69a` | 673 495 | 662 460 | 11 035 | fim |
| `384c9d7b` | REVALIDATE | **OK** | `c9b0cc8` | — | 1 462 431 | 24 229 | qualificação `0002` para a quarentena (70 observações retiradas) |

Os runs `PARTIAL` deixaram as janelas gravadas em `ped_fetch_window`; cada retomada pulou o que já estava gravado. As contagens de brutos são as do próprio run. Os runs encerrados à mão têm o motivo em `ingestion_run.error_summary`.

## 2. Volume, janelas e quarentena

- 46 estações a até 10 km de algum ponto (cadastro de Recife, Jaboatão, Olinda e Camaragibe).
- 2 867 janelas de 29 dias: 2 115 com dado, 752 `NO_DATA` (HTTP 202). Janelas anteriores à instalação não foram pedidas.
- 1 486 660 registros brutos; 1 462 431 observações vigentes (`0000`: 1 119 339; `0004`: 343 092).
- Quarentena:

| Regra | Motivo | Leituras | Critério |
|---|---|---|---|
| `GAP` | `AFTER_GAP` | 24 145 (1,6%) | bloco mais longo que 60 min: leitura depois de lacuna |
| `QUALIFIER` | `SUSPECT_QUALIFIER` | 84 | `qualificacao = 0002` (70 estavam na série até o REVALIDATE; 14 já estavam em `AFTER_GAP`) |

Composição das horas da série dos pontos (`point_rain_hourly`, horas com valor): 175 775 com 1 bloco (cobertura média 58,8 min), 22 208 com 2, 10 058 com 3, 5 157 com 4, 2 942 com 5, 3 573 com 6 blocos.

## 3. Fuso, com os 5 anos

Defasagem horária da Várzea (261160601A) contra a ANA 39187800 (fuso verificado, V14), 12 523 horas em comum:

| Defasagem (h) | −3 | −2 | −1 | **0** | +1 | +2 | +3 |
|---|---|---|---|---|---|---|---|
| r | 0,252 | 0,328 | 0,462 | **0,632** | 0,343 | 0,285 | 0,238 |

Confirma o UTC do teste de maio/2022 (`docs/extratos/cemaden-ped.md`, seção 4). Se o carimbo fosse hora local, o máximo estaria em ±3 h.

## 4. Cobertura por estação e ano

Cobertura = horas com valor ÷ horas do ano dentro do período de operação medido nos dados (primeira a última leitura). 2021 cobre só setembro a dezembro. "Meses reprovados" é da checagem `chuva-ponto-v4`.

| Estação | Código | Período (dados) | Maior lacuna | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 | Meses reprovados / com dado | Motivos |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Aguazinha | 260960002A | 2021-09-01 a 2026-07-22 | 425 d | 0,992 | 0,679 | 0,463 | 0,602 | 0,139 | 0,000 | 15 / 37 | PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH |
| Alberto Maia | 260345402A | 2021-09-01 a 2026-10-07 | 325 d | 0,977 | 0,559 | 0,005 | 0,002 | 0,038 | 0,134 | 7 / 26 | PARTIAL_COVERAGE, UNDERCATCH |
| Alto Mandu | 261160610A | 2021-09-01 a 2026-10-07 | 211 d | 0,930 | 0,982 | 0,988 | 0,744 | 0,012 | 0,618 | 1 / 47 | PARTIAL_COVERAGE |
| Alto Vento | 260790102G | 2021-09-01 a 2024-09-07 | 3 d | 0,975 | 0,959 | 0,970 | 0,968 | 0,000 | 0,000 | 6 / 37 | OVERCATCH, STUCK_ZERO, UNDERCATCH |
| Alto do Céu | 261160607A | 2021-09-01 a 2026-06-17 | 213 d | 0,779 | 0,455 | 0,163 | 0,266 | 0,077 | 0,110 | 6 / 40 | OVERCATCH, PARTIAL_COVERAGE, UNDERCATCH |
| Alto do Reservatório | 260790104G | 2023-11-24 a 2026-09-25 | 48 d | 0,000 | 0,000 | 0,980 | 0,959 | 0,180 | 0,035 | 15 / 33 | PARTIAL_COVERAGE, UNDERCATCH |
| Areias | 261160621A | 2021-09-01 a 2026-10-07 | 479 d | 0,994 | 0,984 | 0,985 | 0,791 | 0,000 | 0,622 | 1 / 47 | UNDERCATCH |
| Bairro dos Estados | 260345403A | 2021-09-01 a 2026-10-07 | 1046 d | 0,960 | 0,296 | 0,060 | 0,000 | 0,000 | 0,109 | 3 / 19 | PARTIAL_COVERAGE, UNDERCATCH |
| Barreira | 261160604G | 2023-12-05 a 2026-10-07 | 10 d | 0,000 | 0,000 | 0,302 | 0,957 | 0,946 | 0,966 | 1 / 35 | UNDERCATCH |
| Brega e Chique | 261160603G | 2021-09-01 a 2026-06-28 | 4 d | 0,875 | 0,955 | 0,973 | 0,988 | 0,986 | 0,996 | 21 / 58 | STUCK_ZERO, UNDERCATCH |
| COMPAZ - Alto Sta. Terezinha | 261160616G | 2023-11-01 a 2026-10-07 | 1 day | 0,000 | 0,000 | 0,986 | 0,978 | 0,979 | 0,978 | 1 / 36 | UNDERCATCH |
| COMPESA - Alto da Brasileira | 261160615G | 2023-11-01 a 2024-09-25 | 1 day | 0,000 | 0,000 | 0,986 | 0,973 | 0,000 | 0,000 | 0 / — |  |
| COMPESA - Alto da Esperança | 261160613G | 2023-11-07 a 2025-06-05 | 1 day | 0,000 | 0,000 | 0,972 | 0,976 | 0,985 | 0,000 | 3 / 20 | UNDERCATCH |
| Caixa D'Água | 260960001G | 2021-09-01 a 2025-06-04 | 11 d | 0,981 | 0,961 | 0,974 | 0,696 | 0,089 | 0,000 | 6 / 46 | PARTIAL_COVERAGE, UNDERCATCH |
| Campina do Barreto | 261160614A | 2021-09-01 a 2026-10-07 | 44 d | 0,951 | 0,975 | 0,781 | 0,617 | 0,915 | 0,621 | 4 / 62 | UNDERCATCH |
| Cavaleiro | 260790103A | 2021-09-01 a 2026-10-07 | 662 d | 0,976 | 0,839 | 0,474 | 0,058 | 0,000 | 0,324 | 9 / 39 | STUCK_ZERO, UNDERCATCH |
| Cavaleiro 2 | 260790102A | 2021-09-01 a 2024-07-31 | 48 d | 0,285 | 0,789 | 0,981 | 0,700 | 0,000 | 0,000 | 14 / 34 | OVERCATCH, STUCK_ZERO, UNDERCATCH |
| Centro | 260790107A | 2022-03-10 a 2026-06-28 | 460 d | 0,000 | 0,259 | 0,868 | 0,790 | 0,028 | 0,096 | 2 / 30 | PARTIAL_COVERAGE, UNDERCATCH |
| Convento Carmelo | 260345401G | 2021-09-01 a 2026-10-07 | 1 d | 0,970 | 0,948 | 0,970 | 0,986 | 0,975 | 0,978 | 15 / 62 | STUCK_ZERO, UNDERCATCH |
| Corrego Jenipapo | 261160608A | 2021-09-01 a 2026-06-09 | 1000 d | 0,996 | 0,899 | 0,047 | 0,000 | 0,000 | 0,118 | 0 / — |  |
| Curado 4 | 260790108A | 2021-09-01 a 2026-07-27 | 785 d | 0,989 | 0,975 | 0,992 | 0,188 | 0,000 | 0,075 | 0 / — |  |
| Dois Irmãos | 261160603A | 2021-09-01 a 2026-10-07 | 398 d | 0,994 | 0,980 | 0,984 | 0,642 | 0,176 | 0,229 | 3 / 49 | PARTIAL_COVERAGE |
| Dois Unidos | 261160606A | 2021-09-01 a 2026-10-07 | 455 d | 0,994 | 0,987 | 0,844 | 0,472 | 0,006 | 0,225 | 11 / 46 | STUCK_ZERO, UNDERCATCH |
| Engenho Velho | 260790119H | 2023-05-09 a 2026-10-07 | 16 d | 0,000 | 0,000 | 1,000 | 0,954 | 0,997 | 0,983 | 18 / 42 | STUCK_ZERO, UNDERCATCH |
| Guabiraba | 261160612A | 2024-02-28 a 2026-10-07 | 16 d | 0,000 | 0,000 | 0,000 | 0,898 | 0,930 | 0,974 | 1 / 33 | UNDERCATCH |
| Ibura | 261160615A | 2021-09-01 a 2026-10-06 | 582 d | 0,957 | 0,958 | 0,726 | 0,000 | 0,102 | 0,094 | 12 / 43 | OVERCATCH, PARTIAL_COVERAGE |
| Imbiribeira | 261160609A | 2021-09-01 a 2026-09-27 | 150 d | 0,960 | 0,578 | 0,882 | 0,616 | 0,976 | 0,972 | 20 / 54 | STUCK_ZERO, UNDERCATCH |
| Jardim Fragoso | 260960004A | 2021-09-01 a 2026-10-07 | 76 d | 0,992 | 0,713 | 0,871 | 0,357 | 0,980 | 0,930 | 11 / 60 | PARTIAL_COVERAGE, UNDERCATCH |
| Jardim Primavera | 260345404A | 2021-09-01 a 2026-10-07 | 18 d | 0,990 | 0,977 | 0,993 | 0,837 | 0,992 | 0,967 | 0 / — |  |
| Lagoa Encantada - COHAB | 261160606G | 2021-09-01 a 2026-03-20 | 305 d | 0,981 | 0,310 | 0,433 | 0,967 | 0,970 | 0,971 | 5 / 46 | UNDERCATCH |
| Morro da Conceição | 261160613A | 2021-09-01 a 2026-10-07 | 175 d | 0,991 | 0,751 | 0,470 | 0,136 | 0,002 | 0,470 | 8 / 42 | PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH |
| Muribeca | 260790101A | 2021-09-01 a 2026-10-07 | 185 d | 0,996 | 0,488 | 0,817 | 0,786 | 0,996 | 0,685 | 0 / — |  |
| Nova Descoberta | 261160605A | 2021-09-01 a 2026-10-07 | 8 d | 0,991 | 0,665 | 0,761 | 0,870 | 0,995 | 0,994 | 6 / 62 | OVERCATCH, PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH |
| Piedade | 260790106A | 2021-09-01 a 2026-07-06 | 4 d | 0,967 | 0,976 | 0,936 | 0,994 | 0,995 | 0,984 | 5 / 59 | UNDERCATCH |
| Pina | 261160617A | 2021-09-01 a 2026-10-07 | 4 d | 0,970 | 0,973 | 0,994 | 0,793 | 0,991 | 0,993 | 18 / 62 | STUCK_ZERO, UNDERCATCH |
| Porto | 261160620A | 2021-09-01 a 2026-10-07 | 62 d | 0,991 | 0,979 | 0,994 | 0,529 | 0,329 | 0,580 | 10 / 61 | UNDERCATCH |
| Prazeres | 260790104A | 2021-09-01 a 2026-10-07 | 198 d | 0,985 | 0,966 | 0,925 | 0,538 | 0,071 | 0,444 | 11 / 56 | PARTIAL_COVERAGE, UNDERCATCH |
| San Martin | 261160619A | 2021-09-01 a 2026-10-07 | 583 d | 0,972 | 0,198 | 0,448 | 0,544 | 0,000 | 0,613 | 1 / 30 | PARTIAL_COVERAGE |
| Santana | 260790115H | 2023-05-09 a 2026-10-07 | 6 d | 0,000 | 0,000 | 0,972 | 1,000 | 1,000 | 1,000 | 1 / 42 | UNDERCATCH |
| Tabajara | 260960005A | 2021-09-01 a 2026-07-15 | 454 d | 0,984 | 0,986 | 0,990 | 0,984 | 0,283 | 0,007 | 1 / 45 | UNDERCATCH |
| Timbi | 260345401A | 2021-09-01 a 2026-10-07 | 463 d | 0,988 | 0,987 | 0,995 | 0,969 | 0,245 | 0,325 | 4 / 47 | UNDERCATCH |
| Torreão | 261160618A | 2021-09-02 a 2026-10-07 | 113 d | 0,184 | 0,778 | 0,994 | 0,996 | 0,996 | 0,950 | 7 / 59 | STUCK_ZERO, UNDERCATCH |
| UR12 - COHAB II | 261160611G | 2021-09-01 a 2024-08-10 | 335 d | 0,974 | 0,882 | 0,152 | 0,972 | 0,000 | 0,000 | 1 / 26 | UNDERCATCH |
| Varzea | 261160601A | 2021-09-01 a 2026-10-07 | 285 d | 0,886 | 0,751 | 0,272 | 0,157 | 0,006 | 0,398 | 5 / 40 | PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH |
| Vila da Fábrica | 260345405A | 2021-09-01 a 2026-10-07 | 245 d | 0,977 | 0,954 | 0,994 | 0,738 | 0,240 | 0,192 | 1 / 55 | UNDERCATCH |
| Águas Compridas | 260960001A | 2021-09-01 a 2026-10-07 | 600 d | 0,994 | 0,989 | 0,985 | 0,240 | 0,000 | 0,323 | 3 / 38 | UNDERCATCH |

Estações com lacuna de centenas de dias (Alberto Maia, Bairro dos Estados, Cavaleiro, Córrego Jenipapo, Curado 4) ficaram paradas por longos períodos sem ser desativadas no cadastro. A série do ponto continua coberta pelas vizinhas válidas (seção 6).

## 5. Checagem espacial mensal (`chuva-ponto-v4`)

2 852 meses-estação: 1 526 `PASS`, 171 `UNCHECKED` (poucas vizinhas com chuva, usados), 289 `FAIL`, 866 `OFFLINE` (sem leitura no mês).

| Motivo | Meses | Defeito |
|---|---|---|
| `UNDERCATCH` | 154 | razão estação/vizinhas < 0,5 nas horas em comum (entupimento) |
| `STUCK_ZERO` | 73 | soma < 1 mm nas horas em comum com vizinhas molhadas (travado) |
| `PARTIAL_COVERAGE` | 60 | blocos cobrem < 80% das horas com valor |
| `OVERCATCH` | 6 | razão > 2,0 |

Reprovações por ano: 2021 (set–dez) 8; 2022 39; 2023 49; 2024 68; 2025 71; 2026 (jan–out) 54. A rede piora ao longo do período, junto com a cobertura.

**Picos isolados.** Antes da checagem, 151 horas-estação saíram da série pela regra da chuva ao vivo (`RainSpatialCheck`: 10 mm ou mais com pelo menos 2 vizinhas a até 6 km abaixo de 1 mm). Cada uma está em `station_month_check.metrics.isolatedPeaksRemoved`.

**Revisões da regra no mesmo dia** (as versões anteriores ficam nas tabelas como registro):

- `chuva-ponto-v1` → `v2`: cobertura parcial medida nas horas com valor, não no mês inteiro. A v1 reprovava 458 meses por dias parados, com blocos cobrindo 95–98% das horas com valor.
- `v2` → `v3`: pico isolado fora antes da checagem. A v2 levou à série do ponto 255,5 mm/h (Alto do Céu, 2022-03-18) e 215,7 mm/h (Cavaleiro 2, três horas de janeiro/2022, mês seco e por isso `UNCHECKED`).
- `v3` → `v4`: qualificação `0002` na quarentena (decisão do Lucas). Na v3 sobravam 3 horas de ponto com bloco `0002`, entre elas 215,7 mm/h no CIn e 63,0 mm/h no CIn (Várzea, 49,7 mm de um bloco `0002`).

Torreão (261160618A, a mais próxima do Derby) reprovou em 7 meses (`STUCK_ZERO` em maio/2022). Imbiribeira e Jaboatão Centro, apontados na etapa 1, não têm defeito de medição em maio/2022: razão 0,92 e 0,87 nas horas em comum.

## 6. Vínculo estação↔ponto

`point_station_link`, `method = MAX_VALID`, `linkage_config` 5 (`MAX_VALID_MONTHLY`, 5 km). Período = primeira e última hora com valor nos dados. "Horas de chuva" = fração das horas com chuva > 0 no ponto (2021-10-01 a 2026-10-01) em que a estação forneceu o maior valor.

| Ponto | Estação | Distância | Período | Horas de chuva | Meses reprovados |
|---|---|---|---|---|---|
| AGAMENON_DERBY | Torreão (261160618A) | 2,07 km | 2021-09-02 a 2026-10-07 | 0,173 | 7 |
| AGAMENON_DERBY | Porto (261160620A) | 2,36 km | 2021-09-01 a 2026-10-07 | 0,132 | 10 |
| AGAMENON_DERBY | San Martin (261160619A) | 4,07 km | 2021-09-01 a 2026-10-08 | 0,104 | 1 |
| AGAMENON_DERBY | Morro da Conceição (261160613A) | 4,37 km | 2021-09-01 a 2026-10-08 | 0,062 | 7 |
| AGAMENON_DERBY | Alto do Céu (261160607A) | 4,52 km | 2021-09-01 a 2026-06-17 | 0,042 | 6 |
| AGAMENON_DERBY | Campina do Barreto (261160614A) | 4,64 km | 2021-09-01 a 2026-10-07 | 0,152 | 4 |
| AGAMENON_DERBY | Alto Mandu (261160610A) | 4,79 km | 2021-09-01 a 2026-10-08 | 0,222 | 1 |
| AGAMENON_DERBY | COMPAZ - Alto Sta. Terezinha (261160616G) | 4,91 km | 2023-11-01 a 2026-10-08 | 0,112 | 1 |
| AV_RECIFE_IBURA | Areias (261160621A) | 0,69 km | 2021-09-01 a 2026-10-08 | 0,195 | 1 |
| AV_RECIFE_IBURA | Imbiribeira (261160609A) | 2,05 km | 2021-09-01 a 2026-09-27 | 0,194 | 20 |
| AV_RECIFE_IBURA | Lagoa Encantada - COHAB (261160606G) | 2,76 km | 2021-09-01 a 2026-03-20 | 0,149 | 5 |
| AV_RECIFE_IBURA | Ibura (261160615A) | 3,44 km | 2021-09-01 a 2026-10-06 | 0,134 | 11 |
| AV_RECIFE_IBURA | UR12 - COHAB II (261160611G) | 3,52 km | 2021-09-01 a 2024-08-10 | 0,076 | 1 |
| AV_RECIFE_IBURA | San Martin (261160619A) | 3,89 km | 2021-09-01 a 2026-10-08 | 0,106 | 1 |
| AV_RECIFE_IBURA | Pina (261160617A) | 4,53 km | 2021-09-01 a 2026-10-08 | 0,146 | 18 |
| CIN_UFPE | San Martin (261160619A) | 3,49 km | 2021-09-01 a 2026-10-08 | 0,082 | 1 |
| CIN_UFPE | Brega e Chique (261160603G) | 3,64 km | 2021-09-01 a 2026-06-28 | 0,133 | 21 |
| CIN_UFPE | Barreira (261160604G) | 3,76 km | 2023-12-05 a 2026-10-08 | 0,115 | 1 |
| CIN_UFPE | Varzea (261160601A) | 3,83 km | 2021-09-01 a 2026-10-08 | 0,089 | 5 |
| CIN_UFPE | Dois Irmãos (261160603A) | 4,13 km | 2021-09-01 a 2026-10-07 | 0,132 | 3 |
| CIN_UFPE | Bairro dos Estados (260345403A) | 4,18 km | 2021-09-01 a 2026-10-07 | 0,017 | 3 |
| CIN_UFPE | Cavaleiro 2 (260790102A) | 4,38 km | 2021-09-01 a 2024-07-31 | 0,083 | 14 |
| CIN_UFPE | Alto Mandu (261160610A) | 4,38 km | 2021-09-01 a 2026-10-08 | 0,169 | 1 |
| CIN_UFPE | Convento Carmelo (260345401G) | 4,86 km | 2021-09-01 a 2026-10-08 | 0,180 | 15 |
| JABOATAO_CENTRO | Engenho Velho (260790119H) | 0,59 km | 2023-05-09 a 2026-10-08 | 0,178 | 18 |
| JABOATAO_CENTRO | Centro (260790107A) | 0,64 km | 2022-03-10 a 2026-06-29 | 0,107 | 2 |
| JABOATAO_CENTRO | Santana (260790115H) | 1,54 km | 2023-05-09 a 2026-10-08 | 0,302 | 1 |
| JABOATAO_CENTRO | Alto Vento (260790102G) | 3,44 km | 2021-09-01 a 2024-09-07 | 0,161 | 6 |
| JABOATAO_CENTRO | Alto do Reservatório (260790104G) | 4,33 km | 2023-11-24 a 2026-09-25 | 0,050 | 15 |
| JABOATAO_CENTRO | Curado 4 (260790108A) | 4,48 km | 2021-09-01 a 2026-07-27 | 0,154 | 0 |
| JABOATAO_CENTRO | Cavaleiro (260790103A) | 4,79 km | 2021-09-01 a 2026-10-08 | 0,048 | 9 |
| MASCARENHAS_IMBIRIBEIRA | Imbiribeira (261160609A) | 1,04 km | 2021-09-01 a 2026-09-27 | 0,203 | 20 |
| MASCARENHAS_IMBIRIBEIRA | Pina (261160617A) | 2,90 km | 2021-09-01 a 2026-10-08 | 0,186 | 18 |
| MASCARENHAS_IMBIRIBEIRA | Areias (261160621A) | 3,20 km | 2021-09-01 a 2026-10-08 | 0,225 | 1 |
| MASCARENHAS_IMBIRIBEIRA | Piedade (260790106A) | 4,11 km | 2021-09-01 a 2026-07-06 | 0,238 | 5 |
| MASCARENHAS_IMBIRIBEIRA | Lagoa Encantada - COHAB (261160606G) | 4,97 km | 2021-09-01 a 2026-03-20 | 0,148 | 5 |

Com a seleção do maior valor, a hora com chuva do ponto vem de estações espalhadas por todo o raio. A estação mais próxima fornece 8–20% das horas de chuva, não a maioria. Isso é consequência direta do ADR 0022 e explica por que a opção B (mais próxima válida) dá picos e contagens menores (`docs/EVENTOS-DE-CHUVA.md`, sensibilidades).

Série do ponto (`point_rain_hourly`, `chuva-ponto-v4`, 43 824 horas da janela):

| Ponto | Cobertura | Estações válidas por hora (média; mínimo) | Pico (mm/h) | Horas ≥ 25,15 | Horas > 60,1 |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 0,9975 | 5,44; 3 | 60,5 | 44 | 1 |
| AV_RECIFE_IBURA | 0,9576 | 4,02; 0 | 61,3 | 38 | 1 |
| CIN_UFPE | 0,9960 | 4,92; 1 | 48,7 | 40 | 0 |
| JABOATAO_CENTRO | 0,9981 | 3,35; 1 | 62,0 | 33 | 1 |
| MASCARENHAS_IMBIRIBEIRA | 0,9630 | 3,55; 0 | 61,3 | 35 | 1 |

## 7. Pendências

- Resposta do Cemaden sobre `qualificacao` (`0000`, `0002`, `0004`): `docs/extratos/cemaden-ped.md`, seção 5.
- Limiares da checagem (`[sem fonte]`, ADR 0022): sensibilidade de `minRatio` 0,5 e `maxRatio` 2,0 não rodada.
- As consultas de chuva recente (`latestClosedHourRain`, `hourlyRain`) levam ~2,7 s por chamada por causa do histórico da ANA na `observation_hourly_v` (filtro por hora não desce abaixo do lag). Anterior a esta carga; a PED foi tirada delas em `ce1e3c1`.
