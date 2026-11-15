-- Cemaden histórico pela Plataforma de Entrega de Dados (PED), ADRs 0021 e 0022.
-- ADITIVA: INSERT de configuração, CREATE TABLE de tabelas novas (uma de progresso da carga, duas derivadas e
-- recalculáveis), troca de um CHECK por um mais largo e CREATE OR REPLACE da view horária com duas colunas novas no
-- fim (as colunas antigas ficam idênticas, na mesma ordem). Nenhuma linha existente é apagada ou alterada.

-- 1. Fonte nova. Carimbo da PED em UTC: o próprio arquivo diz "OBS.: PCD com horario UTC!" e o teste de defasagem
--    contra a ANA (fuso verificado na V14) deu correlação máxima com defasagem zero (docs/extratos/cemaden-ped.md,
--    seção "Fuso"). A fonte nasce UNVERIFIED e sai dela pelo teste registrado, como a ANA e o Cemaden ao vivo.
INSERT INTO source_registry (source, source_tz, verification_state, note, event_rain_blocks) VALUES
    ('CEMADEN_PED', 'UTC', 'UNVERIFIED',
     'Cemaden PED (sws.cemaden.gov.br/PED/rest, /pcds/dados_pcd, sensor 10): chuva por bloco com carimbo no fim', true);

UPDATE source_registry
   SET verification_state = 'VERIFIED',
       note = note || ' | 2026-11-09: fuso UTC verificado. Cabeçalho do arquivo declara UTC; defasagem horária contra '
              'ANA 39187800 (V14) em 2022-05: correlação máxima em 0 h (r = 0,847; r(-1 h) e r(+1 h) menores). '
              'Teste em docs/extratos/cemaden-ped.md.',
       updated_at = now()
 WHERE source = 'CEMADEN_PED';

-- 2. Progresso da carga, por estação e janela: torna a carga retomável sem baixar de novo o que já entrou.
--    A linha é gravada na MESMA transação do lote (bruto + observação), então "OK" implica dado gravado.
CREATE TABLE ped_fetch_window (
    station_id    VARCHAR(120) NOT NULL,
    window_start  TIMESTAMPTZ  NOT NULL,
    window_end    TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('OK', 'NO_DATA')),
    http_status   INTEGER      NOT NULL,
    records       INTEGER      NOT NULL,
    sha256        CHAR(64),
    url           TEXT         NOT NULL,
    parser_version VARCHAR(40) NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (station_id, window_start, window_end)
);

-- 3. Vínculo da chuva medida com seleção do maior valor entre as estações válidas (ADR 0022).
INSERT INTO linkage_config (kind, radius_m, method, created_by, note) VALUES
    ('SENSOR_POINT', 5000, 'MAX_VALID_MONTHLY', 'V24',
     'ADR 0022: dentro de 5 km, maior valor horário entre as estações aprovadas na checagem espacial do mês');

ALTER TABLE point_station_link DROP CONSTRAINT point_station_link_method_check;
ALTER TABLE point_station_link ADD CONSTRAINT point_station_link_method_check
    CHECK (method IN ('NEAREST', 'ALL_WITHIN', 'MAX_VALID'));

-- 4. Checagem espacial mensal (derivada, recalculável; ADR 0022). Estação reprovada fica registrada com o motivo.
CREATE TABLE station_month_check (
    source                   VARCHAR(40)  NOT NULL,
    station_id               VARCHAR(120) NOT NULL,
    month_start_utc          TIMESTAMPTZ  NOT NULL,
    status                   VARCHAR(10)  NOT NULL CHECK (status IN ('PASS', 'FAIL', 'UNCHECKED', 'OFFLINE')),
    reasons                  TEXT[]       NOT NULL DEFAULT '{}',
    hours_in_month           INTEGER      NOT NULL,
    hours_with_value         INTEGER      NOT NULL,
    coverage                 DOUBLE PRECISION NOT NULL,
    total_mm                 DOUBLE PRECISION,
    n_neighbors              INTEGER      NOT NULL,
    neighbor_ratio_median    DOUBLE PRECISION,
    neighbor_common_mm_median DOUBLE PRECISION,
    metrics                  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    rule_version             VARCHAR(40)  NOT NULL,
    built_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (source, station_id, month_start_utc, rule_version)
);

-- 5. Série horária de chuva por ponto (derivada, recalculável; ADR 0022). Uma linha por hora da janela, inclusive
--    as lacunas (value nulo): cada linha declara a estação que forneceu o valor e quantas eram válidas.
CREATE TABLE point_rain_hourly (
    flood_point_id      BIGINT       NOT NULL REFERENCES flood_points (id),
    hour_end_utc        TIMESTAMPTZ  NOT NULL,
    value               DOUBLE PRECISION,
    source              VARCHAR(40)  NOT NULL,
    station_id          VARCHAR(120),
    station_distance_m  DOUBLE PRECISION,
    n_candidates        INTEGER      NOT NULL,
    n_valid             INTEGER      NOT NULL,
    n_valid_with_value  INTEGER      NOT NULL,
    n_blocks            INTEGER,
    blocks_covered_min  DOUBLE PRECISION,
    linkage_config_id   BIGINT       NOT NULL REFERENCES linkage_config (id),
    rule_version        VARCHAR(40)  NOT NULL,
    built_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (flood_point_id, rule_version, hour_end_utc)
);

-- 6. Mesma view da V22 (colunas e ordem idênticas) com duas colunas novas no fim, só para fontes por bloco cujo bloco
--    tem duração conhecida (interval_start_utc < ts_utc): quanto tempo os blocos da hora cobrem e onde começa o
--    primeiro. Um bloco que atravessa a virada da hora entra inteiro na hora do seu fim (regra da V22), então a
--    cobertura pode passar de 60 min numa hora e ficar abaixo de 60 na anterior.
CREATE OR REPLACE VIEW observation_hourly_v AS
WITH base AS (
    SELECT o.source, o.station_id, o.variable, o.resolution, o.unit, o.ts_utc, o.value, o.run_id, o.revision,
           o.quality_flag, c.hourly_aggregation, c.min_completeness, o.interval_start_utc,
           coalesce(r.event_rain_blocks, false) AND o.interval_start_utc < o.ts_utc AS block_known,
           time_bucket('1 hour', o.ts_utc - interval '1 microsecond') + interval '1 hour' AS hour_end_utc,
           CASE o.resolution WHEN 'PT5M' THEN 12 WHEN 'PT10M' THEN 6 WHEN 'PT15M' THEN 4 ELSE 1 END AS n_expected,
           o.ts_utc - lag(o.ts_utc) OVER (PARTITION BY o.source, o.station_id, o.variable, o.resolution
                                          ORDER BY o.ts_utc) AS gap_before
    FROM observation_current o
    JOIN variable_catalog c ON c.variable = o.variable
    LEFT JOIN source_registry r ON r.source = o.source
    WHERE (o.resolution IN ('PT5M', 'PT10M', 'PT15M', 'PT1H')
           OR (o.resolution = 'EVENT' AND c.hourly_aggregation = 'SUM' AND coalesce(r.event_rain_blocks, false)))
      AND c.hourly_aggregation IN ('SUM', 'LAST')
      AND o.value IS NOT NULL
), kept AS (
    SELECT b.* FROM base b LEFT JOIN source_registry s ON s.source = b.source
    WHERE NOT (coalesce(s.exclude_after_gap, false) AND b.hourly_aggregation = 'SUM'
               AND coalesce(b.gap_before > interval '1 hour' / b.n_expected, false))
)
SELECT source, station_id, variable, resolution, hour_end_utc,
       CASE WHEN count(*) < min(n_expected) * min(min_completeness) THEN NULL
            WHEN min(hourly_aggregation) = 'SUM' THEN sum(value)
            ELSE (array_agg(value ORDER BY ts_utc DESC))[1] END AS value,
       (array_agg(unit ORDER BY ts_utc DESC))[1] AS unit,
       count(*)::int AS n_readings,
       min(n_expected) AS n_expected,
       count(*) < min(n_expected) * min(min_completeness) AS incomplete,
       bool_or(quality_flag <> 'OK') AS has_flag,
       array_agg(DISTINCT run_id) AS run_ids,
       max(revision) AS max_revision,
       CASE WHEN bool_and(block_known)
            THEN sum(extract(epoch FROM ts_utc - interval_start_utc)) / 60.0 END AS blocks_covered_min,
       CASE WHEN bool_and(block_known) THEN min(interval_start_utc) END AS first_block_start_utc
FROM kept
GROUP BY source, station_id, variable, resolution, hour_end_utc;
