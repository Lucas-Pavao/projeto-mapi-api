# Snapshot pós-recarga do `mapi_db` — 2026-11-08

Primeira cópia do banco recarregado do zero com o nome novo (ADR 0020).

- Arquivo: `MAPI/snapshots/mapi_db-2026-11-08-pos-recarga.dump` (fora do git; `pg_dump -Fc -Z 9`, 388 MB)
- SHA-256: `3310825eafa1b17c4039068a72af7230b4f73d8cbc48dbb2439ecabc93c7da91` (arquivo `.sha256` ao lado)
- Origem: contêiner `mapi-db`, banco `mapi_db`, volume `projeto-mapi-api_postgres_mapi_data`, imagem `timescale/timescaledb-ha:pg16.15-ts2.30.2`; Flyway em V23; código do branch `fix/saude-sensores-dados` (sem commit)
- Banco: 2 813 MB; `pg_restore -l` lista 138 blocos `TABLE DATA`; `pg_dump` saiu com 0 (só o aviso usual de FK circular em `continuous_agg`)
- Contagens (`count(*)`, 2026-11-08): `raw_observation` 2 207 252 · `observation` 3 308 094 · `ingestion_run` 55 · `station` 94 · `flood_occurrence` 225 · `occurrence_cluster` 187 · `point_terrain` 5 · `users` 2 · `alert_rule` 6 · `flood_events` 58
- Preservado do `tide_db` (dump `2026-11-08-pre-recarga`): `flood_points` e os três vínculos, `users`, `alert_rule`. Terreno recarregado do mesmo insumo (`inputSha256 00d8dfb5…`).
- Restaurar: banco vazio com TimescaleDB 2.30.2, `timescaledb_pre_restore()`, `pg_restore --no-owner`, `timescaledb_post_restore()`.
- **Cópia fora da máquina: pendente** (Lucas, pasta "MAPI — backup" do Drive).
- Relatório antes/depois por fonte: `docs/REVISAO-INGESTAO-2026-11.md`, seção 7.
