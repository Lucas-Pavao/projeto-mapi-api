# F2-BACK-01 — Pipeline de ingestão (ADR 0006)

- Branch: `feat/pipeline-ingestao` (a partir de `integracao/fase-2`). Sem push. Nenhuma migração aplicada no banco real; `docker-compose.yml` intocado.
- Status: **parcial, com testes verdes** (74 testes, 0 falhas). Itens 1, 2, 3 (Open-Meteo archive), 4, 5 e 6 feitos; CKAN e as demais fontes ficam pendentes (seção "Pendências").

## O que foi feito

1. **Migrações V3 a V6**, aditivas (teste T8 verifica que não há `DROP`, `DELETE`, `TRUNCATE` nem `ALTER ... TYPE`): `ingestion_run`, `station`, `source_registry`, `variable_catalog`; `raw_observation` (hypertable, append-only por gatilho) e `ingestion_quarantine`; `observation` (hypertable, `UNIQUE` natural), `observation_revision` com gatilho, views `observation_current` e `observation_hourly_v`; `flood_occurrence`, `occurrence_point_match`, `label_filter`, `linkage_config`, `point_station_link`. V7 (PostGIS) e V8 (compressão) não criadas; `sensor_data` e `weather_data` congeladas e intactas, sem migração de dado antigo.
2. **Núcleo** (`service/ingestion`, `repository/ingestion`): staging com hash SHA-256 canônico e dedupe contra a **versão mais recente** (A→B→A funciona); dedupe do lote antes do `ON CONFLICT`; validação pura (`ValidationService`) com quarentena (`TZ_UNVERIFIED`, `TS_UNPARSEABLE`, `SENTINEL`, `OUT_OF_RANGE`, `UNIT_MISMATCH`, `CONFLICT_IN_BATCH`, `UNKNOWN_VARIABLE`); lacuna gera `GAP_BEFORE` e contagem, nunca constante; upsert em uma instrução (`unnest` + `ON CONFLICT ... WHERE value_hash IS DISTINCT FROM OR status <> 'ACTIVE'`), reativação de `RETRACTED`; retirada (`RETRACTED`) de linhas cuja chave natural mudou; `advisory lock` por fonte; uma transação por lote; `Clock` injetado em UTC. O fuso é convertido **só na validação**, lido de `source_registry` (UNVERIFIED para ANA, APAC meteorologia24h, Cemaden e TabuaMare).
3. **Open-Meteo archive ponta a ponta** (`OpenMeteoArchiveAdapter`): `timezone=GMT` explícito, falha se `utc_offset_seconds != 0`, SHA-256 da resposta em `ingestion_run.params`, coordenada real da grade em `station.attrs`, estação = coordenada solicitada.
4. **Reprocessamento**: `POST /api/admin/ingestion/runs` (ADMIN, 202 + `runId`), `GET /api/admin/ingestion/runs/{id}`; `REFETCH` (revalida também os `raw_unchanged`, conta `absent_in_refetch`) e `REVALIDATE` (seleção pelo instante de origem com o fuso declarado no bruto **e** o fuso atual, margem de 3 dias na pré-seleção textual).
5. **Export**: o clima do CSV vem de `observation_hourly_v` (colunas do CSV idênticas às anteriores); `GET /api/export/ia-dataset/{slug}/manifest` e cabeçalho `X-Dataset-Id` (SHA-256 do CSV) nos endpoints CSV; parâmetro opcional `to` (ISO-8601) fixa o fim do período para reproduzir o mesmo hash. `provenanceId` e `revision` adicionados (aditivos, nulos por ora) a `SensorResponseDTO` e `WeatherResponseDTO`.

## Commits

`940cd5e` migrações V3–V6; `2f6504f` checkpoint (repositórios e validação); `3258fad` núcleo + Open-Meteo + endpoint admin + testes; `fc9be82` testes Open-Meteo e fuso; commit seguinte: export, manifesto e DTOs; este relatório.

## Como testar

`./mvnw -q clean test` (Docker necessário; imagem via `mapi.test.db-image`). Cobertura: `./mvnw -q clean org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`.

## Testes T1–T9

| Teste | Status | Onde |
|---|---|---|
| T1 idempotência (e 2 threads na mesma fonte) | verde | `IngestionPipelineIntegrationTest` |
| T2a correção retroativa | verde | idem |
| T2b chunk comprimido | **não feito** (compressão adiada, V8) | — |
| T2c hora reflete correção sem refresh | verde | idem |
| T2d A→B→A | verde | idem |
| T2e lote com versões do mesmo registro | verde | idem |
| T3 fuso (parametrizado com JVM em UTC, America/Recife, Asia/Tokyo; UNVERIFIED em quarentena) | verde | `TimezoneConversionTest`, `IngestionPipelineIntegrationTest` |
| T4 quarentena e lacuna | verde | `IngestionPipelineIntegrationTest` |
| T5 intervalo e reclassificação da quarentena | verde | idem |
| T5b deslocamento de fuso e reativação | verde | idem |
| T6 hora (15:00:00 em `(14:00, 15:00]`, 15:01 vai para 16:00, soma, última, <80% = NULL, P1D fora da grade, sem vazamento) | verde | idem |
| T7 rótulo CKAN e raio | **não feito** (CKAN pendente) | — |
| T8 migração | verde | `MigrationsIntegrationTest` |
| T9 `provenanceId` em `/api/sensors/{id}/latest` | **não feito** (sensores ainda legados) | — |

Extras: append-only do bruto, `absent_in_refetch`, Open-Meteo ponta a ponta (inclui `utc_offset_seconds != 0` ⇒ run `FAILED`), export com manifesto.

## Medidas

| | Antes (integracao/fase-2) | Depois |
|---|---|---|
| Testes | 49 | 74 (0 falhas) |
| Cobertura de linhas (JaCoCo) | 36,2% | 46,0% (1822 de 3964) |

## Pendências por fonte

- **CKAN**: não migrado (filtro Cobrade "alagamento", `HOUR`/`DAY`, casamento por raio em `linkage_config`; tabelas V6 existem, sem código). O código atual de `FloodEvent` segue em uso.
- **ANA, APAC meteorologia24h, Cemaden**: coletores atuais intocados; quando migrados, entram em quarentena `TZ_UNVERIFIED` até F2-DADOS-07 mudar `source_registry`.
- **APAC portal (P1D), TabuaMare, Open-Meteo current e marine**: sem adaptador; a troca de `timezone=auto` por `GMT` nos dois coletores ao vivo não foi feita. Maré no export continua via `TideService`.
- Sensores no export continuam vindos de `sensor_data` (legado, fuso não verificado); `point_station_link` sem uso ainda (o export acha a estação Open-Meteo pela coordenada do ponto).

## Desvios do ADR, com motivo

1. **Colunas extras** além do ADR: `raw_observation.seq` (desempate de "mais recente" no mesmo instante), `observation.origin_ts_norm` (a revalidação precisa saber quais linhas vieram de qual registro de origem para retirá-las), `ingestion_run.batch_deduped/gaps/obs_retracted`, `observation_revision.reason`.
2. **Faixas físicas** no `variable_catalog` (V3) são limites conservadores declarados pelo backend, todos com `range_source = '[sem fonte]'`; a lista de sentinelas está em `mapi.ingestion.sentinels`. Completude 80% é coluna `min_completeness` (também `[sem fonte]`).
3. **Contagens**: `fetched = raw_inserted + raw_unchanged` (após dedupe do lote, que tem contador próprio); `valid` e `quarantined` contam valores (observações), não registros.
4. **Formato de timestamp com `Z` NÃO aplicado** a `SensorResponseDTO` e `UnifiedDataDTO`: esses DTOs ainda carregam hora local sem fuso do legado, e rotulá-los com `Z` falsificaria o fuso. Muda junto com a migração dos sensores. Só `ExportManifestDTO` e `IngestionRunView` usam ISO-8601 com `Z`.
5. **Colunas `*_imputed` não acrescentadas**: o teste de integração e o projeto-mapi-ai dependem do cabeçalho exato do CSV (não verifiquei o leitor do AI). Além disso o export ainda aplica `fillMissingValues` com constantes (25 °C, 1013 hPa, 0 mm), contrário à regra "lacuna = NULL"; fica registrado para a tarefa de export.
6. **Fixtures**: a resposta do Open-Meteo nos testes é sintética (formato da API), não resposta real anonimizada com SHA-256 no nome.
7. **Convenção de intervalo do Open-Meteo** ("precipitation = soma da hora anterior", rótulo no fim da hora) foi adotada a partir de conhecimento da documentação, **não verificada nesta sessão** (sem acesso à rede); registrada em `variable_catalog.interval_convention = END`. Conferir antes da primeira carga.
8. Conflito entre um registro reprocessado e uma linha de outra origem **fora do lote** (chave natural colidindo com origem diferente) sobrescreve por valor/`revision`; só `CONFLICT_IN_BATCH` é detectado dentro do lote.
9. `OpenMeteoArchiveClient` é interface (mock de classe falha com Byte Buddy no JDK 25 deste ambiente).
