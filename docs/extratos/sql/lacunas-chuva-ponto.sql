\pset format unaligned
\pset fieldsep '|'
\echo == por ponto e ano
SELECT f.slug, extract(year from p.hour_end_utc)::int ano, count(*) horas, count(*) FILTER (WHERE p.value IS NULL) lacunas,
       round(100.0*count(*) FILTER (WHERE p.value IS NULL)/count(*),2) pct
FROM point_rain_hourly p JOIN flood_points f ON f.id=p.flood_point_id
WHERE p.rule_version='chuva-ponto-v4' GROUP BY 1,2 ORDER BY 1,2;
\echo == total por ponto
SELECT f.slug, count(*) horas, count(*) FILTER (WHERE p.value IS NULL) lacunas, round(100.0*count(*) FILTER (WHERE p.value IS NULL)/count(*),2) pct,
       min(p.hour_end_utc) de, max(p.hour_end_utc) ate
FROM point_rain_hourly p JOIN flood_points f ON f.id=p.flood_point_id WHERE p.rule_version='chuva-ponto-v4' GROUP BY 1 ORDER BY 1;
\echo == duracao das lacunas
WITH s AS (SELECT flood_point_id, hour_end_utc, value IS NULL g,
             hour_end_utc - (row_number() OVER (PARTITION BY flood_point_id, (value IS NULL) ORDER BY hour_end_utc)) * interval '1 hour' grp
           FROM point_rain_hourly WHERE rule_version='chuva-ponto-v4'),
runs AS (SELECT flood_point_id, grp, count(*) len, min(hour_end_utc) ini FROM s WHERE g GROUP BY 1,2)
SELECT f.slug, CASE WHEN len=1 THEN '1 h' WHEN len<=6 THEN '2-6 h' WHEN len<=24 THEN '7-24 h' ELSE '>24 h' END faixa,
       count(*) n_lacunas, sum(len) horas, max(len) maior
FROM runs r JOIN flood_points f ON f.id=r.flood_point_id GROUP BY 1,2 ORDER BY 1,2;
\echo == dependencia da chuva: lacuna na hora t x chuva da hora anterior com valor (t-1)
WITH s AS (SELECT flood_point_id, hour_end_utc, value, lag(value) OVER (PARTITION BY flood_point_id ORDER BY hour_end_utc) prev
           FROM point_rain_hourly WHERE rule_version='chuva-ponto-v4')
SELECT CASE WHEN prev=0 THEN '0 seco' WHEN prev<1 THEN '1 (0,1)' WHEN prev<5 THEN '2 [1,5)' WHEN prev<10 THEN '3 [5,10)' ELSE '4 >=10' END chuva_hora_anterior,
       count(*) horas, count(*) FILTER (WHERE value IS NULL) viram_lacuna, round(100.0*count(*) FILTER (WHERE value IS NULL)/count(*),3) pct
FROM s WHERE prev IS NOT NULL GROUP BY 1 ORDER BY 1;

CREATE TEMP TABLE g AS
WITH s AS (SELECT p.flood_point_id, f.name, p.hour_end_utc, p.value,
             p.hour_end_utc - (row_number() OVER (PARTITION BY p.flood_point_id, (p.value IS NULL) ORDER BY p.hour_end_utc)) * interval '1 hour' grp
           FROM point_rain_hourly p JOIN flood_points f ON f.id=p.flood_point_id WHERE p.rule_version='chuva-ponto-v4'),
l AS (SELECT s.*, count(*) OVER (PARTITION BY flood_point_id, grp, (value IS NULL)) len FROM s)
SELECT l.name, l.hour_end_utc, l.value, CASE WHEN l.value IS NOT NULL THEN 'com valor' WHEN len<=24 THEN 'lacuna <=24 h' ELSE 'lacuna >24 h' END tipo, o.value rean
FROM l JOIN station st ON st.source='OPEN_METEO_ARCHIVE' AND st.name=l.name
JOIN observation o ON o.source='OPEN_METEO_ARCHIVE' AND o.station_id=st.station_id AND o.variable='precipitation' AND o.ts_utc=l.hour_end_utc;
\echo == chuva de reanalise (Open-Meteo, mesma hora) por tipo de hora
SELECT tipo, count(*) horas, round(100.0*count(*) FILTER (WHERE rean>0)/count(*),1) pct_com_chuva,
       round(100.0*count(*) FILTER (WHERE rean>=1)/count(*),2) pct_ge1mm, round(100.0*count(*) FILTER (WHERE rean>=5)/count(*),2) pct_ge5mm,
       round(avg(rean)::numeric,3) media_mm
FROM g GROUP BY 1 ORDER BY 1;
\echo == taxa de lacuna curta por faixa de chuva de reanalise
SELECT CASE WHEN rean=0 THEN '0 seco' WHEN rean<1 THEN '1 (0,1)' WHEN rean<5 THEN '2 [1,5)' ELSE '3 >=5' END faixa, count(*) horas,
       count(*) FILTER (WHERE tipo='lacuna <=24 h') lac_curta, round(100.0*count(*) FILTER (WHERE tipo='lacuna <=24 h')/count(*),3) pct
FROM g WHERE tipo <> 'lacuna >24 h' GROUP BY 1 ORDER BY 1;
\echo == lacunas simultaneas: horas de lacuna curta em quantos pontos ao mesmo tempo
SELECT n_pontos, count(*) horas FROM (SELECT hour_end_utc, count(*) n_pontos FROM g WHERE tipo='lacuna <=24 h' GROUP BY 1) x GROUP BY 1 ORDER BY 1;
