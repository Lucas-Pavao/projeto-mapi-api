# Troca da imagem do banco — 2026-10-10

Execução do procedimento a–f do ADR 0005 (aprovado pelo Lucas, pergunta 20). Branch `feat/imagem-timescaledb-ha`.

| Passo | O que foi feito | Resultado |
|---|---|---|
| a | `mapi-api` e `mapi-ai` parados; só o banco de pé, imagem antiga `timescale/timescaledb:latest-pg16` | — |
| b | `pg_dump -Fc` → `MAPI/snapshots/tide_db-2026-10-10-pre-troca-imagem.dump` | 423 876 B, SHA-256 `eac2668743406633fc8152184dcfe51f38403c3315a370241262895bb78ce1cb` |
| c | volume novo `projeto-mapi-api_postgres_ha_data` (criado pelo compose); `projeto-mapi-api_postgres_data` **não** foi apagado | os dois volumes existem |
| d | imagem `timescale/timescaledb-ha:pg16.15-ts2.30.2`; extensão recriada **na versão do dump (2.27.2)**, `timescaledb_pre_restore()`, `pg_restore --no-owner`, `timescaledb_post_restore()`, depois `ALTER EXTENSION timescaledb UPDATE` → 2.30.2 | 0 erros no `pg_restore` |
| e | contagem por tabela antes × depois | **idêntica nas 17 tabelas**; Flyway V1, V2 `success`; 4 hypertables, 1 continuous aggregate |
| f | compose apontado para a imagem e o volume novos; API subiu `healthy` (Flyway + `ddl-auto: validate`), `GET /api/sensors/ids` 200 | PostGIS e `postgis_raster` 3.6.4 disponíveis (ainda não criados; ficam para a V7) |

## Contagens (dump `eac26687…`)
day_data 366 · flood_events 0 · flood_point_pluviometers 12 · flood_point_river_levels 1 · flood_point_weather_stations 0 ·
flood_points 5 · flood_predictions 25 242 · flood_scenario_labels 0 · flyway_schema_history 2 · geo_locations 1 ·
hour_data 1 415 · month_data 12 · refresh_token 10 · sensor_data 617 · tide_tables 1 · users 10 · weather_data 45

São números maiores que os do snapshot pré-revisão (`3ace3cde…`: sensor 555, clima 40, predições 25 102, usuários 8),
porque a linha de base do k6 e a subida da API para o OpenAPI gravaram dados depois dele (F1-BACK-04/05).

## Incidente durante a execução (registrado para auditoria)
A primeira tentativa do passo d restaurou direto na 2.30.2, porque um erro de script do orquestrador impediu o
`DROP`/`CREATE EXTENSION ... VERSION '2.27.2'` e o `pre_restore`. O resultado foram 18 erros de catálogo do
TimescaleDB, todos no banco **novo** recém-criado. O `tide_db` do volume novo foi recriado vazio e o passo d foi
refeito com os comandos explícitos. Volume antigo e dumps não foram afetados.

## Pendências
- Remoção do volume antigo `projeto-mapi-api_postgres_data`: exige aprovação separada.
- Testcontainers ainda usa `timescale/timescaledb:2.17.2-pg16`; precisa passar para a imagem `-ha` antes da migração V7 (PostGIS), senão a V7 falha nos testes.
