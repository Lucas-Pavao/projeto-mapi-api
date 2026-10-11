-- F2-DADOS-11 (docs/revisao/fase-2/F2-DADOS-11.md; critério em F2-DADOS-11-criterio.md, commit ea9b43c e nota datada).
-- Chuva da ANA_SOAP (legado) entra na grade horária como PT15M. Regra de qualidade declarada: a leitura cuja leitura
-- anterior da mesma série está a mais de um passo (mais de 1 h / n_expected) tem janela desconhecida e NÃO entra na
-- soma horária; a hora dela fica NULL por completude. Nada é apagado de `observation`. A regra é por fonte
-- (source_registry.exclude_after_gap), para não mudar nenhuma fonte existente. Pode colidir com migração de outra
-- branch no merge; renumerar se necessário.
ALTER TABLE source_registry ADD COLUMN exclude_after_gap BOOLEAN NOT NULL DEFAULT false;

UPDATE source_registry
   SET exclude_after_gap = true,
       note = note || ' | F2-DADOS-11: chuva do legado = incremento de 15 min com carimbo no fim; leitura depois de lacuna fica fora da hora.',
       updated_at = now()
 WHERE source = 'ANA_SOAP';

-- Mesma view da V5 (colunas e ordem idênticas), com a exclusão pós-lacuna das fontes marcadas.
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
    WHERE o.resolution IN ('PT5M', 'PT10M', 'PT15M', 'PT1H')
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
