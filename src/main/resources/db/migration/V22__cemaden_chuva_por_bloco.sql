-- Chuva da CEMADEN na grade horária. Cada leitura do endpoint /cemaden/ da APAC é a chuva de um BLOCO (10 min quando
-- chove, 60 min quando está seco), com o carimbo no fim do bloco; não é acumulado. Verificado em 2026-10-21: a soma
-- dos blocos de 24 h do feed bate com o acc24hr publicado pela própria CEMADEN (Alto Mandu 4,7 x 4,73; Compaz
-- 6,2 x 6,2; Campina do Barreto 3,8 x 3,75; Guabiraba 3,2 x 3,15; Nova Descoberta 3,0 x 2,96).
--
-- Regra declarada (decisão do Lucas, 2026-10-21): a leitura fica com resolução EVENT, e a hora é a SOMA dos blocos
-- com fim nela. Bloco que falta numa hora com pelo menos uma leitura conta como zero (a estação estava transmitindo).
-- Hora sem nenhuma leitura não gera linha: estação calada é lacuna, não "não choveu". Um bloco de 60 min entra
-- inteiro na hora do seu fim; como a estação passa a 10 min quando detecta chuva, ele quase sempre é zero.
-- A regra é por fonte (source_registry.event_rain_blocks), para não mudar nenhuma outra fonte EVENT.
ALTER TABLE source_registry ADD COLUMN event_rain_blocks BOOLEAN NOT NULL DEFAULT false;

UPDATE source_registry
   SET event_rain_blocks = true,
       note = coalesce(note, '') || ' | 2026-10-21: chuva = bloco de 10/60 min com carimbo no fim (bate com acc24hr da CEMADEN); hora = soma dos blocos.',
       updated_at = now()
 WHERE source = 'CEMADEN';

-- Mesma view da V15 (colunas e ordem idênticas), com a chuva EVENT das fontes marcadas (n_expected = 1).
CREATE OR REPLACE VIEW observation_hourly_v AS
WITH base AS (
    SELECT o.source, o.station_id, o.variable, o.resolution, o.unit, o.ts_utc, o.value, o.run_id, o.revision,
           o.quality_flag, c.hourly_aggregation, c.min_completeness,
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
       max(revision) AS max_revision
FROM kept
GROUP BY source, station_id, variable, resolution, hour_end_utc;
