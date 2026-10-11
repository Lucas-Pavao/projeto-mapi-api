# Snapshot do banco antes da revisão — 2026-10-10

Tarefa F2-DADOS-00 (item 0 da matriz). É o "antes" auditável de qualquer migração ou correção retroativa.

- Arquivo: `MAPI/snapshots/tide_db-2026-10-10-pre-revisao.dump` (fora do git; `pg_dump -Fc`, 408 355 bytes)
- SHA-256: `3ace3cdeaeeaee39bc4a8e8321098dc31c7b62b7ef98bd54304d426c69d4dedd`
- Origem: volume docker `projeto-mapi-api_postgres_data`, container `mapi-db`, só o Postgres de pé (a API não rodou)
- Servidor: PostgreSQL 16.14, TimescaleDB 2.27.2 (imagem `timescale/timescaledb:latest-pg16`, sem PostGIS)
- Flyway: V1 baseline schema, V2 add river alert levels (ambas `success`)
- Tamanho do banco: 19 MB
- Restaurar: `pg_restore -U mapi_user -d <banco_vazio_com_timescaledb> --no-owner <arquivo>` (a restauração de hypertables exige `timescaledb_pre_restore()` / `timescaledb_post_restore()`)

## Contagem por tabela

| Tabela | Linhas | Período |
|---|---|---|
| sensor_data (hypertable, 5 chunks, 1,3 MB) | 555 | 2026-09-05 12:10 → 2026-10-01 18:20 |
| weather_data (hypertable, 4 chunks, 320 kB) | 40 | 2026-09-09 16:00 → 2026-10-01 17:30 |
| flood_predictions (hypertable, 4 chunks, 6,8 MB) | 25 102 (1 650 FAILED) | 2026-09-06 → 2026-10-01 |
| flood_scenario_labels (hypertable) | 0 | — |
| flood_events | 0 | — |
| flood_points | 5 | — |
| flood_point_pluviometers / river_levels / weather_stations | 12 / 1 / 0 | — |
| tide_tables / month_data / day_data / hour_data | 1 / 12 / 366 / 1 415 | — |
| users / refresh_token | 8 / 8 | — |
| geo_locations | 1 | — |

Os timestamps estão como gravados (sem fuso declarado; ver ADR 0001).

## Leitura

- **Não há histórico de 5 anos neste banco**: 555 leituras de sensor e 40 de clima em ~4 semanas, e 0 eventos de alagamento (rótulos).
- `flood_predictions` tem 45 vezes mais linhas que `sensor_data`: confirma que a navegação grava auditoria (F1-B2-25, F1-F2-06).
- O log do `pg_dump` tem 1 aviso (o esperado de dependência circular em hypertables do TimescaleDB).
