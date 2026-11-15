# Snapshot pré-recarga — 2026-11-08

Cópia do banco de desenvolvimento antes da recarga e da troca de nome para `mapi_db` (ADR 0020, proposto). Nada foi apagado ao gerar este arquivo.

- Arquivo: `MAPI/snapshots/tide_db-2026-11-08-pre-recarga.dump` (fora do git; `pg_dump -Fc -Z 9`, 102 MB)
- SHA-256: `c8e8e0684a9e9ca53a2937036d62e05f5f1ec290dff292310455d84d577c3189` (arquivo `.sha256` ao lado)
- Origem: contêiner `mapi-db`, banco `tide_db`, imagem `timescale/timescaledb-ha:pg16.15-ts2.30.2`, extensão TimescaleDB 2.30.2; Flyway em V22
- Banco: 1 491 MB; `pg_restore -l` lista 77 blocos `TABLE DATA`; log do `pg_dump` sem erro (só o aviso usual de FK circular em `continuous_agg`, catálogo do TimescaleDB)
- Contagens (consulta `count(*)` em 2026-11-08): `raw_observation` 1 101 620 · `observation` 5 433 · `ingestion_run` 470 · `flood_occurrence` 225 · `sensor_data` 1 667 · `weather_data` 200 · `flood_predictions` 2 233 · `alert` 443 · `users` 2 · `point_terrain` 5 · `flood_report` 0
- Série consolidada: só dado ao vivo (ANA de 2026-10-12 a 2026-10-22, CEMADEN de 2026-10-12 a 2026-11-05, TabuaMare de 2026-10-18 a 2026-11-15). O histórico de 5 anos não está aqui; está no snapshot `2026-10-10-pos-carga`.
- Restaurar: banco vazio com TimescaleDB 2.30.2, `timescaledb_pre_restore()`, `pg_restore --no-owner`, `timescaledb_post_restore()` (mesmo roteiro de `2026-10-10-pos-carga.md`).
- **Cópia fora da máquina: NÃO encontrada no Drive** (busca na pasta "MAPI — backup" em 2026-11-08: só o tar de 2026-10-11 e, fora dela, a planilha `eventos_ANA`). Por isso este dump local e o volume Docker `projeto-mapi-api_postgres_ha_data` (banco `tide_db`) **não foram apagados** na limpeza de disco. Enviar ao Drive é pendência do Lucas.

A partir desta data o banco passa a se chamar `mapi_db` (ADR 0020). Os manifestos anteriores continuam citando `tide_db`, que é o nome que existia quando foram gerados.
