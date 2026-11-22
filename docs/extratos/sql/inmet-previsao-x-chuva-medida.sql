-- Previsão e avisos do INMET × chuva medida no ponto (prompt dos horizontes, bloco A3).
-- Pronta para rodar ao fim da próxima quadra chuvosa, quando houver previsão arquivada (V41, desde 22/11/2026) e
-- avisos arquivados (V31, desde 16/11/2026). Só leitura.
--
-- Regras:
-- * só vale o que o MAPi viu ANTES do início do turno (first_seen_at <= início): nada visto depois entra;
-- * chuva medida = série do ponto (point_rain_hourly, ADR 0022), hora cheia de relógio; hora sem valor fica NULL
--   (lacuna não vira zero) e o turno com lacuna é marcado;
-- * evento FORTE = hora com chuva do ponto >= 25,15 mm (ADR 0017). Aqui é hora de relógio, não janela móvel de 60 min:
--   pode perder um evento que cruza a virada da hora (aproximação declarada);
-- * horário dos turnos do INMET [não verificado: a API não publica]: manhã 06-12h, tarde 12-18h, noite 18-24h, hora de
--   Recife (America/Recife = horário de Brasília desde 2019).
-- * point_rain_hourly guarda uma série por versão da regra (rule_version): vale a versão construída por último.
-- DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC · INMET — Instituto Nacional de Meteorologia.

-- 1. Previsão por turno × chuva medida no mesmo turno ----------------------------------------------------------------
WITH regra AS (SELECT rule_version FROM point_rain_hourly GROUP BY 1 ORDER BY max(built_at) DESC LIMIT 1),
turno AS (
    SELECT f.ibge, f.forecast_date, f.period,
           (f.forecast_date + CASE f.period WHEN 'MANHA' THEN time '06:00' WHEN 'TARDE' THEN time '12:00' ELSE time '18:00' END)
               AT TIME ZONE 'America/Recife' AS inicio,
           (f.forecast_date + CASE f.period WHEN 'MANHA' THEN time '12:00' WHEN 'TARDE' THEN time '18:00' ELSE time '00:00' END
               + CASE f.period WHEN 'NOITE' THEN interval '1 day' ELSE interval '0' END) AT TIME ZONE 'America/Recife' AS fim
    FROM official_forecast f
    WHERE f.source = 'INMET_PREVISAO' AND f.period IN ('MANHA', 'TARDE', 'NOITE')
    GROUP BY f.ibge, f.forecast_date, f.period
),
vigente AS (   -- a última versão vista até o início do turno
    SELECT DISTINCT ON (t.ibge, t.forecast_date, t.period) t.*, f.summary, f.icon_code, f.first_seen_at,
           (f.summary ILIKE '%pancada%' OR f.summary ILIKE '%trovoada%' OR f.summary ILIKE '%chuva forte%') AS texto_forte,
           (f.summary ILIKE '%chuva%' OR f.summary ILIKE '%pancada%' OR f.summary ILIKE '%chuvisco%') AS texto_chuva
    FROM turno t
    JOIN official_forecast f ON f.ibge = t.ibge AND f.forecast_date = t.forecast_date AND f.period = t.period
    WHERE f.first_seen_at <= t.inicio
    ORDER BY t.ibge, t.forecast_date, t.period, f.first_seen_at DESC
),
ponto AS (     -- pontos ativos por município (o cadastro guarda o nome, não o IBGE)
    SELECT id, slug, CASE unaccent_upper WHEN 'RECIFE' THEN '2611606' WHEN 'JABOATAO DOS GUARARAPES' THEN '2607901'
                                         WHEN 'OLINDA' THEN '2609600' END AS ibge
    FROM (SELECT id, slug, upper(translate(municipality, 'ãâáàéêíóôõúçÃÂÁÀÉÊÍÓÔÕÚÇ', 'aaaaeeiooouCAAAAEEIOOOUC')) AS unaccent_upper
          FROM flood_points WHERE active) x
),
medida AS (
    SELECT v.ibge, v.forecast_date, v.period, p.slug,
           max(h.value) AS max_1h_mm,
           count(*) FILTER (WHERE h.value IS NULL) AS horas_sem_valor,
           bool_or(h.value >= 25.15) AS forte
    FROM vigente v
    JOIN ponto p ON p.ibge = v.ibge
    LEFT JOIN point_rain_hourly h ON h.flood_point_id = p.id AND h.rule_version = (SELECT rule_version FROM regra)
                                 AND h.hour_end_utc > v.inicio AND h.hour_end_utc <= v.fim
    GROUP BY v.ibge, v.forecast_date, v.period, p.slug
)
SELECT v.ibge, v.forecast_date, v.period, v.summary, v.texto_chuva, v.texto_forte, v.first_seen_at AS visto_em,
       m.slug, m.max_1h_mm, m.horas_sem_valor, m.forte
FROM vigente v JOIN medida m USING (ibge, forecast_date, period)
ORDER BY v.forecast_date, v.period, v.ibge, m.slug;

-- Resumo para a avaliação (troque o SELECT final acima por este): turnos com evento FORTE em algum ponto do município,
-- e quantos tinham texto de chuva forte; turnos com texto de chuva forte e sem evento (aviso falso).
-- SELECT texto_forte, bool_or(forte) AS teve_forte, count(DISTINCT (ibge, forecast_date, period)) AS turnos
-- FROM vigente JOIN medida USING (ibge, forecast_date, period) WHERE horas_sem_valor = 0 GROUP BY 1, 2;

-- 2. Avisos do INMET × eventos FORTE ----------------------------------------------------------------------------------
-- Para cada hora com evento FORTE num ponto: havia aviso do INMET vigente para o município, visto pelo MAPi antes da
-- hora começar? E com que antecedência (do first_seen_at ao fim da hora)?
WITH regra AS (SELECT rule_version FROM point_rain_hourly GROUP BY 1 ORDER BY max(built_at) DESC LIMIT 1),
ponto AS (
    SELECT id, slug, CASE upper(translate(municipality, 'ãâáàéêíóôõúçÃÂÁÀÉÊÍÓÔÕÚÇ', 'aaaaeeiooouCAAAAEEIOOOUC'))
                         WHEN 'RECIFE' THEN '2611606' WHEN 'JABOATAO DOS GUARARAPES' THEN '2607901'
                         WHEN 'OLINDA' THEN '2609600' END AS ibge
    FROM flood_points WHERE active
),
forte AS (
    SELECT p.slug, p.ibge, h.hour_end_utc, h.value
    FROM point_rain_hourly h JOIN ponto p ON p.id = h.flood_point_id
    WHERE h.value >= 25.15 AND h.hour_end_utc >= timestamptz '2026-11-16 00:00:00+00'
      AND h.rule_version = (SELECT rule_version FROM regra)
)
SELECT f.slug, f.hour_end_utc, f.value AS chuva_mm,
       w.warning_id, w.severity, w.first_seen_at,
       round(extract(epoch FROM (f.hour_end_utc - w.first_seen_at)) / 60) AS antecedencia_min
FROM forte f
LEFT JOIN LATERAL (
    SELECT * FROM official_warning w
    WHERE f.ibge = ANY (w.geocodes) AND w.valid_from < f.hour_end_utc AND w.valid_to > f.hour_end_utc - interval '1 hour'
      AND w.first_seen_at <= f.hour_end_utc - interval '1 hour'
    ORDER BY w.first_seen_at LIMIT 1
) w ON true
ORDER BY f.hour_end_utc, f.slug;

-- E o contrário (avisos falsos): avisos vigentes para o município sem nenhuma hora FORTE nos pontos durante a vigência.
-- SELECT w.warning_id, w.sequence_id, w.severity, w.valid_from, w.valid_to,
--        bool_or(h.value >= 25.15) AS teve_forte, count(h.value) AS horas_com_valor
-- FROM official_warning w JOIN ponto p ON p.ibge = ANY (w.geocodes)
-- LEFT JOIN point_rain_hourly h ON h.flood_point_id = p.id AND h.hour_end_utc > w.valid_from AND h.hour_end_utc <= w.valid_to
-- GROUP BY 1, 2, 3, 4, 5;
