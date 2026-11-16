-- Entradas físicas novas do modelo v3 (etapa A3), por ponto e hora, ao lado de point_rain_accum_v e na mesma grade
-- (hour_end_utc, hora rotulada pelo fim). ADITIVA: só CREATE VIEW; nenhuma linha existente é apagada ou alterada.
--
-- Cada entrada vem com a coluna de origem. Ausente é NULL, nunca zero.
--   rain_max_10min_mm  maior bloco de 10 min da hora, da MESMA estação que deu o valor horário do ponto (ADR 0022).
--                      NULL se a hora do ponto é lacuna ou se algum bloco da hora é maior que 10 min E tem chuva: o
--                      pico de 10 min dentro dele não é conhecido. Bloco longo com 0 mm (hora seca do Cemaden chega como
--                      um bloco de 60 min) vale 0: se não choveu em 60 min, não choveu em nenhum 10 min.
--   tide_trend_m_h     nível da maré na hora menos o da hora anterior (m/h); positivo = enchendo. Maré horária prevista
--                      do Porto do Recife (TIDE_PE02_HOURLY).
--   river_level_m      régua do rio Duas Unas (APAC, Engenho Velho, a 556 m do ponto JABOATAO_CENTRO), média da hora, em
--                      metros (o feed vem em cm). Só vale para pontos a até 1 km da régua: hoje, só JABOATAO_CENTRO. A
--                      série começa em 2026-11-09 (não há histórico anterior no MAPi).
--   soil_moisture      umidade do solo 0-7 cm (m3/m3) do ERA5-Land na hora ANTERIOR. É REANÁLISE, não medida, e chega
--                      com semanas de atraso: ausente nas horas recentes.
CREATE VIEW point_physical_inputs_v AS
SELECT p.flood_point_id, f.slug, p.rule_version, p.hour_end_utc,
       b.rain_max_10min_mm,
       p.station_id                                   AS rain_max_10min_station_id,
       CASE WHEN b.rain_max_10min_mm IS NOT NULL THEN p.source END AS rain_max_10min_origin,
       t.value - t1.value                             AS tide_trend_m_h,
       CASE WHEN t.value IS NOT NULL AND t1.value IS NOT NULL THEN 'TIDE_PE02_HOURLY' END AS tide_trend_origin,
       r.river_level_m,
       CASE WHEN r.river_level_m IS NOT NULL THEN 'APAC_RIO_DUAS_UNAS' END AS river_level_origin,
       s.value                                        AS soil_moisture,
       CASE WHEN s.value IS NOT NULL THEN 'REANALYSIS_ERA5_LAND' END AS soil_moisture_origin
FROM point_rain_hourly p
JOIN flood_points f ON f.id = p.flood_point_id
LEFT JOIN LATERAL (
    SELECT CASE WHEN bool_and(o.ts_utc - o.interval_start_utc <= interval '10 minutes' OR o.value = 0) THEN max(o.value) END AS rain_max_10min_mm
    FROM observation_current o
    WHERE p.value IS NOT NULL AND o.source = p.source AND o.station_id = p.station_id AND o.variable = 'precipitation'
      AND o.ts_utc > p.hour_end_utc - interval '1 hour' AND o.ts_utc <= p.hour_end_utc AND o.value IS NOT NULL
) b ON true
LEFT JOIN observation_current t  ON t.source = 'TIDE_PE02_HOURLY' AND t.station_id = 'pe02'
     AND t.variable = 'tide_height_hourly' AND t.resolution = 'PT1H' AND t.ts_utc = p.hour_end_utc
LEFT JOIN observation_current t1 ON t1.source = 'TIDE_PE02_HOURLY' AND t1.station_id = 'pe02'
     AND t1.variable = 'tide_height_hourly' AND t1.resolution = 'PT1H' AND t1.ts_utc = p.hour_end_utc - interval '1 hour'
LEFT JOIN LATERAL (
    SELECT avg(d.water_level) / 100.0 AS river_level_m
    FROM sensor_data d
    WHERE d.sensor_id = 'APAC-RIO-260790119H' AND d.water_level IS NOT NULL
      AND ST_DistanceSphere(ST_MakePoint(f.longitude, f.latitude), ST_MakePoint(d.longitude, d.latitude)) <= 1000
      -- sensor_data.timestamp é hora local de Recife sem fuso (legado, ADR 0001)
      AND d.timestamp >  ((p.hour_end_utc - interval '1 hour') AT TIME ZONE 'America/Recife')
      AND d.timestamp <= (p.hour_end_utc AT TIME ZONE 'America/Recife')
) r ON true
LEFT JOIN station es ON es.source = 'OPEN_METEO_ERA5LAND' AND es.name = f.name
LEFT JOIN observation_current s ON s.source = 'OPEN_METEO_ERA5LAND' AND s.station_id = es.station_id
     AND s.variable = 'soil_moisture_0_7cm' AND s.resolution = 'PT1H' AND s.ts_utc = p.hour_end_utc - interval '1 hour';
