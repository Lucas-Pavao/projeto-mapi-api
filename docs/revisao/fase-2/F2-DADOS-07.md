# F2-DADOS-07 — Verificação do fuso de ANA, APAC meteorologia24h e Cemaden

- Data: 2026-10-10 · Branch: `feat/verificacao-fuso` (a partir de `integracao/fase-2`, 0e307b2)
- Decisão do autor: tirar as fontes da quarentena `TZ_UNVERIFIED` **com evidência**, por correlação cruzada com o Open-Meteo (UTC confirmado: `utc_offset_seconds = 0` conferido a cada resposta, `OpenMeteoArchiveAdapter`).
- Convenção de sinal: `h` é o fuso do rótulo da fonte (`rótulo = UTC + h`); portanto `UTC = rótulo − h`. Dado em UTC−3 tem `h = −3`.

## 1. Critério de decisão (fixado ANTES de calcular)

Este bloco foi commitado antes de qualquer correlação ser calculada sobre os dados reais (ver `git log` deste arquivo: o commit "critério pré-fixado" precede o commit dos resultados). Os limiares abaixo são escolhas de método do autor desta tarefa, `[sem fonte]` na literatura; a justificativa de cada um está na coluna "Por quê".

### 1.1 Teste A — correlação cruzada de chuva (fontes com histórico: ANA)

| Item | Regra | Por quê |
|---|---|---|
| Série da estação | leituras de 15 min (`Chuva`) somadas em hora UTC rotulada pelo **fim** `(H−1h, H]`; hora válida só com ≥ 3 de 4 leituras; nunca preencher lacuna | mesma convenção do Open-Meteo (`precipitation` = soma da hora anterior) e do ADR 0006 §5 |
| Referência | Open-Meteo archive, `precipitation` horária, `timezone=GMT`, na coordenada da própria estação (inventário ANA) | UTC confirmado; a grade mais próxima da estação, não do ponto crítico, evita somar distância |
| Hipóteses | `h` inteiro em −6..+6 (13), incluindo `h = 0` (UTC) e `h = −3` (UTC−3) | passo de 1 h; −3 e 0 são as fisicamente plausíveis |
| Estatística principal | Spearman ρ(h) nas horas comuns (robusta à calibração do pluviômetro e a caudas pesadas); Pearson r(h) reportado ao lado | chuva é assimétrica, com muitos zeros |
| Defasagem de máxima correlação cruzada | `argmax_h ρ(h)` (a correlação cruzada em passos de 1 h é exatamente ρ(h)) | |
| Incerteza | bootstrap por blocos de dias UTC, B = 1000, semente 20261010, IC 95 % percentil; os mesmos dias sorteados valem para as 13 hipóteses | dias consecutivos de chuva são correlatos; sortear horas subestimaria o IC |
| Poder mínimo | ≥ 50 horas com chuva (referência ≥ 0,1 mm) em ≥ 5 dias UTC distintos | sem chuva suficiente a correlação não discrimina fuso |

**Estação aprovada (ACCEPT)** se, simultaneamente: (a) poder mínimo atendido; (b) `argmax ρ ∈ {−3, 0}`; (c) ρ(vencedor) ≥ 0,30; (d) ρ(vencedor) − ρ(segundo colocado) ≥ 0,05; (e) em ≥ 95 % das réplicas do bootstrap ρ(vencedor) > ρ(segundo). Resultados possíveis quando falha: `INSUFFICIENT_DATA` (a), `WEAK_CORRELATION` (c), `IMPLAUSIBLE_OFFSET` (argmax fora de {−3, 0} com poder e correlação suficientes), `AMBIGUOUS` (d ou e).

**Fonte aprovada** se ≥ 2 estações forem ACCEPT, todas no mesmo `h`, e nenhuma estação tiver `IMPLAUSIBLE_OFFSET`. Caso contrário, a fonte **continua em quarentena** e o motivo é registrado.

### 1.2 Teste B — causalidade e latência contra o relógio (fontes só ao vivo: APAC meteorologia24h e Cemaden)

Os endpoints da APAC devolvem só uma janela rolante de ~24 h, sem consulta por intervalo e sem credencial (seção 2); não há meio de reunir ≥ 6 meses de chuva. Para essas fontes o teste A não tem poder e vale o teste B, que usa o instante em que o coletor ao vivo baixou a leitura (`collected_at`, relógio do servidor em UTC):

- Para cada registro de execução `LIVE`, `lag(h) = collected_at − (rótulo − h)`.
- Hipótese `h` aprovada se: **p01 do lag(h) ≥ −10 min** (nenhuma leitura vem do futuro; tolerância de 10 min para relógio e atraso de publicação) **e** **0 ≤ mediana do lag(h) ≤ 30 min** (telemetria em tempo quase real; hipóteses vizinhas diferem em 60 min, então no máximo uma satisfaz as duas).
- Poder mínimo: ≥ 200 registros de ≥ 20 estações distintas.
- Aprovada se existir exatamente um `h` aprovado e ele estiver em {−3, 0}.
- O teste A é calculado também para essas fontes, só como evidência exploratória (sem peso na decisão), contra o Open-Meteo `forecast` com `past_days` (archive tem atraso de 2 dias, `openMeteoArchiveLagDays`).

Limite assumido desde já: o teste B prova o fuso **da janela observada**, não que a APAC nunca tenha mudado de fuso. Só o bruto coletado a partir de agora sai da quarentena por ele.

### 1.3 O que acontece com o resultado

- Fonte aprovada com `h = −3` → `source_registry.source_tz = 'America/Recife'`, `verification_state = 'VERIFIED'`; com `h = 0` → `'UTC'`. Mudança de dado de configuração via migração Flyway; o bruto sai da quarentena por `REVALIDATE` (nunca por `UPDATE` em `observation`).
- Fonte não aprovada → continua `UNVERIFIED`, nota com o motivo.

### 1.4 Aditamento (posterior ao resultado do teste A da ANA; declarado como pós-hoc)

O teste A da ANA foi calculado antes deste aditamento e **não aprovou nenhuma estação** (seção 3.1). Este item foi escrito depois de ver esse resultado e **antes** de calcular o teste abaixo; por isso ele é registrado como pós-hoc e **não** altera a decisão pré-fixada da seção 1.1. Serve de evidência independente para o autor decidir.

Teste C — relógio da ANA: para cada estação telemétrica ativa de PE do inventário `ListaEstacoesTelemetricas`, baixar `DadosHidrometeorologicos` de D−1 a D+1 e tomar o rótulo mais recente; `lag(h) = instante do download (UTC) − (rótulo − h)`. Hipótese `h` viável se **≤ 2 % das estações** tiverem `lag < −10 min` (leitura do futuro) **e** a estação mais fresca tiver `lag ≤ 30 min` (a telemetria tem alguma estação em dia com transmissão recente). Poder: ≥ 50 estações com leitura. Exatamente um `h` viável, em {−3, 0}, é a conclusão do teste. Limite: estações satelitais transmitem em blocos e ficam atrasadas; por isso o limite superior usa a estação mais fresca, não a mediana.

## 2. Dados: o que foi possível carregar no staging

Tudo foi gravado em `raw_observation` com `source_tz = 'UNVERIFIED'` e foi para a quarentena `TZ_UNVERIFIED`, como na carga ao vivo (F2-BACK-12). A conversão de fuso continua só na validação.

| Fonte | Como acessa | Credencial | Cobertura obtida | Linhas brutas |
|---|---|---|---|---|
| ANA_SOAP (histórico) | serviço legado público `ServiceANA.asmx/DadosHidrometeorologicos` (15 min), por estação e mês, adaptador `AnaSoapAdapter.fetch` | **nenhuma** (`ANA_IDENTIFICADOR`/`ANA_SENHA` vazios e desnecessários; o Hidro-WebService autenticado não foi usado) | 4 estações com pluviômetro: 39187800 e 39370100 desde 2021-10-01; 39084000 desde 2022-08-24; 39167000 desde 2023-02-01; todas até 2026-10-10 (39187800 até 2026-09-30) | 555.737 (137.303 a 158.142 leituras de chuva por estação) |
| CEMADEN (espelho da APAC, `/cemaden/`) | janela rolante de ~24 h, sem consulta por intervalo (`CemadenAdapter.fetch` grava o instantâneo completo) | nenhuma | 2026-10-09 20:45 a 2026-10-10 20:40, 335 estações | 8.421 |
| APAC_METEO24H (`/meteorologia24h/`) | idem; em 2026-10-10 o endpoint trazia só 1 estação fluviométrica (Jaboatão, Rio Duas Unas) | nenhuma | 48 leituras de 24 h | 60 |
| APAC portal (histórico pluviométrico) | `boletins/historico-pluviometrico/diario.php`, só boletim diário | nenhuma | não serve: sem hora, não testa fuso horário | 0 |
| Cemaden direto (`resources.cemaden.gov.br/graficos/interativo/getJson2.php`) | só o último valor por estação (público) | histórico exige conta | não carregado | 0 |

**Lacuna declarada:** o requisito de "pelo menos 6 meses com chuva relevante por fonte" só foi atingido para a ANA. Para CEMADEN e APAC meteorologia24h **não existe histórico acessível sem credencial** (a janela é de 24 h), então elas não podem passar pelo teste A com poder; foram avaliadas pelo teste B. Estações da ANA: o inventário (`ListaEstacoesTelemetricas`) tem 17 estações distintas a ≤ 60 km do Recife e nenhuma dentro da RMR urbana; só 4 delas têm pluviômetro com série contínua (17 sondadas para maio/2025; lista fixada em `IngestionProperties.anaHistoricalStations`). `point_station_link` está vazia no banco (0 linhas), então a "ligação por raio" não define estações; as mais próximas do Derby e de Mascarenhas/Imbiribeira estão na seção 3.2.

## 3. Resultados

Referência: Open-Meteo archive na coordenada de cada estação (`utc_offset_seconds = 0` conferido pelo serviço a cada resposta). Dados brutos completos: `F2-DADOS-07-resultados.json`; evidência pós-hoc da ANA: `F2-DADOS-07-ana-relogio.json`.

### 3.1 Teste A — ANA_SOAP

**Nenhuma estação passou no critério pré-fixado.** Resumo:

| Estação | Distância ao ponto Derby | Spearman máx. | argmax | margem sobre o 2º | Decisão |
|---|---|---:|---:|---:|---|
| 39187800 São Lourenço da Mata II | 17 km do Derby | 0,293 | −4 h | 0,006 | `WEAK_CORRELATION` (< 0,30) |
| 39370100 Engenho Maranhão | 45 km do Derby | 0,327 | −4 h | 0,005 | `IMPLAUSIBLE_OFFSET` (argmax −4) |
| 39167000 Vitória de Santo Antão Montante | 48 km do Derby | 0,252 | −4 h | 0,009 | `WEAK_CORRELATION` |
| 39084000 Caricé | 60 km do Derby | 0,308 | −4 h | 0,009 | `IMPLAUSIBLE_OFFSET` |

**Decisão da fonte (pré-fixada): `INSUFFICIENT_DATA` — ANA_SOAP continua em quarentena** (0 estações aceitas; mínimo 2).

Leitura dos números, sem alterar a decisão: (i) a correlação horária é baixa (0,25 a 0,33) porque as estações ficam a dezenas de km do ponto de grade e a chuva convectiva é local; o critério `ρ ≥ 0,30` e margem `≥ 0,05` foi mais exigente do que o poder dos dados permite. (ii) O argmax cai em −4 h nas quatro estações, com −3 h a ≤ 0,009 de distância (dentro do IC), o que sugere um viés sistemático de ~1 h entre pluviômetro e grade do Open-Meteo, e não um fuso de −4 h (inexistente no Recife). (iii) **UTC (h = 0) é rejeitado nas quatro estações**: o IC 95 % de ρ(0) fica disjunto do IC de ρ(argmax) em todas (por exemplo Caricé: ρ(0) = 0,232 [0,209; 0,255] contra ρ(−4) = 0,308 [0,285; 0,329]). Como as hipóteses plausíveis eram só −3 e 0, a ANA não é UTC, mas pela chuva não se separa −3 de −4. Isso é uma leitura pós-hoc.

Tabelas completas por estação (todas as 13 hipóteses):

**39084000 CARICÉ** — 2022-08-24 a 2026-10-10, 137303 leituras de 15 min; 59.8 km do Derby, 66.5 km de Mascarenhas/Imbiribeira; argmax = -4 h, segundo = -5 h, margem = 0.009, vencedor > segundo em 97.9 % das réplicas; decisão: `IMPLAUSIBLE_OFFSET`.

| h (rótulo = UTC + h) | horas comuns | horas com chuva (OM ≥ 0,1 mm) | Spearman | IC 95 % | Pearson | IC 95 % |
|---:|---:|---:|---:|---|---:|---|
| -6 | 34262 | 10311 | 0.297 | [0.276; 0.318] | 0.179 | [0.144; 0.215] |
| -5 | 34263 | 10323 | 0.299 | [0.276; 0.320] | 0.203 | [0.158; 0.245] |
| -4 **←** | 34264 | 10327 | 0.308 | [0.285; 0.329] | 0.198 | [0.158; 0.236] |
| -3 | 34265 | 10326 | 0.299 | [0.276; 0.320] | 0.230 | [0.171; 0.293] |
| -2 | 34266 | 10330 | 0.279 | [0.256; 0.300] | 0.221 | [0.163; 0.281] |
| -1 | 34267 | 10331 | 0.259 | [0.236; 0.281] | 0.190 | [0.140; 0.239] |
| +0 | 34268 | 10339 | 0.232 | [0.209; 0.255] | 0.197 | [0.140; 0.255] |
| +1 | 34269 | 10321 | 0.202 | [0.177; 0.225] | 0.163 | [0.116; 0.211] |
| +2 | 34270 | 10312 | 0.179 | [0.154; 0.202] | 0.136 | [0.098; 0.178] |
| +3 | 34271 | 10293 | 0.165 | [0.140; 0.187] | 0.141 | [0.095; 0.184] |
| +4 | 34272 | 10293 | 0.150 | [0.126; 0.171] | 0.117 | [0.082; 0.150] |
| +5 | 34273 | 10293 | 0.131 | [0.107; 0.152] | 0.100 | [0.070; 0.128] |
| +6 | 34274 | 10285 | 0.124 | [0.099; 0.145] | 0.099 | [0.071; 0.127] |

**39167000 VITÓRIA DE SANTO ANTÃO MONTANTE** — 2023-09-04 a 2026-10-10, 95841 leituras de 15 min; 47.6 km do Derby, 45.8 km de Mascarenhas/Imbiribeira; argmax = -4 h, segundo = -3 h, margem = 0.009, vencedor > segundo em 95.8 % das réplicas; decisão: `WEAK_CORRELATION`.

| h (rótulo = UTC + h) | horas comuns | horas com chuva (OM ≥ 0,1 mm) | Spearman | IC 95 % | Pearson | IC 95 % |
|---:|---:|---:|---:|---|---:|---|
| -6 | 23940 | 7132 | 0.234 | [0.207; 0.261] | 0.164 | [0.092; 0.242] |
| -5 | 23941 | 7138 | 0.241 | [0.215; 0.268] | 0.179 | [0.114; 0.251] |
| -4 **←** | 23942 | 7149 | 0.252 | [0.225; 0.278] | 0.212 | [0.135; 0.297] |
| -3 | 23943 | 7154 | 0.243 | [0.216; 0.268] | 0.198 | [0.116; 0.292] |
| -2 | 23944 | 7151 | 0.236 | [0.209; 0.262] | 0.217 | [0.143; 0.301] |
| -1 | 23945 | 7159 | 0.214 | [0.187; 0.240] | 0.178 | [0.116; 0.242] |
| +0 | 23946 | 7149 | 0.193 | [0.167; 0.219] | 0.148 | [0.083; 0.221] |
| +1 | 23947 | 7135 | 0.167 | [0.141; 0.192] | 0.132 | [0.069; 0.209] |
| +2 | 23948 | 7116 | 0.137 | [0.112; 0.164] | 0.114 | [0.065; 0.177] |
| +3 | 23949 | 7102 | 0.120 | [0.094; 0.146] | 0.104 | [0.051; 0.179] |
| +4 | 23950 | 7088 | 0.106 | [0.080; 0.133] | 0.086 | [0.053; 0.129] |
| +5 | 23950 | 7078 | 0.109 | [0.082; 0.135] | 0.065 | [0.042; 0.092] |
| +6 | 23951 | 7071 | 0.107 | [0.081; 0.134] | 0.052 | [0.034; 0.073] |

**39187800 SÃO LOURENÇO DA MATA II** — 2021-10-01 a 2026-09-30, 158142 leituras de 15 min; 17.0 km do Derby, 19.9 km de Mascarenhas/Imbiribeira; argmax = -4 h, segundo = -3 h, margem = 0.006, vencedor > segundo em 95.5 % das réplicas; decisão: `WEAK_CORRELATION`.

| h (rótulo = UTC + h) | horas comuns | horas com chuva (OM ≥ 0,1 mm) | Spearman | IC 95 % | Pearson | IC 95 % |
|---:|---:|---:|---:|---|---:|---|
| -6 | 39261 | 14872 | 0.274 | [0.251; 0.295] | 0.163 | [0.112; 0.223] |
| -5 | 39261 | 14898 | 0.280 | [0.256; 0.301] | 0.185 | [0.126; 0.252] |
| -4 **←** | 39261 | 14929 | 0.293 | [0.269; 0.313] | 0.206 | [0.140; 0.283] |
| -3 | 39261 | 14961 | 0.287 | [0.264; 0.308] | 0.215 | [0.145; 0.299] |
| -2 | 39261 | 14981 | 0.274 | [0.252; 0.295] | 0.176 | [0.121; 0.248] |
| -1 | 39261 | 14986 | 0.259 | [0.237; 0.279] | 0.179 | [0.123; 0.240] |
| +0 | 39261 | 14993 | 0.238 | [0.216; 0.258] | 0.159 | [0.107; 0.218] |
| +1 | 39261 | 14988 | 0.220 | [0.198; 0.241] | 0.154 | [0.100; 0.217] |
| +2 | 39261 | 14975 | 0.206 | [0.184; 0.227] | 0.141 | [0.088; 0.208] |
| +3 | 39261 | 14955 | 0.190 | [0.167; 0.210] | 0.116 | [0.069; 0.173] |
| +4 | 39261 | 14941 | 0.173 | [0.151; 0.193] | 0.105 | [0.069; 0.148] |
| +5 | 39261 | 14929 | 0.164 | [0.142; 0.184] | 0.105 | [0.074; 0.135] |
| +6 | 39261 | 14909 | 0.155 | [0.132; 0.175] | 0.089 | [0.066; 0.114] |

**39370100 ENGENHO MARANHÃO** — 2021-10-01 a 2026-10-10, 157410 leituras de 15 min; 44.6 km do Derby, 38.3 km de Mascarenhas/Imbiribeira; argmax = -4 h, segundo = -3 h, margem = 0.005, vencedor > segundo em 91.7 % das réplicas; decisão: `IMPLAUSIBLE_OFFSET`.

| h (rótulo = UTC + h) | horas comuns | horas com chuva (OM ≥ 0,1 mm) | Spearman | IC 95 % | Pearson | IC 95 % |
|---:|---:|---:|---:|---|---:|---|
| -6 | 39324 | 16554 | 0.311 | [0.292; 0.329] | 0.213 | [0.169; 0.255] |
| -5 | 39325 | 16599 | 0.322 | [0.301; 0.340] | 0.215 | [0.175; 0.254] |
| -4 **←** | 39326 | 16626 | 0.327 | [0.307; 0.345] | 0.222 | [0.178; 0.267] |
| -3 | 39327 | 16657 | 0.322 | [0.303; 0.341] | 0.226 | [0.177; 0.275] |
| -2 | 39328 | 16690 | 0.316 | [0.297; 0.335] | 0.218 | [0.172; 0.263] |
| -1 | 39329 | 16694 | 0.301 | [0.281; 0.319] | 0.216 | [0.171; 0.261] |
| +0 | 39330 | 16683 | 0.280 | [0.260; 0.299] | 0.205 | [0.158; 0.251] |
| +1 | 39331 | 16662 | 0.249 | [0.228; 0.268] | 0.187 | [0.144; 0.227] |
| +2 | 39332 | 16627 | 0.226 | [0.205; 0.244] | 0.161 | [0.126; 0.197] |
| +3 | 39333 | 16595 | 0.206 | [0.184; 0.225] | 0.164 | [0.127; 0.200] |
| +4 | 39334 | 16571 | 0.186 | [0.164; 0.205] | 0.146 | [0.110; 0.180] |
| +5 | 39335 | 16528 | 0.174 | [0.153; 0.194] | 0.134 | [0.098; 0.169] |
| +6 | 39336 | 16504 | 0.168 | [0.146; 0.188] | 0.133 | [0.099; 0.166] |

### 3.2 Teste B — fontes só ao vivo

**CEMADEN** — 328 leituras LIVE de 47 estações; decisão: `ACCEPT` (única hipótese aprovada: -3 h).

| h | p01 do atraso (min) | mediana do atraso (min) | viável |
|---:|---:|---:|:---:|
| -6 | -178.5 | -176.2 | não |
| -5 | -118.5 | -116.2 | não |
| -4 | -58.5 | -56.2 | não |
| -3 | 1.5 | 3.8 | sim |
| -2 | 61.5 | 63.8 | não |
| -1 | 121.5 | 123.8 | não |
| +0 | 181.5 | 183.8 | não |
| +1 | 241.5 | 243.8 | não |
| +2 | 301.5 | 303.8 | não |
| +3 | 361.5 | 363.8 | não |
| +4 | 421.5 | 423.8 | não |
| +5 | 481.5 | 483.8 | não |
| +6 | 541.5 | 543.8 | não |

**APAC_METEO24H** — 11 leituras LIVE de 2 estações; decisão: `INSUFFICIENT_DATA` (amostras=11 (min 200), estações=2 (min 20)).

| h | p01 do atraso (min) | mediana do atraso (min) | viável |
|---:|---:|---:|:---:|
| -6 | -177.0 | -168.5 | não |
| -5 | -117.0 | -108.5 | não |
| -4 | -57.0 | -48.5 | não |
| -3 | 3.0 | 11.5 | sim |
| -2 | 63.0 | 71.5 | não |
| -1 | 123.0 | 131.5 | não |
| +0 | 183.0 | 191.5 | não |
| +1 | 243.0 | 251.5 | não |
| +2 | 303.0 | 311.5 | não |
| +3 | 363.0 | 371.5 | não |
| +4 | 423.0 | 431.5 | não |
| +5 | 483.0 | 491.5 | não |
| +6 | 543.0 | 551.5 | não |

#### Teste A exploratório (últimas horas, Open-Meteo forecast com past_days = 3), sem peso na decisão

| fonte | estação | leituras | horas com chuva (OM) | argmax | decisão |
|---|---|---:|---:|---:|---|
| CEMADEN | 260960003A [CEMADEN] Bonsucesso | 10 | 2 | -5 | `INSUFFICIENT_DATA` |
| CEMADEN | 260830501A [APAC] | 47 | 0 | +6 | `INSUFFICIENT_DATA` |
| CEMADEN | 260345401A [CEMADEN] Timbi | 27 | 1 | -6 | `INSUFFICIENT_DATA` |
| CEMADEN | 260410603A [CEMADEN] Segundo Distrito | 11 | 0 | -6 | `INSUFFICIENT_DATA` |
| CEMADEN | 260720802A [CEMADEN] Camela | 25 | 3 | -4 | `INSUFFICIENT_DATA` |
| CEMADEN | 260790103A [CEMADEN] Cavaleiro | 25 | 4 | -2 | `INSUFFICIENT_DATA` |

- Mais próximas de Derby (AGAMENON_DERBY) no CEMADEN: 261160618A [CEMADEN] Torreão (2.1 km, 2 leituras LIVE); 261160623A [APAC] Sede (2.3 km, 8 leituras LIVE); 261160620A [CEMADEN] Porto (2.4 km, 8 leituras LIVE)
- Mais próximas de Mascarenhas/Imbiribeira (MASCARENHAS_IMBIRIBEIRA) no CEMADEN: 261160609A [CEMADEN] Imbiribeira (1.0 km, 4 leituras LIVE); 261160617A [CEMADEN] Pina (2.9 km, 7 leituras LIVE); 261160621A [CEMADEN] Areias (3.2 km, 7 leituras LIVE)

Conferências feitas na mesma consulta: as estações com prefixo `[APAC]` e `[CEMADEN]` do mesmo endpoint têm a mesma mediana de atraso (≈ 3,5 min com h = −3), então o resultado vale para o endpoint inteiro. Uma amostra única do Cemaden direto (`getJson2.php`, 2026-10-10 ≈ 23:46 UTC) trouxe `datahoraUltimovalor` 23:10, ou seja, **UTC**: o espelho da APAC (UTC−3) e o Cemaden direto (UTC) têm fusos diferentes, e a regra `America/Recife` do registro vale **só** para o espelho da APAC (`source = CEMADEN`). Isso é uma observação, não parte do critério.

### 3.3 Teste C — relógio da ANA (pós-hoc, seção 1.4)

De 149 estações telemétricas ativas de PE no inventário, só **7** tinham leitura entre D−1 e D+1 (a maioria está sem transmitir). Abaixo do poder mínimo (50): `INSUFFICIENT_DATA`. Sem valor de decisão, mas coerente com −3 h:

| h | estações com leitura do futuro (lag < −10 min) | atraso da estação mais fresca (min) |
|---:|---:|---:|
| −5 | 7 de 7 | −98,4 |
| −4 | 3 de 7 | −38,4 |
| **−3** | **0 de 7** | **21,6** |
| −2 | 0 de 7 | 81,6 |
| 0 | 0 de 7 | 201,6 |

## 4. Decisão por fonte

| Fonte | Critério | Resultado | Estado no `source_registry` |
|---|---|---|---|
| CEMADEN (espelho APAC) | teste B | `ACCEPT`, h = −3 (328 leituras, 47 estações; atraso mediano 3,8 min; as outras 12 hipóteses reprovam) | **`America/Recife`, `VERIFIED`** |
| ANA_SOAP | teste A | `INSUFFICIENT_DATA` (0 de 4 estações aprovadas) | `UNVERIFIED` (mantida), nota com o motivo |
| APAC_METEO24H | teste B | `INSUFFICIENT_DATA` (11 leituras LIVE de 2 estações; mínimo 200 e 20). As poucas leituras são coerentes com −3 h, sem valor de decisão | `UNVERIFIED` (mantida), nota com o motivo |

## 5. Aplicação

- Migração `V13__tz_verification_cemaden.sql`: `UPDATE` de configuração em `source_registry` (CEMADEN → `America/Recife` / `VERIFIED`; notas de ANA_SOAP e APAC_METEO24H com o motivo). Nenhuma tabela criada ou alterada. `ruleVersion` passou de `2026.10.1` a `2026.10.2`. O número V13 pode colidir com migrações de outras branches no merge; renumerar se necessário.
- `CemadenAdapter.declaredTz()` passou a declarar `America/Recife` (bruto novo já nasce com o fuso); o bruto antigo mantém `UNVERIFIED`, o que é esperado e auditável.
- **Saída da quarentena por REVALIDATE** (`POST /api/admin/ingestion/runs`, aguardando o estado final): run `4ec21f85-4d04-46db-a50c-0e74fb31bd31`, janela `[2026-10-08, 2026-10-12)`, `OK`: `fetched = 8099`, `valid = 8099`, `quarantined = 0`, `obs_inserted = 8099`. Exemplo de auditoria: rótulo `2026-10-09 21:50:30` → `ts_utc = 2026-10-10 00:50:30+00`. Repetição (run `9ae854a4-babc-491b-9fad-0cc9aa640127`): `obs_inserted = obs_updated = 0`, idempotente. Quarentena aberta do CEMADEN: 0.
- ANA_SOAP e APAC_METEO24H: **mantidas em quarentena** (565.612 e 117+48 registros), motivo na nota do registro.
- Nenhum `UPDATE` ou `DELETE` em `observation` ou `raw_observation` foi feito à mão.

## 6. Limitações e pendências para o autor

1. **ANA: decisão do autor.** O critério pré-fixado reprova; a evidência pós-hoc (UTC rejeitado em 4 de 4 estações; teste C coerente com −3 h, n = 7) aponta −3 h. Se o autor aceitar essa evidência, a mudança é uma migração de uma linha (`ANA_SOAP` → `America/Recife`, `VERIFIED`) seguida de REVALIDATE da janela inteira (2021-10 a 2026-10, ~556 mil registros); **não foi aplicada**.
2. **ANA ao vivo (Hidro-WebService, `Data_Hora_Medicao`) não foi testada**: sem credencial. O registro é por fonte (`ANA_SOAP`), então uma verificação do legado não se estende por si só ao Hidro-WebService.
3. **ANA, unidade do nível:** o legado devolve `Nivel` em centímetros (ex.: 239,00), mas o catálogo de `water_level` é em metros com máximo 50; quando a ANA sair da quarentena, o nível será reprovado por faixa. Assunto da unidade, não do fuso (qualificador `UNIT_UNVERIFIED` já existe).
4. **CEMADEN e APAC meteorologia24h** só têm janela de 24 h: o teste B prova o fuso **na janela observada** (outubro/2026), não que nunca mudou. Um teste A com poder exigiria histórico autenticado do Cemaden (conta) ou acumular chuva ao vivo por meses.
5. **APAC_METEO24H** poderá ser reavaliada quando o endpoint publicar mais de 2 estações (hoje só a fluviométrica de Jaboatão); basta repetir o runner e, se aprovar, nova migração.
6. A chuva da fonte ao vivo (`chuva_acumulada`) tem janela desconhecida (`WINDOW_UNVERIFIED`); a verificação de fuso não resolve isso.

## 7. Como reproduzir

1. Subir o banco e a API: `docker compose -p projeto-mapi-api -f <worktree>/docker-compose.yml --env-file /home/lucas/Documents/Projetos/MAPI/.env.revisao up -d --build --no-deps postgres mapi-api` (volume `projeto-mapi-api_postgres_ha_data`).
2. Cargas (ADMIN; cada run assíncrono é aguardado até sair de `RUNNING`): `{"source":"ANA_SOAP","from":"2021-10-01T00:00:00Z","to":"2026-10-11T00:00:00Z","mode":"REFETCH"}` em blocos de 6 meses; `{"source":"CEMADEN",...,"mode":"REFETCH"}` e `{"source":"APAC_METEO24H",...}` (instantâneo de 24 h).
3. Verificação (não grava no banco): `java -jar target/projeto-mapi-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=tzverify --spring.main.web-application-type=none --mapi.tz-verify.output=saida.json` com `POSTGRES_URL=jdbc:postgresql://localhost:5433/tide_db` e `ANA_COLLECTOR_ENABLED=false APAC_COLLECTOR_ENABLED=false`. Semente 20261010, 1000 réplicas.
4. Testes: `TzCorrelationTest` (séries sintéticas: recupera −3 h e 0 h; aceita ambos; rejeita +2 h e ruído independente; poder insuficiente), `WallClockCheckTest`, `AnaSoapAdapterTest`, `LiveSensorStagingIntegrationTest` (fluxo quarentena → REVALIDATE).

Usuário ADMIN criado no banco local para os próximos agentes: `revisao_admin` (senha em `/home/lucas/Documents/Projetos/MAPI/.env.revisao`, fora do git, permissão 600).

