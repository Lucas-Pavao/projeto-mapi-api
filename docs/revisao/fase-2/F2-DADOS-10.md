# F2-DADOS-10 — Reteste do fuso da ANA_SOAP e saída da quarentena

- Data: 2026-10-11 · Branch `feat/fuso-ana-reteste` (a partir de `integracao/fase-2`, 706aadf).
- **Critério fixado em `283f6b7`** (`F2-DADOS-10-criterio.md`), commitado antes de qualquer estatística do reteste. O critério não foi alterado depois.
- Convenção: `rótulo = UTC + h`; dado em UTC−3 tem `h = −3`.

## 1. Decisão

**ANA_SOAP aprovada em `h = −3` (America/Recife), `VERIFIED`**, para o serviço legado `ServiceANA.asmx`. Os dois braços do critério aprovaram. Ficou fora da verificação o Hidro-WebService ao vivo (ver seção 6).

## 2. Braço 1 — relógio sobre leituras ao vivo (coletadas após `283f6b7`)

Sondagem de 10:16 a 10:36 UTC de 2026-10-11 (4 ciclos de 5 min; parou ao atingir o tamanho mínimo, como o critério previa). Inventário PE com 202 linhas, 62 entradas ativas com leitura nas últimas 24 h (53 estações distintas; o inventário repete estações por origem). Cabeçalho HTTP `Date` dentro de 60 s do relógio local em todas as respostas. Dados: `F2-DADOS-10-anexos/poll.jsonl.gz`; cálculo: `arm1.py`; saída: `arm1_result.json`.

Amostra: 33 leituras novas de 12 estações (mínimo 30 e 3) e 4 025 leituras no total (novas e de base).

| h | p01 do atraso, todas (min) | p05 do atraso, novas (min) | estações com > 5 % no futuro | V1 | V2 | viável |
|---:|---:|---:|---:|:---:|:---:|:---:|
| −6 | −148,1 | −159,7 | 45 | não | sim | não |
| −5 | −88,1 | −99,7 | 30 | não | sim | não |
| −4 | −28,1 | −39,7 | 5 | não | sim | não |
| **−3** | **+31,9** | **+20,3** | **0** | **sim** | **sim** | **sim** |
| −2 | +91,9 | +80,3 | 0 | sim | não | não |
| 0 | +211,9 | +200,3 | 0 | sim | não | não |
| +1 a +6 | +271,9 a +571,9 | +260,3 a +560,3 | 0 | sim | não | não |

(V1: p01 ≥ −10 min e nenhuma estação com > 5 % no futuro; V2: p05 das novas ≤ 30 min.) **Resultado do braço: `ACCEPT(−3)`.** Exatamente um `h` viável. Em particular, `h = −4` lê o futuro (p01 = −28 min, 5 estações com mais de 5 % de leituras no futuro), o que o teste de chuva não conseguia separar; UTC (`h = 0`) é rejeitado porque nenhuma leitura chega com menos de 200 min de atraso.

Limite: prova o relógio de outubro/2026. O p01 de +31,9 min mostra que a ANA publica com algum atraso (satélite em blocos), por isso o `lag` do vencedor não é próximo de 0, mas a janela [−10, 30 min] do critério ainda o contém pelo p05 das novas.

## 3. Braço 2 — constância do fuso no histórico, por ano

Mesma série e referência do F2-DADOS-07 (Open-Meteo archive na coordenada da estação, `utc_offset_seconds = 0` conferido), recortada por ano civil UTC. Cálculo: `arm2.py`; saída completa: `arm2_raw.json`.

| Estação | anos com poder | conformes | ano não conforme |
|---|---:|---:|---|
| 39084000 Caricé | 5 | 4 | 2022: ρ(−3) − ρ(0) = 0,002 (< 0,03) |
| 39167000 Vitória de Santo Antão Montante | 4 | 4 | — |
| 39187800 São Lourenço da Mata II | 6 | 6 | — |
| 39370100 Engenho Maranhão | 6 | 5 | 2022: ρ(−3) − ρ(0) = 0,021 (< 0,03) |
| **Total** | **21** | **19 (90,5 %)** | — |

Critério: ≥ 8 anos-estação (21), ≥ 80 % conformes (90,5 %), cada estação com ≥ 3 anos (4 a 6) e ≥ 60 % conformes (80 % a 100 %). **Aprovado.** Em nenhum ano UTC supera −3 por mais de 0,03 (a regra (a) falha só em 2022 de 2 estações, por UTC ficar perto de −3, nunca por UTC ganhar), e −3 fica a menos de 0,03 do máximo em todos os anos com poder (regra (b)). O melhor `h` do ano oscila entre −5 e −3 (−2 e −1 nos anos de pouca chuva, 39167000/2023 e 39187800/2021), o que confirma o viés de ~1 h da reanálise e não uma mudança de relógio.

## 4. O que foi aplicado

- Migração `V14__tz_verification_ana.sql` (próximo número livre depois da V13): `UPDATE` de configuração em `source_registry` (`ANA_SOAP` → `America/Recife`, `VERIFIED`, nota com o resultado). Nenhuma tabela criada ou alterada. `ruleVersion` `2026.10.2` → `2026.10.3`. Pode colidir com migração de outra branch no merge; renumerar se necessário.
- `AnaSoapAdapter`: `declaredTz()` passa a `America/Recife` (bruto novo nasce com o fuso; o antigo mantém `UNVERIFIED`, auditável); `parserVersion` `ana-soap/3`.
- **Unidade do nível (cm → m)** corrigida em `AnaSoapAdapter.extract`, antes da revalidação: o bruto guarda `Cota_Adotada` como a ANA entregou (ex.: `239.00`); a conversão `/100` vale só para envelopes com `origem = ServiceANA.DadosHidrometeorologicos`, e o qualificador `UNIT_UNVERIFIED` sai nesses valores. Envelopes de outra origem (coletor ao vivo) não são convertidos. Testes novos em `AnaSoapAdapterTest`: conversão 239,00 cm → 2,39 m com bruto intacto; envelope de outra origem fica 239,0 com `UNIT_UNVERIFIED`; conversão de fuso do primeiro registro (23:45 local = 02:45Z do dia seguinte).
- `LiveSensorStagingIntegrationTest` ajustado ao novo estado (só APAC_METEO24H segue `UNVERIFIED`; o teste de REVALIDATE devolve o registro a `VERIFIED` ao final, para não vazar estado).
- `./mvnw -q clean test`: **230 testes verdes** (linha de base 228; +2 do adaptador).

## 5. Saída da quarentena (REVALIDATE do bruto, nada escrito à mão)

Cinco runs `REVALIDATE` sequenciais (cada um aguardado até sair de `RUNNING`), `ANA_SOAP`, janela 2021-09-30 a 2026-10-13, todos `OK`, `quarantined = 0`:

| Janela de origem | fetched | valid | obs_inserted |
|---|---:|---:|---:|
| 2021-09-30 a 2022-10-01 (`ffe6cf54-5fdf-4073-9076-1122630823ec`) | 64 634 | 118 957 | 118 957 |
| 2022-10-01 a 2023-10-01 (`f4eeef31-26f1-4c75-87b1-3e85753f578d`) | 101 046 | 198 106 | 198 106 |
| 2023-10-01 a 2024-10-01 (`2c1d9f4a-d4a3-4be3-8652-763bac255497`) | 130 721 | 253 208 | 253 208 |
| 2024-10-01 a 2025-10-01 (`2c610c1b-1264-45b3-a1c5-86e2bc7f938e`) | 131 410 | 256 642 | 256 642 |
| 2025-10-01 a 2026-10-13 (`0757a440-b095-4d39-8d71-54b13cd9a466`) | 127 926 | 238 932 | 238 932 |
| **Total** | **555 737** | **1 065 845** | **1 065 845** |

`fetched` bate com as 555 737 linhas brutas. Em `observation`: `precipitation` 548 696 (0 a 68,8 mm por leitura de 15 min) e `water_level` 517 149 (0,63 a 11,25 m, dentro da faixa do catálogo, −5 a 50 m). Quarentena aberta da ANA_SOAP: 0. Repetição sobre 2026-10-01 a 2026-10-13: `obs_inserted = obs_updated = 0`, `obs_unchanged = 5 466` (idempotente).

Limitação que continua: a chuva da ANA entra com resolução `EVENT` e qualificador `WINDOW_UNVERIFIED` (janela da leitura de 15 min não confirmada), então **não entra em `observation_hourly_v`** até essa janela ser verificada. A saída da quarentena resolve o fuso, não a janela.

## 6. Limitações

1. O reteste prova o relógio **na janela observada** (outubro/2026); o histórico 2021–2026 é coberto só pela constância relativa do braço 2, que depende da reanálise (viés ~1 h) e discrimina UTC de UTC−3, mas não −4 de −3 isoladamente.
2. A chave do registro é `ANA_SOAP`: o coletor ao vivo do Hidro-WebService (com credencial) também passa a ser lido em UTC−3. O rótulo dele (`Data_Hora_Medicao`) **não foi testado** e a unidade do nível dele segue `UNIT_UNVERIFIED` (120,0 de nível ao vivo cai fora da faixa em m, como no teste de integração). Se o Hidro-WebService usar outro fuso, será preciso separar a fonte.
3. O braço "início de chuva contra o Cemaden" foi descartado a priori (sem poder: 5 horas de chuva em 39084000 e 0 nas outras estações da ANA nas datas em que o Cemaden existe).
4. O braço 1 teve 33 leituras novas, perto do mínimo (30); a decisão é robusta porque `h = −4` falha em V1 com folga (p01 = −28 min contra o limite de −10) e `h = −2` falha em V2 (80 contra 30).

## 7. Horas de chuva horária de pluviômetro disponíveis, por estação e ano (2021–2024)

Hora UTC rotulada pelo fim `(H−1h, H]`, soma das leituras de 15 min com ≥ 3 de 4 leituras (mesma regra do F2-DADOS-07 §1.1), sobre `observation` (`ANA_SOAP`, `precipitation`) após a revalidação. "Com chuva" = soma > 0 (em todas as linhas coincide com ≥ 0,1 mm). Máximo horário em mm/h entre parênteses. Fonte: consulta SQL direta em `observation` em 2026-10-11.

| Estação | Ano | Horas válidas | Horas com chuva | Total (mm) | Máx. (mm/h) |
|---|---:|---:|---:|---:|---:|
| 39084000 Caricé | 2022 | 2 720 | 194 | 251 | 15,4 |
| 39084000 Caricé | 2023 | 7 891 | 929 | 1 421 | 28,0 |
| 39084000 Caricé | 2024 | 8 733 | 857 | 1 335 | 34,8 |
| 39167000 Vitória de Santo Antão Montante | 2023 | 2 421 | 219 | 179 | 13,6 |
| 39167000 Vitória de Santo Antão Montante | 2024 | 8 209 | 684 | 781 | 35,0 |
| 39187800 São Lourenço da Mata II | 2021 | 1 249 | 71 | 81 | 16,6 |
| 39187800 São Lourenço da Mata II | 2022 | 7 489 | 886 | 1 810 | 28,6 |
| 39187800 São Lourenço da Mata II | 2023 | 8 657 | 1 155 | 1 809 | 27,8 |
| 39187800 São Lourenço da Mata II | 2024 | 7 922 | 927 | 1 488 | 32,8 |
| 39370100 Engenho Maranhão | 2021 | 2 170 | 210 | 296 | 23,2 |
| 39370100 Engenho Maranhão | 2022 | 7 937 | 1 331 | 2 735 | 36,4 |
| 39370100 Engenho Maranhão | 2023 | 7 758 | 1 337 | 2 410 | 32,8 |
| 39370100 Engenho Maranhão | 2024 | 7 853 | 1 011 | 1 438 | 30,4 |

Totais 2021–2024: 81 009 horas válidas e 9 811 horas com chuva (o pluviômetro chega a 36 mm/h, contra 16,7 mm/h da reanálise Open-Meteo). 2021 cobre só outubro a dezembro (carga a partir de 2021-10-01); 39084000 começa em 2022-08-24 e 39167000 em 2023-02-01 (primeira leitura de chuva disponível no staging, F2-DADOS-07 §2). Estações a 17 a 60 km do ponto Derby; nenhuma dentro da RMR urbana.

## 8. Como reproduzir

1. Banco e API: `docker compose -p projeto-mapi-api -f <worktree>/docker-compose.yml --env-file /home/lucas/Documents/Projetos/MAPI/.env.revisao up -d --build --no-deps postgres mapi-api` (volume `projeto-mapi-api_postgres_ha_data`).
2. Braço 1: `python3 -I poll.py` (grava `poll.jsonl`; só leitura da ANA), depois `arm1.py`.
3. Braço 2: exportar `raw_observation` da ANA para `ana_rain.csv`, rodar `arm2.py` (baixa o Open-Meteo archive por estação).
4. REVALIDATE: `POST /api/admin/ingestion/runs` com `{"source":"ANA_SOAP","from":...,"to":...,"mode":"REVALIDATE"}` por ano, aguardando o estado final (docs/INGESTAO.md).
