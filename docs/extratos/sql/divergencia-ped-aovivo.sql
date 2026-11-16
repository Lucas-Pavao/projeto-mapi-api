\timing on
CREATE TEMP TABLE h AS
SELECT a.canonical_id, h.source, h.hour_end_utc, h.value
FROM observation_hourly_v h
JOIN station_alias a ON a.source = h.source AND a.station_id = h.station_id
WHERE h.variable = 'precipitation' AND h.value IS NOT NULL
  AND h.source IN ('CEMADEN','CEMADEN_PED')
  AND h.hour_end_utc > '2026-11-08T00:00:00Z' AND h.hour_end_utc <= '2026-11-16T10:00:00Z';
SELECT source, count(*), min(hour_end_utc), max(hour_end_utc), count(DISTINCT canonical_id) est FROM h GROUP BY 1;
WITH p AS (
  SELECT l.canonical_id, l.hour_end_utc, l.value live, d.value ped, abs(l.value - d.value) diff
  FROM h l JOIN h d USING (canonical_id, hour_end_utc)
  WHERE l.source = 'CEMADEN' AND d.source = 'CEMADEN_PED')
SELECT count(*) horas_comparadas, count(DISTINCT canonical_id) estacoes,
       count(*) FILTER (WHERE diff > 0.5) divergentes,
       round(100.0 * count(*) FILTER (WHERE diff > 0.5) / nullif(count(*),0), 2) pct,
       count(*) FILTER (WHERE live > 0 OR ped > 0) horas_com_chuva,
       count(*) FILTER (WHERE diff > 0.5 AND (live > 0 OR ped > 0)) div_com_chuva,
       round(max(diff)::numeric, 1) max_diff,
       round(sum(live)::numeric,1) soma_live, round(sum(ped)::numeric,1) soma_ped
FROM p;
WITH p AS (
  SELECT l.canonical_id, l.hour_end_utc, l.value live, d.value ped, abs(l.value - d.value) diff
  FROM h l JOIN h d USING (canonical_id, hour_end_utc)
  WHERE l.source = 'CEMADEN' AND d.source = 'CEMADEN_PED')
SELECT canonical_id, hour_end_utc, live, ped, round(diff::numeric,2) diff FROM p WHERE diff > 0.5 ORDER BY diff DESC LIMIT 12;
