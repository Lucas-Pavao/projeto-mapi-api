# API do MAPI — referência

- Tarefa: F2-BACK-03 · Data: 2026-10-10
- Fonte única dos números: `docs/openapi/openapi-integracao-fase2.json`, gerado de `/v3/api-docs` do código `integracao/fase-2` (commit e27755b, migrations V1–V13). Comparado com `docs/openapi/openapi-2026-10-10.json` (snapshot da Fase 1).
- Os contratos de predição, proveniência e terreno vêm dos ADRs 0006, 0007, 0008 e 0009. Onde o OpenAPI não descreve algo, a seção "Pendências de documentação no código" lista o que falta.

## 1. Visão geral

- Base local: `http://localhost:8080` (porta publicada em `docker-compose.yml`). Prefixo das rotas: `/api`. Swagger UI em `/swagger-ui/index.html`; JSON em `/v3/api-docs`.
- Versão declarada no OpenAPI: `0.0.1` (`info.version`). Não há versionamento de rota.
- Autenticação: JWT. O filtro (`JwtAuthenticationFilter`) lê o token do cabeçalho `Authorization: Bearer <accessToken>` e, se ele não existir, do cookie `accessToken`. O OpenAPI declara só o esquema global `bearerAuth` (HTTP bearer, JWT); não marca rota a rota.
- Sessão sem estado (`STATELESS`), CSRF desligado. `POST /api/auth/login` devolve `{accessToken, refreshToken}`; `POST /api/auth/refresh` recebe `TokenRefreshRequest`; `GET /api/auth/me` devolve `{username, role}`.
- Perfis de usuário (`Role`): `USER` e `ADMIN`.
  - `ADMIN` é exigido em `/api/admin/ingestion/**`, `/api/admin/terrain/**` e `/actuator/**` (exceto `health`, `info` e `prometheus`, públicos).
  - O perfil de execução `dev` (Spring profile, não é papel de usuário) ativa `DELETE /api/admin/ingestion/wipe-database` (`DevAdminController`, `@Profile("dev")`). Fora de `dev` a rota não existe (404) e some do OpenAPI. Por isso ela aparece no snapshot antigo e não no novo.
- Regras de acesso (fonte: `SecurityConfig.java`, linhas 36–52):
  - Público: `/api/auth/{login,register,refresh,logout}`, `/swagger-ui/**`, `/v3/api-docs/**`, `GET /api/sensors/**`, `GET /api/weather/**`.
  - Autenticado (qualquer papel): todo o resto de `/api/**`.
  - Somente `ADMIN`: `/api/admin/ingestion/**`, `/api/admin/terrain/**`.
- Erros: o OpenAPI padroniza `400, 401, 404, 409, 500` com o corpo `ErrorMessage` em todas as operações.
- Instantes: ISO-8601 em UTC com `Z` (ADR 0001 e ADR 0006, contrato 2). Fuso `America/Recife` só na exibição.

## 2. Endpoints por grupo

Total: 52 operações em 51 caminhos (fonte: contagem em `openapi-integracao-fase2.json`). "Auth" segue a seção 1. "Novo" marca o que não existia no snapshot da Fase 1.

### 2.1 Autenticação (`auth-controller`)

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| POST | `/api/auth/login` | público | Autentica; devolve tokens | `LoginResponse` |
| POST | `/api/auth/refresh` | público | Renova o token | `LoginResponse` |
| POST | `/api/auth/register` | público | Cria usuário | sem corpo |
| POST | `/api/auth/logout` | público | Encerra a sessão | sem corpo |
| GET | `/api/auth/me` | autenticado | Usuário e papel atuais | `UserInfoResponse` |

### 2.2 Pontos, previsão e dado agregado (`MAPI`)

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| GET | `/api/pontos` | autenticado | Lista os pontos de monitoramento | `FloodPointResponseDTO[]` |
| POST | `/api/pontos` | autenticado | Registra ponto novo | `FloodPointResponseDTO` |
| GET | `/api/pontos/{slug}` | autenticado | Status atual do ponto | `FloodPointResponseDTO` |
| POST | `/api/pontos/scenarios` | autenticado | Registra observação de cenário de alagamento | `FloodScenarioLabelResponseDTO` |
| POST | `/api/flood-predictions` | autenticado | Roda predição da IA para `latitude`/`longitude` e grava auditoria; 200 também quando não há previsão válida | `FloodPredictionResponseDTO` |
| GET | `/api/environmental-data` | autenticado | Dado ambiental agregado (sensores, clima, maré, ondas) por coordenada | `MapiResponseDTO` |
| GET | `/api/pontos/{slug}/terrain` | autenticado | Terreno PE3D do ponto (novo) | `TerrainResponseDTO` |

### 2.3 Eventos de alagamento

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| POST | `/api/eventos-alagamento` | autenticado | Registra ocorrência real (por `slug`) | `FloodEventDTO` |
| POST | `/api/eventos-alagamento/ingest` | autenticado | Ingere evento bruto de scraper por coordenadas (ver pendência 8) | `FloodEventDTO` |
| GET | `/api/eventos-alagamento/{slug}` | autenticado | Histórico paginado do ponto | `PageFloodEventDTO` |

### 2.4 Sensores, clima, ondas

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| GET | `/api/sensors/latest` | público | Leituras recentes de todos os sensores | `SensorResponseDTO[]` |
| GET | `/api/sensors/{sensorId}/latest` | público | Leitura mais recente do sensor | `SensorResponseDTO` |
| GET | `/api/sensors/{sensorId}/history` | público | Histórico paginado | `PageSensorResponseDTO` |
| GET | `/api/sensors/inventory` | público | Inventário paginado | `PageSensorResponseDTO` |
| GET | `/api/sensors/ids` | público | IDs de sensores distintos | lista (tipo do item não declarado) |
| GET | `/api/weather` | público | Clima atual por coordenada | `WeatherResponseDTO` |
| GET | `/api/marine` | autenticado | Dados marinhos (Open-Meteo) | `JsonNode` |
| GET | `/api/marine/wave-height` | autenticado | Altura da onda atual | `number` |

### 2.5 Maré

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| GET | `/api/tide/{harbor}` | autenticado | Tábua do porto (`year` opcional) | `TideTableResponseDTO` |
| GET | `/api/tide/harbors` | autenticado | Portos disponíveis | lista (tipo do item não declarado) |
| GET | `/api/tide/search` | autenticado | Busca por nome, paginada | `PageTideTableResponseDTO` |
| GET | `/api/tide/state/{state}` | autenticado | Tábuas por estado, paginado | `PageTideTableResponseDTO` |
| GET | `/api/tabua-mare/states` | autenticado | Estados costeiros (API externa) | `TabuaMareResponseListString` |
| GET | `/api/tabua-mare/harbors/state/{state}` | autenticado | Portos por estado | `TabuaMareResponseListObject` |
| GET | `/api/tabua-mare/harbors/{ids}` | autenticado | Portos por IDs | `TabuaMareResponseListObject` |
| GET | `/api/tabua-mare/nearest` | autenticado | Porto mais próximo de `latitude`/`longitude` | `TabuaMareResponseObject` |
| GET | `/api/tabua-mare/tide/{harbor}/{month}/{days}` | autenticado | Tábua por porto e período | `TabuaMareResponseListObject` |

### 2.6 Exportação para a IA (`Exportação de Dados`)

| Método | Caminho | Auth | O que faz | Resposta |
|---|---|---|---|---|
| GET | `/api/export/ia-dataset/{slug}` | autenticado | Dataset unificado do ponto (`days`, padrão 30) | `UnifiedDataDTO[]` |
| GET | `/api/export/ia-dataset/stream` | autenticado | Dataset via streaming JSON (`days`, padrão 30) | `StreamingResponseBody` |
| GET | `/api/export/ia-dataset/{slug}/csv` | autenticado | CSV do ponto (`days`, `to`); cabeçalho `X-Dataset-Id` (SHA-256 do CSV) | `string` (CSV) |
| GET | `/api/export/ia-dataset/all/csv` | autenticado | CSV único de todos os pontos (`days`, `to`) | `string` (CSV) |
| GET | `/api/export/ia-dataset/{slug}/manifest` | autenticado | Manifesto do dataset (novo) | `ExportManifestDTO` |

### 2.7 Administração (somente `ADMIN`)

| Método | Caminho | O que faz | Resposta |
|---|---|---|---|
| POST | `/api/admin/ingestion/runs` | Dispara REFETCH ou REVALIDATE de `[from, to)` de uma fonte; assíncrono (novo) | 202 `{<chave>: uuid}` (ver 3.4) |
| GET | `/api/admin/ingestion/runs/{id}` | Consulta um `ingestion_run` (novo) | `IngestionRunView` |
| POST | `/api/admin/ingestion/ckan/revalidate` | REVALIDATE de um recurso CKAN a partir do bruto gravado, síncrono (novo) | `IngestionRunView` |
| POST | `/api/admin/ingestion/ckan/backfill-geocode-level` | Preenche `geocode_level` e `neighborhood` de ocorrências legadas, síncrono (novo) | `GeocodeLevelBackfill` |
| POST | `/api/admin/terrain/runs` | Carrega o terreno do diretório `mapi.terrain.dir` (novo) | `TerrainLoadResultDTO` (200 ou 201) |
| POST | `/api/admin/ingestion/historical-apac` | Histórico de chuva APAC de uma estação (`stationCode`, `year`) | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/historical-apac-full` | Histórico APAC do estado inteiro (`year`) | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/historical-civil-defense` | Histórico da Defesa Civil, últimos `years` | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/historical-sensors` | Histórico de sensores ANA + APAC, todos os pontos | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/historical-weather` | Histórico Open-Meteo, todos os pontos | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/historical-full-sync` | Sincronização total (Clima, ANA, APAC, Defesa Civil); o alinhamento de eventos saiu | `IngestionAcceptedDTO` |
| POST | `/api/admin/ingestion/tide-sync` | Sincroniza a tábua TabuaMare dos portos próximos aos pontos | `TideSyncSummaryDTO` |
| POST | `/api/admin/ingestion/repair-stations` | Repara o mapeamento de estações dos pontos piloto, síncrono | `IngestionAcceptedDTO` |
| GET | `/api/admin/ingestion/check-integrity` | Relatório de integridade (lacunas e contagens) | `DataHealthReportDTO[]` |
| POST | `/api/admin/ingestion/align-events` | Desativado: responde 410 Gone | `ErrorMessage` |
| DELETE | `/api/admin/ingestion/wipe-database` | Só no perfil `dev`; ausente deste snapshot | 204 |

## 3. Contratos que importam para a dissertação

### 3.1 Predição (ADR 0007) — `POST /api/flood-predictions`

`FloodPredictionResponseDTO`: `riskLevel`, `floodProbability`, `modelVersion`, `issuedAt`, `horizonHours`, `validUntil`, `degradationReason`, `lastValidIssuedAt`, `contributors[]`, `imputedFeatures[]`, `estimatedTimeToEvent`, `message`.

- `riskLevel` é `LOW | MEDIUM | HIGH | UNKNOWN` (enum no OpenAPI). `UNKNOWN` significa "não há previsão válida" e não é risco baixo. A IA nunca emite `UNKNOWN`; quem atribui é o backend. `CRITICAL` e `EXTREME` saíram.
- Coerência (ADR 0007, seção 2): com `LOW/MEDIUM/HIGH`, `floodProbability` ∈ [0, 1], `modelVersion` e `issuedAt` não nulos e `degradationReason = null`. Com `UNKNOWN`, `floodProbability = null` e `degradationReason` não nulo; `horizonHours`, `validUntil` e `modelVersion` podem ser nulos.
- `degradationReason` (enum no OpenAPI): `INSUFFICIENT_DATA` (IA respondeu 422), `AI_TIMEOUT`, `AI_UNAVAILABLE` (conexão recusada ou circuit breaker aberto), `AI_ERROR` (5xx, corpo inválido ou payload incoerente). O texto da exceção vai só para o log; `message` é fixo por motivo.
- `issuedAt` e `validUntil` são `date-time` em UTC com `Z`. `issuedAt` é a emissão da previsão ou, se `UNKNOWN`, o instante da tentativa que falhou. `validUntil = issuedAt + horizonHours`; nulo se `UNKNOWN`.
- `lastValidIssuedAt`: `issuedAt` da última previsão bem-sucedida do ponto em `flood_predictions`; nulo se nunca houve.
- `contributors[]` (`featureId`, `label`, `contribution`) e `imputedFeatures[]` (`featureId`, `label`): o `label` vem de catálogo fixo em português (`feature-catalog.properties`); id fora do catálogo vira "Outro fator". Listas vazias até a IA da Fase 4 enviar contribuições.
- `estimatedTimeToEvent` segue `null` (obsoleto). `modelVersion` leva só a parte semântica; o hash git fica no log.
- HTTP: 200 inclusive para `UNKNOWN` (descrição da operação no OpenAPI). O snapshot antigo também listava só `200`; o ADR 0007 registra que o código anterior respondia 201 em falha, ponto a conferir (pendência 9).

### 3.2 Proveniência e export (ADR 0006)

- `provenanceId` (string, UUID do `ingestion_run` que produziu o valor vigente) e `revision` (inteiro) estão em `SensorResponseDTO` e `WeatherResponseDTO`. Nos dois DTOs o OpenAPI os declara como `string` e `integer`. O ADR 0006 não os põe por linha do export, porque a linha é agregada.
- Timestamps de `UnifiedDataDTO` e `SensorResponseDTO` agora são `date-time` (ISO-8601 com `Z`); `UnifiedDataDTO` ganhou `timestampUtc` ao lado de `timestamp`.
- `GET /api/export/ia-dataset/{slug}/csv` e `/all/csv` aceitam `days` e `to` (fim da janela). A resposta do `{slug}/csv` leva o cabeçalho `X-Dataset-Id` = SHA-256 do CSV, que é o `datasetId` do manifesto (descrito só no `summary`; ver pendência 3).
- `GET /api/export/ia-dataset/{slug}/manifest` devolve `ExportManifestDTO`: `datasetId`, `slug`, `periodFrom`, `periodTo`, `timezone` (`UTC`), `ruleVersion`, `codeVersion`, `rows`, `rowsWithFlag`, `provenance[]` (`source`, `variable`, `runIds[]`, `maxRevision`), `terrain` (`Provenance`), `tide` (`TideManifest`: `verticalReference`, `hourlyMethod`, `uncertainty`, `note`, `rowsWithTide`), `label` (`LabelManifest`: `policy`, `horizonsHours`, `counts` por horizonte com `positives/negatives/excluded`, `dayLabelPositives`, `excludedByReason`, `linkageConfigId`, `linkageMethod`, `linkageRadiusM`, `postEventWindowHours`, `occurrencesDay`, `occurrencesHour`), `missingPolicy`, `missingCounts`, `sourcesNotMigrated`, `deprecatedColumns` (`isFlooded`, `severity`).
- Colunas do CSV (fonte: `DataExportServiceImpl.generateCsv`, `TerrainExportColumnsImpl.HEADER`), na ordem:
  1. Base: `floodPointSlug, timestamp, sensorPrecipitation, sensorWaterLevel, sensorSoilHumidity, weatherPrecipitation, weatherTemperature, weatherPressure, weatherCode, tideHeight, accumulated3h…accumulated48h, isFlooded, severity, timestampUtc`.
  2. `<variável>_missing` para as 13 variáveis acima (1 = ausente, 0 = presente).
  3. Rótulos: `label_h<H>` para cada horizonte de `labelProps.horizonsHours`, depois `label_day`, `label_excluded_reason`, `occurrence_ids`.
  4. `soil_moisture_0_7cm` e `soil_moisture_0_7cm_missing`.
  5. Maré: `tide_height_t1…t6`, `tide_max_next_6h`, `tide_hours_to_next_high`, `tide_range_day_m`, `tide_rising`, `tide_slope_m_h`, depois as mesmas 11 com sufixo `_missing`.
  6. Terreno: `terrain_run_id, terrain_street_elevation_m, terrain_mean_slope_pct, terrain_depression_depth_m, terrain_contributing_area_m2` (vazios se o ponto é `NO_DATA`).
- Política de ausência (`missingPolicy` no manifesto): sem imputação; ausente é célula vazia (CSV) ou `null` (JSON). `isFlooded` e `severity` são legados e obsoletos; o rótulo novo vem de `flood_occurrence`.
- Alturas de maré do export estão referidas ao NR da DHN (`NR_DHN_pe02`) e não foram convertidas ao datum do MDT (ADR 0009).

### 3.3 Terreno (ADR 0008 e ADR 0009) — `GET /api/pontos/{slug}/terrain`

- Lê a execução vigente (`terrain_run.current`). Ponto fora das folhas do MDT, ou sem carga, responde 200 com `status = NO_DATA` e atributos nulos, nunca zero.
- `TerrainResponseDTO`: `floodPointSlug`, `status` (`OK` ou `NO_DATA`), `streetElevationM` (mediana do MDT num raio de 3 m), `meanSlopePct` e `maxSlopePct` (janela de 100 m), `depressionDepthM`, `contributingAreaM2` (D8), `bottomElevationM`, `bottomDistanceM`, `volumeCurve[]` (`CurvePoint`: `aboveBottomCm`, `elevationM`, `wetAreaM2`, `cumulativeVolumeM3`, `m3PerCm`; 41 níveis de 5 cm, vazia se `NO_DATA`), `diagnostics` (JSON livre), `provenanceId` (id do `terrain_run`, nulo se nunca houve carga), `provenance` (`vrtSha256`, `scriptSha256`, `flightYear`, `verticalReference`, `runAt`) e `tideComparabilityWarning`.
- Aviso do ADR 0009 (campo `tideComparabilityWarning`, texto fixo; descrição da operação): as cotas estão no datum do MDT, que **não está confirmado**, e **não são comparáveis ao nível da maré**. A tábua usa o NR da DHN. Enquanto o desnível Δ entre os dois planos não for aprovado, não se calcula "nível lá fora − cota da rua", folga até a soleira nem limiar de maré acima da rua.
- O endpoint não serve raster; o COG é arquivo estático do front.

### 3.4 Ingestão administrativa (ADR 0006, `docs/INGESTAO.md`)

- `POST /api/admin/ingestion/runs`, corpo `IngestionRunRequestDTO`: `source`, `from`, `to` (`date-time`, intervalo `[from, to)`), `mode` (`REFETCH | REVALIDATE`) e `kind` (`LIVE | BACKFILL | REPROCESS`; no guia, o `kind` é implícito `REPROCESS`).
  - Assíncrono: responde 202 com um objeto de valores UUID (o guia chama a chave `runId`; o OpenAPI não nomeia a chave) e o run segue em segundo plano. Consultar `GET /api/admin/ingestion/runs/{id}` até `status` deixar de ser `RUNNING` (depois `OK` ou `FAILED`; `errorSummary` explica a falha).
  - `REFETCH` baixa de novo a fonte. `REVALIDATE` reaplica regras e fuso sobre o bruto já gravado, sem acessar a agência. Repetir a chamada não insere nem altera nada; registro que a agência deixou de devolver não é apagado (`absentInRefetch`). Um reprocessamento por fonte por vez.
  - Fontes presentes no código: `OPEN_METEO_ARCHIVE`, `OPEN_METEO_ERA5LAND`, `ANA_SOAP`, `APAC_METEO24H`, `CEMADEN`, `DHN_TABUA_PE02`, `TABUAMARE_PE02`, `TIDE_PE02_HOURLY`, `CKAN_RECIFE`. Ordem da maré: `DHN_TABUA_PE02`, `TABUAMARE_PE02`, por último `TIDE_PE02_HOURLY`. O limite da TabuaMare é 15 chamadas por 60 s (`docs/INGESTAO.md`).
- `IngestionRunView`: `id`, `parentRunId`, `source`, `kind`, `mode`, `status`, `startedAt`, `finishedAt`, `intervalStart`, `intervalEnd`, `fetched`, `rawInserted`, `rawUnchanged`, `valid`, `quarantined`, `gaps`, `obsInserted`, `obsUpdated`, `obsUnchanged`, `obsRetracted`, `absentInRefetch`, `batchDeduped`, `parserVersion`, `ruleVersion`, `codeVersion`, `params`, `errorSummary`.
- `POST /api/admin/ingestion/ckan/revalidate?resourceId=…&packageId=…`: síncrono, devolve `IngestionRunView`. `resourceId` é obrigatório.
- `POST /api/admin/ingestion/ckan/backfill-geocode-level`: síncrono, devolve contagens `examined, street, neighborhood, city, none, unverifiable, neighborhoodFilled` (`GeocodeLevelBackfill`).
- `POST /api/admin/terrain/runs`: carrega o terreno do diretório configurado. Idempotente: o mesmo conteúdo (SHA-256 dos 3 arquivos) devolve o run existente (200); conteúdo novo cria `terrain_run` e o torna vigente (201). `TerrainLoadResultDTO`: `runId, created, current, inputSha256, curveRows, pointsOk, pointsNoData, slugsNotFound[]`.
- `align-events` está desativado (410); o alinhamento ao pico de chuva criava rótulo circular (ADR 0006).

## 4. Diferenças em relação ao snapshot da Fase 1

Contagem: 46 operações (Fase 1) contra 52 (integração Fase 2). 7 adicionadas, 1 removida, 4 alteradas (fonte: script de comparação sobre os dois JSON, método + caminho).

Operações adicionadas (7):
`GET /api/admin/ingestion/runs/{id}`, `POST /api/admin/ingestion/runs`, `POST /api/admin/ingestion/ckan/revalidate`, `POST /api/admin/ingestion/ckan/backfill-geocode-level`, `POST /api/admin/terrain/runs`, `GET /api/export/ia-dataset/{slug}/manifest`, `GET /api/pontos/{slug}/terrain`.

Operação removida (1): `DELETE /api/admin/ingestion/wipe-database`. Não foi apagada do código: passou para `@Profile("dev")` e some do OpenAPI fora desse perfil.

Operações alteradas (4):
- `GET /api/export/ia-dataset/{slug}/csv`: parâmetro `to` novo; summary cita `X-Dataset-Id`.
- `GET /api/export/ia-dataset/all/csv`: parâmetro `to` novo.
- `POST /api/admin/ingestion/align-events`: summary passa a "DESATIVADO (410 Gone)". O OpenAPI continua listando `200` como resposta e não lista `410` (pendência 5).
- `POST /api/admin/ingestion/historical-full-sync`: summary sem "alinhamento de eventos".

Schemas novos (14): `Contributor`, `CurvePoint`, `ExportManifestDTO`, `GeocodeLevelBackfill`, `ImputedFeature`, `IngestionRunRequestDTO`, `IngestionRunView`, `LabelCounts`, `LabelManifest`, `Provenance`, `SourceProvenance`, `TerrainLoadResultDTO`, `TerrainResponseDTO`, `TideManifest`. Nenhum schema removido.

Schemas alterados (4):
- `FloodPredictionResponseDTO`: de 4 campos (`riskLevel` string livre, `floodProbability`, `message`, `estimatedTimeToEvent`) para 12; `riskLevel` vira enum com `UNKNOWN`; entram `modelVersion`, `issuedAt`, `horizonHours`, `validUntil`, `degradationReason` (enum), `lastValidIssuedAt`, `contributors`, `imputedFeatures`.
- `UnifiedDataDTO`: de 17 para 29 campos. Novos: `timestampUtc`, `labelByHorizon`, `labelDay`, `labelExcludedReason`, `occurrenceIds`, `soilMoisture07cm`, `tideHeightAhead`, `tideMaxNext6h`, `tideHoursToNextHigh`, `tideRangeDayM`, `tideRising`, `tideSlopeMh` (contagem de propriedades dos dois JSON; o `tideSlopeMh` do JSON corresponde à coluna `tide_slope_m_h` do CSV).
- `WeatherResponseDTO`: `provenanceId` e `revision` novos.
- `SensorResponseDTO`: `provenanceId` e `revision` novos.

## 5. Pendências de documentação no código

Itens que o OpenAPI gerado não cobre ou cobre de forma incompleta (a corrigir com `@Operation`/`@ApiResponse`/`@Schema`; não alterado nesta tarefa):

1. Autenticação e papel não aparecem por operação: só há `bearerAuth` global (`security` por operação vazio). O fato de `/api/admin/**` exigir `ADMIN` e de `/api/sensors/**` e `/api/weather/**` serem públicos só está em `SecurityConfig.java`. Sem `@SecurityRequirement` ou texto por operação.
2. As 5 operações de `auth-controller` não têm `summary` nem `description`.
3. O cabeçalho de resposta `X-Dataset-Id` não está declarado em `responses.200.headers` do `{slug}/csv`; consta apenas no `summary`. O CSV também não tem descrição de colunas nem `Content-Type` `text/csv` (a resposta é `*/*` com `string`).
4. `POST /api/admin/ingestion/runs` responde 202 com `additionalProperties: uuid`; falta um schema com a chave `runId` (o guia `docs/INGESTAO.md` usa `runId`).
5. `align-events` não declara `410` em `responses`; hoje lista `200` e o erro padrão.
6. `POST /api/admin/terrain/runs` declara 200 e 201, mas o 200 e o 201 compartilham o schema sem exemplo; não há descrição de falha (diretório ausente ou arquivo faltando).
7. `GET /api/sensors/ids` e `GET /api/tide/harbors` não declaram o tipo do item da lista; `GET /api/marine` devolve `JsonNode` sem forma.
8. Semântica do `POST /api/eventos-alagamento/ingest`: o `summary` fala em 201 (criado) e 204 (já existia), mas o OpenAPI só lista `200`.
9. `POST /api/flood-predictions`: o OpenAPI lista só `200`; confirmar contra o código se algum caminho ainda responde 201 (o ADR 0007 registra que o código anterior respondia 201 também em falha).
10. Nenhuma operação tem `@ApiResponse` específico. Todas herdam o mesmo bloco de `400, 401, 404, 409, 500` com `ErrorMessage`, inclusive rotas que não retornam 404 ou 409.
11. `provenanceId` aparece como `string` em `SensorResponseDTO` e `WeatherResponseDTO`, sem `format: uuid` nem descrição; em `TerrainResponseDTO` é `integer(int64)` (id do `terrain_run`, não UUID). Mesmo nome, dois tipos: documentar.
12. `UnifiedDataDTO` e vários DTOs não têm `@Schema(description)`: unidade, fuso e significado de `tideRising`, `labelByHorizon` e `severity` só estão no `LABEL_POLICY` do manifesto.
13. O perfil `dev` e a operação `wipe-database` não têm nota no OpenAPI de produção (a rota some do documento); o contrato fica sem registro público.
14. `info.version` é `0.0.1`; não há controle de versão do contrato além deste arquivo e dos ADRs.
15. Nome de usuário/papel `dev`: não existe papel `dev` em `Role` (só `USER` e `ADMIN`); `dev` é perfil de execução do Spring.
