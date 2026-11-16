-- Requer a tabela temporária 'old' com flood_occurrence de um dump anterior:
--   pg_restore -a -t flood_occurrence -f - <dump> | (trocar 'COPY public.flood_occurrence' por 'COPY old')
\pset format unaligned
\echo == mudanca de coordenada entre tide_db 08/11 (pre-recarga) e o banco atual
SELECT count(*) pares, count(*) FILTER (WHERE d>50) gt50m, count(*) FILTER (WHERE d>500) gt500m FROM (
 SELECT ST_DistanceSphere(ST_MakePoint(o.geocoded_lon,o.geocoded_lat), ST_MakePoint(n.geocoded_lon,n.geocoded_lat)) d
 FROM old o JOIN flood_occurrence n USING (ckan_resource_id, ckan_record_id)) x;
\echo == casamento por raio: conjunto | raio | pares ocorrencia-ponto | ponto-dia | dias | pontos
WITH pts AS (SELECT slug, latitude, longitude FROM flood_points WHERE active),
sets AS (SELECT 'tide_db 2026-11-08 pre-recarga' s, ckan_record_id, occurred_from, geocoded_lat la, geocoded_lon lo FROM old
         UNION ALL SELECT 'mapi_db atual (2026-11-16)', ckan_record_id, occurred_from, geocoded_lat, geocoded_lon FROM flood_occurrence),
m AS (SELECT s.s, r.r, p.slug, s.ckan_record_id, (s.occurred_from AT TIME ZONE 'America/Recife')::date dia
      FROM sets s CROSS JOIN pts p CROSS JOIN (VALUES (500),(1000),(2000),(3000)) r(r)
      WHERE s.la IS NOT NULL AND ST_DistanceSphere(ST_MakePoint(s.lo,s.la), ST_MakePoint(p.longitude,p.latitude)) <= r.r)
SELECT s, r, count(*) pares, count(DISTINCT (slug, dia)) ponto_dia, count(DISTINCT dia) dias, count(DISTINCT slug) pontos FROM m GROUP BY 1,2 ORDER BY 1,2;
\echo == enderecos distintos e conflitos (mesmo endereco+bairro, coordenadas diferentes) no banco atual
SELECT count(*) chaves, count(*) FILTER (WHERE n>1) com_conflito FROM (SELECT address_text, neighborhood, count(DISTINCT (round(geocoded_lat::numeric,5), round(geocoded_lon::numeric,5))) n FROM flood_occurrence GROUP BY 1,2) x;
