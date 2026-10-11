# Primeira carga histórica pelo pipeline novo — 2026-10-10

Tarefa F2-BACK-09. Código: branch `integracao/fase-2` (HEAD `70c1cbc`), imagem `projeto-mapi-api-mapi-api` (id `e1c242e8…`) construída da worktree. Banco: `mapi-db`, imagem `timescale/timescaledb-ha:pg16.15-ts2.30.2` (TimescaleDB 2.30.2), volume `projeto-mapi-api_postgres_ha_data`, projeto compose `projeto-mapi-api`. Nada foi apagado: sem `down -v`, sem `wipe-database`, sem drop; as tabelas legadas ficaram intactas.

## Resumo para decisão

1. **Migrações V3–V10 aplicadas com sucesso** (10 linhas `success = t` em `flyway_schema_history`) e API `healthy`.
2. **Carga de clima, solo e terreno sem quarentena e sem lacuna**; as duas fontes de clima passaram por `REFETCH` com 0 falhas de validação.
3. **Não há rótulo `HOUR` no CKAN**: todas as 197 ocorrências de alagamento distintas (591 linhas, ver duplicação abaixo) têm `Data` à meia-noite, ou seja, precisão `DAY`. **N_HOUR = 0 em qualquer período**, então a regra N_min = 20 **não é atingível** com esta fonte e, pela A.1.4, a tarefa principal passa a ser **(b)** (rótulo diário).
4. **Poucos positivos casam com os pontos a 500 m**: 3 eventos-dia no total (1 em AV_RECIFE_IBURA, 2 em AGAMENON_DERBY); CIN_UFPE, JABOATAO_CENTRO e MASCARENHAS_IMBIRIBEIRA têm 0.
5. **Cobertura do CKAN com buracos**: 2022 falhou por bug (código), 2025 tem 1 registro por recurso, 2026 não tem recurso. Ver "Problemas".
6. **Export de 5 anos de 1 ponto: p95 = 40,9 s, mediana 11,2 s** (gatilho do ADR 0006 §9 é > 5 s): **gatilho atingido**.

## Passos

| # | Passo | Resultado |
|---|---|---|
| 1 | Disco antes: `/home` 3,6 GB livres (o Docker usa `/`, 6,8 GB livres, 8,2 GB ao fim). Só o `postgres` de pé. `pg_dump -Fc` → `MAPI/snapshots/tide_db-2026-10-10-pre-V3-V10.dump` | 423 722 B, SHA-256 `35dccf4599308eadd970c662969df76c8089cf8ba89984639af7ad9f998ec4c8` (265 entradas no `pg_restore -l`; `.sha256` ao lado). Flyway no banco: V1, V2 |
| 2 | `docker compose -p projeto-mapi-api -f <wt>/docker-compose.yml up -d --build --no-deps mapi-api` | V3 ingestion registry, V4 raw observation and quarantine, V5 observation timeseries, V6 labels and linkage, V7 postgis extension, V8 flood predictions audit columns, V9 terrain, V10 era5land soil moisture: todas `success`. Volume do banco: o mesmo `postgres_ha_data` (ver incidente 1). Healthy em ~15 s |
| 3a | Admin local: `ADMIN_BOOTSTRAP_USERNAME=mapi_admin_local` e senha aleatória gerada na hora, passadas só por variável de ambiente do `docker compose up` (mecanismo `AdminBootstrapSeeder`; nada commitado, nenhuma credencial de outro serviço). Terreno: um arquivo compose de apoio **fora do repositório** montou `MAPI/terreno` em `/terreno` (somente leitura) e definiu `MAPI_TERRAIN_DIR=/terreno`, porque o compose do repositório não monta o diretório | Admin criado (log do seeder); login 200 |
| 3b | Terreno `POST /api/admin/terrain/runs` | SHA-256 do `ponto_terreno.csv` = `114ddd32679176010c45b514842b00b8a755671e4f6edbba8be2b78483fbb405` (confere). 201, `runId` 1, 4 pontos OK, 1 sem dado (JABOATAO_CENTRO), 164 linhas de curva; `inputSha256` `00d8dfb5…`, `vrt_sha256` `d63e3b12…`; 0,08 s |
| 3c | Open-Meteo archive, `POST /api/admin/ingestion/runs` `REFETCH`, blocos anuais de 2021-10-01 a 2026-10-01 | ver tabela de cargas |
| 3d | ERA5-Land idem | ver tabela de cargas |
| 3e | CKAN: `POST /api/admin/ingestion/historical-civil-defense?years=6` (6 para incluir o recurso de 2021; o pipeline roda raio 500 m via `rematch`) | ver tabela de cargas |
| 5 | Export e medição | ver seções abaixo |
| 6 | `docker compose -p projeto-mapi-api -f <wt>/docker-compose.yml stop` | contêineres parados; volumes preservados |

## Cargas: tempo e volume

Período dos blocos: 2021-10-01 a 2026-10-01 (5 anos, 5 blocos de um ano). Antes, um bloco de teste de 2021-01 (um mês) foi carregado no Open-Meteo, por isso a série do Open-Meteo archive começa em 2021-01-01 (3 720 linhas brutas a mais, idempotentes). O export usa o período explícito 2021-10-01 a 2026-10-01.

| Carga | Linhas brutas (`fetched`) | Linhas em `observation` | Tempo | Vazão | Quarentena | Lacunas |
|---|---|---|---|---|---|---|
| Open-Meteo archive, 5 pontos × 5 blocos | 222 840 (43 800 por ano) | 1 559 880 (7 variáveis) | ~21–24 s por bloco, ~110 s no total | ~2 000 linhas brutas/s (~120 mil/min); 5 requisições por bloco, sem erro de limite | 0 | 0 |
| ERA5-Land (`soil_moisture_0_7cm`) | 219 120 | 219 120 | ~15–18 s por bloco; 2 blocos falharam e passaram na segunda tentativa | ~1 500 linhas brutas/s | 0 | 0 |
| CKAN (9 recursos de atendimentos + 8 de tempo real; 3 recursos 2022 falharam) | 684 925 linhas brutas | 591 linhas em `flood_occurrence` | 954 s (18:43:04 a 18:58:58 UTC) | ~43 mil linhas brutas/min; o tempo é dominado pela geocodificação (Nominatim) e pelo legado `FloodEvent`, que roda no mesmo job | 0 | n/a |
| Terreno | 5 pontos (164 linhas de curva) | `point_terrain` | 0,08 s | n/a | n/a | n/a |

A taxa-limite do Open-Meteo gratuito não foi atingida (≈30 requisições no total). As duas falhas do ERA5-Land (blocos 2024-10→2025-10 e 2025-10→2026-10) foram `fonte indisponível: ERA5-Land: JSON inválido`, transitórias; a repetição idêntica passou (`rawInserted` 43 800 cada). O `ingestion_run` das falhas fica no banco (status `FAILED`, `fetched = 0`), sem dado parcial.

### Estado do banco ao fim

| | Valor |
|---|---|
| `raw_observation` por fonte | OPEN_METEO_ARCHIVE 222 840 · OPEN_METEO_ERA5LAND 219 120 · CKAN_RECIFE 684 925 · CEMADEN 56 · APAC_METEO24H 2 (os dois últimos vieram do coletor vivo da API) |
| `observation` | 1 779 000 linhas, 5 pontos (`station_id`) |
| `ingestion_quarantine` | 58 linhas, todas `TZ_UNVERIFIED` (regra `TZ`, vindas dos coletores vivos Cemaden/APAC, ainda sem fuso verificado). Das cargas históricas: 0 |
| `ingestion_run` | 50 `OK`, 5 `FAILED` (2 ERA5 repetidos com sucesso; 3 CKAN 2022, ver problemas) |
| Tamanho (`hypertable_size`) | `raw_observation` 1 486 MB (1 chunk), `observation` 878 MB (63 chunks), `sensor_data` 1,2 MB, `weather_data` 0,4 MB, `flood_predictions` 7 MB; banco inteiro 2 399 MB |
| Tabelas legadas | `day_data`, `hour_data`, `month_data`, `flood_points`, `flood_predictions`, `geo_locations`, `tide_tables`, `flood_point_*`: contagens idênticas às do dump. Mudaram só por atividade normal da API: `sensor_data` 617→674, `weather_data` 50→60, `refresh_token` 10→11, `users` 10→11 (admin local), `flood_events` 0→58 (ingestão legada do CKAN) |

Observação de volume: 1,5 GB de `raw_observation` vêm quase todos do CKAN (685 mil linhas com o `payload` completo, 3× duplicadas, ver problemas). Compressão fica adiada (ADR 0006 §9).

## Rótulos (CKAN, filtro textual "alagamento", raio 500 m)

Linhas em `flood_occurrence` = **591** (todas geocodificadas, todas `DAY`, 0 `HOUR`). Cada ano tem **3 recursos duplicados** no CKAN com o mesmo conteúdo (ids diferentes; `_id` coincidem), então a contagem real é 591 / 3 = **197 ocorrências distintas** (a deduplicação por data + endereço dá 227 porque os recursos duplicados têm variações de codificação no endereço). Nenhuma deduplicação foi feita no banco.

Ocorrências de alagamento por ano de `Data` (America/Recife) e precisão:

| Ano | Recursos | Registros brutos por recurso | Linhas em `flood_occurrence` | Distintas (÷3) | HOUR | DAY |
|---|---|---|---|---|---|---|
| 2021 | 3 | 64 855 | 3 | 1 | 0 | 1 |
| 2022 | 3 | 90 425 | 0 (**carga falhou, bug**) | desconhecido | n/d | n/d |
| 2023 | 3 | 83 164 | 225 | 75 | 0 | 75 |
| 2024 | 3 | 80 173 | 363 | 121 | 0 | 121 |
| 2025 | 3 | 1 | 0 | 0 | 0 | 0 |
| 2026 | nenhum | n/a | n/a | n/a | n/a | n/a |

Casadas a 500 m (`linkage_config` 3, distância 300–354 m) por ponto, ano e precisão:

| Ponto | Ano | Precisão | Linhas | Eventos-dia distintos |
|---|---|---|---|---|
| AV_RECIFE_IBURA | 2024 | DAY | 3 | 1 (2024-02-16) |
| CIN_UFPE | — | — | 0 | 0 |
| AGAMENON_DERBY | 2024 | DAY | 6 | 2 (2024-06-15 e 2024-06-25) |
| JABOATAO_CENTRO | — | — | 0 | 0 |
| MASCARENHAS_IMBIRIBEIRA | — | — | 0 | 0 |

Total casado: 9 linhas = 3 eventos-dia distintos de 197 ocorrências distintas (1,5%). O `linkage_config` de 3 km (id 2) não tem casamentos gravados; só o de 500 m (id 3) foi criado pelo pipeline.

### Conta de MODELAGEM-IA A.3.7 e a regra N_min = 20

Taxa de instâncias positivas por ponto = `N·H/43.800` (43.800 h = 5 anos). Com os dados reais, não há `N_HOUR`: N = 0 para H = 3 e H = 6, taxa 0. Eventos `DAY` entram como exclusão (`DAY_EVENT_SAME_DAY`, 60 horas em h3 e 72 em h6 para AGAMENON_DERBY, 0 nos demais) e em `label_day` (48 horas positivas em AGAMENON_DERBY = 2 dias × 24 h).

- `N_HOUR,teste = 0 < N_min = 20`: **a regra não é atingível**, independentemente do corte de datas (não se escolheu corte), porque o CKAN de Recife não traz hora (`Data` sempre à meia-noite).
- Mesmo a alternativa (b), que usa `N_HOUR + N_DAY`, teria apenas 3 eventos-dia casados a 500 m em 5 pontos, em 2024; o teste da A.1.4 com N_min = 20 também não se cumpre. Se o raio fosse maior (3 km, ver A.3.1), os números mudariam; isso não foi medido aqui.
- Série anual de ocorrências distintas (todas DAY): 2021: 1 · 2022: não carregado · 2023: 75 · 2024: 121 · 2025: 0 · 2026: sem recurso.
- Cobertura temporal real do rótulo: dado útil só de 2023 e 2024 (e 1 registro em 2021). O restante do período de 5 anos fica sem rótulo informativo, e as horas viram negativos (0) no export, o que pode ser um viés de rótulo ausente (já anotado como pendência 2 do F2-BACK-13).

## Export de exemplo (`MAPI/exports/`, fora do git)

Chamada: `GET /api/export/ia-dataset/{slug}/csv?to=2026-10-01T00:00:00Z&days=1826` (e `/manifest` com os mesmos parâmetros). `datasetId` = SHA-256 do CSV no manifesto (valor do manifesto, não conferido contra o arquivo).

| Arquivo | SHA-256 | Linhas |
|---|---|---|
| `AGAMENON_DERBY.csv` | `0c0ab1d883a2eac6922f66fbf2328b77f92e33fc23df2ffc9d0b78c868090e19` | 43 825 |
| `AGAMENON_DERBY.manifest.json` | `ec80e3d8b2b2e97a59bef6e0eacd4afd8f8d47b3f2b12a2d364ef8eeee1ad922` | |
| `MASCARENHAS_IMBIRIBEIRA.csv` | `94356c8b3a3414decd6b824dd532b5fefbea1c7feb77a472656b620805cf8334` | 43 825 |
| `MASCARENHAS_IMBIRIBEIRA.manifest.json` | `80c1ca49105fef823e6c00ab111d4731c3a417b065693f607d8f02d5edb883e0` | |

Manifesto: `terrain.runId` 1, `linkageConfigId` 3, `linkageRadiusM` 500, `ruleVersion` 2026.10.1, `codeVersion` "unknown". Contagens de rótulo em AGAMENON_DERBY: `label_h6` 0 positivos / 43 753 negativos / 72 excluídos; `label_h3` 0 / 43 765 / 60; `label_day` 48 positivos; `occurrencesHour` 0, `occurrencesDay` 6 (3× 2 eventos). MASCARENHAS_IMBIRIBEIRA: tudo zero positivos, 0 excluídos. Em ambos, 1 hora sem clima (`weatherPrecipitation` ausente: 1) e `sensor*` quase todo vazio (sensores ainda do legado), `tideHeight` ausente em 37 272 das 43 825 horas.

### p95 do export de 5 anos de 1 ponto (AGAMENON_DERBY, 10 chamadas sequenciais, CSV, HTTP 200)

Tempos (s): 11,09 · 11,14 · 11,15 · 11,16 · 11,16 · 11,27 · 36,91 · 36,98 · 39,17 · 40,88 (ordenados). **Mediana 11,2 s; p95 (método do posto mais próximo, n = 10) 40,9 s.** A distribuição é bimodal (6 × ~11 s e 4 × ~37–41 s), causa não investigada. O manifesto sozinho leva ~11 s. Ambiente: API em contêiner e banco no mesmo host local. **> 5 s: o gatilho do ADR 0006 §9 foi atingido.**

### 5 linhas de exemplo (AGAMENON_DERBY; terreno `terrain_run_id` = 1)

| `timestampUtc` | chuva (mm) | acum. 6 h | solo (m³/m³) | `label_h6` | `label_h3` | `label_day` | motivo de exclusão | `occurrence_ids` | cota rua (m) | declividade (%) | depressão (m) | área contrib. (m²) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2021-10-01T00:00Z | 0,1 | (vazio, janela incompleta) | 0,335 | 0 | 0 | 0 | | | 2,58 | 4,404 | 0,0 | 62,0 |
| 2024-01-12T08:00Z | 0,0 | 0,0 | 0,221 | 0 | 0 | 0 | | | 2,58 | 4,404 | 0,0 | 62,0 |
| 2024-06-14T21:00Z | 1,3 | 3,6 | 0,467 | (vazio) | 0 | 0 | DAY_EVENT_SAME_DAY | 77;198;319 | 2,58 | 4,404 | 0,0 | 62,0 |
| 2024-06-15T03:00Z | 1,4 | | 0,504 | (vazio) | (vazio) | 1 | DAY_EVENT_SAME_DAY | 77;198;319 | 2,58 | 4,404 | 0,0 | 62,0 |
| 2024-06-16T03:00Z | 0,5 | 4,4 | 0,491 | (vazio) | (vazio) | 0 | POST_EVENT_WINDOW | 77;198;319 | 2,58 | 4,404 | 0,0 | 62,0 |

Os `occurrence_ids` repetem 3 ids por causa dos recursos duplicados (77, 198, 319 são a mesma ocorrência).

## Problemas encontrados (nada foi corrigido no banco nem no código)

1. **Compose do repositório principal desatualizado nesta branch (`revisao/fase-0`)**: `docker compose -f <repo principal>/docker-compose.yml up -d postgres` usa a imagem antiga `timescale/timescaledb:latest-pg16` e o volume `projeto-mapi-api_postgres_data`. A primeira chamada do passo 1 recriou o contêiner `mapi-db` com esse compose, **criando um volume novo e vazio `projeto-mapi-api_postgres_data`** (o volume antigo tinha sido removido em 2026-10-10) e subindo um Postgres vazio por segundos. Nenhum dado foi tocado. O contêiner foi parado e recriado com o compose da worktree (imagem `-ha`, volume `postgres_ha_data`); o volume vazio ficou lá (não removido, por regra de não apagar volume) e precisa de decisão do autor. O snapshot e todas as cargas usaram o compose da worktree.
2. **CKAN 2022 falhou nos três recursos duplicados** (`FAILED`, `fetched = 90 425`, sem dado gravado): `ERROR: value too long for type character varying(40)` no `INSERT` de `raw_observation`. O registro `_id` 27476 de cada recurso tem texto livre no campo `Data` ("Desabamento Parcial de Obras de Conteno / Drenagem"); `TimestampNormalizer.normalize` o devolve sem cortar e a coluna `source_ts_norm` tem 40 caracteres. Em vez de ir à quarentena `TS_UNPARSEABLE` (como o F2-BACK-12 prevê), derruba o lote inteiro. **Exige mudança de código** (limitar/validar `source_ts_norm` antes do insert). Efeito: o ano de 2022 está **sem rótulo**. Reexecutar depois da correção.
3. **Recursos duplicados no CKAN**: cada ano de "Atendimentos" tem 3 recursos com o mesmo conteúdo; a chave natural é recurso + `_id`, então cada ocorrência entra 3 vezes em `flood_occurrence`, em `occurrence_point_match` e no manifesto (`occurrencesDay` 6 em vez de 2). Triplica também o espaço em `raw_observation`. Já era a pendência 3 do F2-BACK-12; agora medida.
4. **Recursos de 2025 com 1 registro** (06/12/2024, sem ocorrência) e **sem recurso de 2026**: o CKAN usado não cobre 2025 e 2026. A janela de 5 anos tem rótulo útil só em 2023 e 2024 (+1 registro em 2021).
5. **Todo o CKAN é `DAY`**: a fonte não tem hora. Efeito sobre o desenho está na seção de rótulos.
6. **Export lento e bimodal** (ver p95), 11 s só para o manifesto.
7. **ERA5-Land**: 2 respostas "JSON inválido" transitórias do Open-Meteo; repetir resolveu. Sem retry automático no adaptador.
8. `codeVersion` aparece como "unknown" nos runs e no manifesto (a imagem não recebe a versão do código). Para auditoria, convém injetar o hash do commit no build.
9. `mapi-ai` não foi construído nem iniciado nesta tarefa (o compose da worktree aponta o contexto `../projeto-mapi-ai`, que não existe ao lado da worktree); a API subiu com `--no-deps`. Predições da IA não foram exercitadas.
10. Sem arquivo de ambiente: o admin local e a senha aleatória ficaram só no diretório temporário da sessão; para reusar o admin, é preciso recriar o contêiner com `ADMIN_BOOTSTRAP_*` definidos.
