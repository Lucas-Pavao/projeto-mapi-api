# Benchmark do banco — TimescaleDB+PostGIS × MySQL — 2026-10-10

Tarefa F2-DADOS-01. Alimenta o ADR 0005. Todos os números abaixo foram medidos nesta data, nesta máquina
(12 CPUs, 23 GB RAM, disco NVMe, Docker 29.1.3), em containers isolados `bench-ts` e `bench-mysql`
(sem volume nomeado; removidos com `docker rm -fv` ao final). O container `mapi-db` e o volume
`projeto-mapi-api_postgres_data` não foram tocados.

## 1. Ambiente

| Item | Valor | Origem |
|---|---|---|
| Imagem Timescale+PostGIS | `timescale/timescaledb-ha:pg16.15-ts2.30.2` (digest `sha256:ce47ce9026b0…`, mesma da tag flutuante `pg16` em 2026-10-10) | `docker pull`, `docker images --digests` |
| PostgreSQL | 16.15 (Ubuntu 16.15-1.pgdg22.04+2) | `select version()` |
| TimescaleDB / toolkit | 2.30.2 / 1.26.0 | `pg_extension` |
| PostGIS (inclui raster) | 3.6.4, GEOS 3.10.2, PROJ 8.2.1, GDAL 3.4.1 | `postgis_full_version()` |
| Config PG | padrão da imagem (`shared_buffers` 5932 MB, `max_wal_size` 1 GB) | `show` |
| MySQL | 8.4.11 (`mysql:8.4`), InnoDB, config padrão | `select version()` |
| Imagem em produção hoje | `timescale/timescaledb:latest-pg16` = PG 16.14 + TimescaleDB 2.27.2, sem PostGIS | `docs/snapshots/2026-10-10-pre-revisao.md` |

Comandos de subida:

```bash
docker pull timescale/timescaledb-ha:pg16.15-ts2.30.2
docker run -d --name bench-ts -e POSTGRES_PASSWORD=bench --shm-size=1g timescale/timescaledb-ha:pg16.15-ts2.30.2
docker run -d --name bench-mysql -e MYSQL_ROOT_PASSWORD=bench -e MYSQL_DATABASE=bench --shm-size=1g mysql:8.4 --local-infile=1
```

Compatibilidade do schema: `V1__baseline_schema.sql` e `V2__add_river_alert_levels.sql`, sem alteração,
rodaram com `ON_ERROR_STOP=1` na imagem nova (psql `-d bench`), seguidos de `CREATE EXTENSION postgis`.

## 2. Dados sintéticos

- `weather_data` (esquema exato do V1): 50 pontos × 43 824 h (2021-10-01 → 2026-09-30, inclui 29/02/2024) =
  **2 191 200 linhas**. Temperatura/umidade com ciclo diário + ruído; precipitação > 0 em 12% das horas;
  valores arredondados a 1 casa (como a Open-Meteo). Semente `setseed(0.42)`. Geração: 37,3 s.
- `sensor_data` (V1+V2): 12 sensores × 10 min × 5 anos = **3 155 328 linhas** (os 12 pluviômetros de hoje; cadência
  de 10 min é suposição `[sem fonte]`). Geração: 7,3 s.
- Limitação: ruído pseudoaleatório comprime pior que série real (suaves e repetitivas). A razão de compressão
  real tende a ser igual ou melhor; o tamanho em MySQL também.
- Chunk padrão do Timescale (7 dias): 261 chunks por hypertable.

## 3. Tamanho — `weather_data`, 2 191 200 linhas

| Configuração | Bytes | MB | Bytes/linha | Origem |
|---|---|---|---|---|
| Timescale sem compressão (tabela 303 456 256 + índices 384 671 744) | 688 128 000 | 656 | 314 | `hypertable_size`, `hypertable_detailed_size` |
| Timescale com compressão nativa (`segmentby latitude,longitude`; `orderby timestamp DESC`) | 126 115 840 (`hypertable_compression_stats`); 134 701 056 (`hypertable_size`, após VACUUM) | 120 / 128 | 57,6 / 61,5 | `hypertable_compression_stats` |
| **Razão de compressão** | **5,46×** | | | stats: 688 095 232 / 126 115 840 |
| MySQL 8.4, InnoDB, particionada por `YEAR(timestamp)` (6 anos + pmax), mesmos 3 índices | 493 273 088 (dados 263,6 MB + índices 206,8 MB) | 470 | 225 | `information_schema.tables` |
| MySQL 8.4, `ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8` | 239 812 608 | 229 | 109 | idem |

Leitura: com compressão, Timescale ocupa 53% do MySQL comprimido (126 MB × 240 MB) e 26% do MySQL sem
compressão. Sem compressão o MySQL é menor (493 × 688 MB): a hypertable carrega a PK `(id, timestamp)` e dois
índices secundários por chunk.

`sensor_data` (3 155 328 linhas, `segmentby sensor_id`): 1 111 269 376 B → 177 446 912 B (6,26×); compressão de
261 chunks em 18,9 s. `weather_data`: compressão de 261 chunks em 3,1 s.

## 4. Consulta típica de export (1 ponto, agregação horária)

Consulta Timescale (`EXPLAIN (ANALYZE, BUFFERS, SUMMARY)`), 6 agregados por hora:

```sql
SELECT time_bucket('1 hour', timestamp) AS h, avg(temperature), avg(humidity), avg(pressure),
       sum(precipitation), avg(wind_speed), avg(solar_radiation)
FROM weather_data WHERE latitude = -8.050 AND longitude = -34.850
  AND timestamp >= :ini AND timestamp < :fim GROUP BY h ORDER BY h;
```

MySQL: mesma consulta com `DATE_FORMAT(timestamp,'%Y-%m-%d %H:00:00')` e `EXPLAIN ANALYZE`. Cache quente,
3 execuções cada (tempo de execução do nó raiz, ms). Janela "30 dias" = 720 linhas; "5 anos" = 43 824 linhas.

| Cenário | Timescale sem compr. | Timescale com compr. | MySQL particionado | MySQL comprimido |
|---|---|---|---|---|
| 30 dias (planejamento) | 0,92–1,53 ms (plan. 1,6–2,9) | 1,12–1,19 ms (plan. 2,8–3,5) | 1,24–1,71 ms | 1,22–1,52 ms |
| 5 anos | **49,9–50,2 ms** (plan. 39–41) | **58,7–59,2 ms** (plan. 91–94) | **90,6–116 ms** | 85,1–116 ms |

(Execução 1 / 2 / 3 — Timescale sem compr. 30 d: 0,921 / 1,507 / 1,528; 5 a: 49,927 / 50,175 / 50,075.
Com compr. 30 d: 1,192 / 1,159 / 1,120; 5 a: 58,683 / 58,874 / 59,160. MySQL particionado 30 d: top-node
1,71 / 1,26 / 1,24; 5 a: 116 / 90,8 / 90,6 (tempo final do nó raiz). MySQL comprimido 30 d: 1,52 / 1,23 / 1,22;
5 a: 116 / 87,8 / 85,1.)

Leitura: o ganho não é de ordem de grandeza. Na janela de 5 anos o Timescale é ~1,5–1,8× mais rápido que o MySQL
(50–59 ms × ~91 ms em cache quente), mas o tempo de planejamento do Timescale em 261 chunks (39–94 ms) é do mesmo tamanho
da execução. Nos dois bancos, 1 ponto × 5 anos é sub-segundo. **Neste volume, o desempenho de consulta não
decide a escolha.**

Leitura do cagg: `SELECT … FROM weather_hourly` (1 ponto, 5 anos, 43 824 linhas) = 29,7 ms (sem compressão
do cagg).

### Assimetria de configuração e rerrodada do MySQL

A comparação acima tem viés: o PG usou `shared_buffers` 5932 MB e o MySQL o `innodb_buffer_pool_size` padrão
(128 MB), menor que os 470 MB de dados. Rerrodada com `--innodb-buffer-pool-size=2G` (2304 MB confirmados por
`@@innodb_buffer_pool_size`), mesma tabela e consultas, 3 execuções (tempo final do nó raiz, ms):

| Cenário | MySQL particionado, 2 GB | MySQL comprimido, 2 GB |
|---|---|---|
| 30 dias | 1,38–1,68 | 1,23–1,24 |
| 5 anos | 82,6–90,4 | 82,0–84,3 |

Com buffer pool equiparável, 1 ponto × 5 anos fica em ~83 ms contra 50–59 ms do Timescale (≈1,4–1,7×): não é
argumento contra o MySQL. Tamanhos em disco não mudam. Tempo de pull da `timescaledb-ha` (4,38 GB): não medido,
camadas em cache local `[sem fonte]`.

## 5. Agregação horária contínua

| Operação | Tempo | Origem |
|---|---|---|
| `refresh_continuous_aggregate('hourly_precipitation', 2021-10-01, 2026-10-01)` (a view do V1 sobre 3 155 328 linhas → 525 888 buckets) | **12,0 s** | `\timing`, `res_cagg` |
| `refresh_continuous_aggregate('weather_hourly', …)` (2 191 200 → 2 191 200 buckets, caso 1:1, o pior) | **27,6 s** | idem |
| Refresh incremental de 1 dia novo (1 200 linhas) | **54 ms** | idem |
| Tamanho dos caggs: `hourly_precipitation` / `weather_hourly` | 64 405 504 B / 447 791 104 B | `hypertable_size` |
| MySQL: `INSERT … SELECT … GROUP BY hora, lat, lon` (2 191 200 linhas) numa tabela-resumo, manual | 17,2 s | `weather_hourly` em MySQL |

O MySQL não tem cagg: a tabela-resumo e a rotina incremental (evento agendado ou job Java) seriam código
nosso. A primeira carga completa é mais rápida no MySQL (17 × 28 s), mas o refresh incremental é automático
só no Timescale.

## 6. Carga, backup e restauração

| Operação | Resultado | Origem |
|---|---|---|
| Carga CSV → MySQL (`LOAD DATA INFILE`, 2 191 200 linhas, particionada) | 13,2 s | `my_load.sql` |
| Cópia para tabela `ROW_FORMAT=COMPRESSED` | 37,9 s | `my_c.sql` |
| `pg_dump -Fc` do banco `bench` (2 hypertables comprimidas + caggs, ~5,3 M linhas) | 177 359 148 B; 15,6 s | `pg_dump` |
| `pg_restore` em banco novo com `timescaledb_pre_restore()` / `post_restore()` | 11,4 s; contagens idênticas (2 192 400 / 3 155 328) | `pg_restore` |
| Aviso do `pg_dump` | "circular foreign-key constraints" (o mesmo do snapshot de 2026-10-10) | stderr |

## 7. PostGIS / raster (imagem `…pg16.15-ts2.30.2`)

Script: `CREATE EXTENSION postgis; CREATE EXTENSION postgis_raster;` → ambos 3.6.4. Raster sintético 100×100 px,
1 m, canto (290000, 9110100), EPSG:31985, float32, nodata −9999, valor = coluna·1,0 + linha·0,01
(`ST_MapAlgebra`).

| Teste | Resultado |
|---|---|
| `ST_SRID`, `ST_Width`, `ST_BandNoDataValue` | 31985, 100, −9999 |
| `ST_Value(rast, ST_SetSRID(ST_MakePoint(290010.5, 9110089.5),31985))` (coluna 11, linha 11) | **11,11** (11,1099996…) — esperado 11 + 11·0,01 |
| Ponto fora do raster | `NULL` (com NOTICE) |
| WGS84 (−34,8770; −8,0476) → `ST_Transform` → 31985 | POINT(293144,40 9109965,42), dentro da faixa da RMR (E 280–300 km, N 9 090–9 130 km) |
| `ST_SummaryStats(ST_Clip(rast, ST_Buffer(ponto,3), true))` | 26 pixels, média 51,63 |
| `ST_Slope(rast,1,'32BF','PERCENT')` num pixel | 100,005 % (gradiente de 1 m/m em x) |
| Índice GiST `ST_ConvexHull(rast)` | criado |

Limite: raster de 10 000 pixels, não o MDT real (1 m, 48 folhas). Desempenho com o PE3D real (carga com
`raster2pgsql -t 256x256`, ou VRT/COG fora do banco) fica para a Fase 3.

## 8. MySQL 8.4 — espacial

Documentação oficial (MySQL 8.4 Reference Manual, "Spatial Data Types"): suporta apenas o modelo geométrico
OpenGIS (Geometry, Point, LineString, Polygon e coleções), funções de análise espacial e índice espacial,
em InnoDB/MyISAM/NDB/ARCHIVE. A página não menciona raster. Portanto: **sem tipo raster, sem `ST_Value` em
raster, sem declividade nem recorte de raster**. Mitigação possível: calcular cota/declividade/bacia fora do
banco (Python/rasterio) e guardar só o resultado por ponto — o que o MAPI faria de qualquer modo para 5
pontos, mas que perde consulta ad hoc em SQL.

## 9. Conta de volume projetado

Colunas numéricas medidas (V1+V2): `weather_data` = 8 variáveis (temperature, apparent_temperature, humidity,
pressure, weather_code, precipitation, wind_speed, solar_radiation), mais latitude/longitude (chaves do
ponto) e `is_day` (booleano); `sensor_data` = 14 variáveis (value, fog_value_reference, temperature,
humidity, pressure, wind_speed, solar_radiation, accumulated_precipitation, water_level, flow_rate,
tide_height, river_pre_alert_level, river_alert_level, river_flood_level), mais latitude/longitude.

Linhas = pontos × 8 760 h × 5 anos (365 dias; o benchmark usou 43 824 h, com bissexto):

| Pontos | Linhas horárias | Valores `weather_data` (×8) | Valores `sensor_data` se 1 sensor/ponto, hora (×14) | Tamanho extrapolado, sem compr. (314 B/linha) | Com compr. (61,5 B/linha) |
|---|---|---|---|---|---|
| 5 | 219 000 | 1 752 000 | 3 066 000 | 69 MB | 13 MB |
| 20 | 876 000 | 7 008 000 | 12 264 000 | 275 MB | 54 MB |
| 50 | 2 190 000 | 17 520 000 | 30 660 000 | 688 MB | 135 MB |
| 200 | 8 760 000 | 70 080 000 | 122 640 000 | 2,75 GB | 539 MB |

Tamanhos: extrapolação linear dos bytes/linha medidos em 50 pontos (seção 3); 5, 20 e 200 não foram medidos.
Sensores a cada 10 min multiplicam as linhas de `sensor_data` por 6 (12 sensores medidos: 1,1 GB → 177 MB).
**Conclusão numérica: até 200 pontos × 5 anos o dado de clima cabe em < 3 GB sem compressão.** Qualquer
dos dois bancos aguenta; o motivo para o Timescale é conforto (compressão, cagg), não necessidade.
