-- Estação canônica e acumulados por ponto (ADR 0023, Etapa 0 do IN1165).
-- ADITIVA: só CREATE TABLE e CREATE VIEW. As três tabelas são derivadas e recalculáveis por
-- POST /api/admin/stations/unify; nenhuma linha existente é apagada ou alterada.
--
-- Os pluviômetros do Cemaden chegam por dois caminhos: a coleta ao vivo pela APAC (source CEMADEN) e a carga histórica
-- pela PED (source CEMADEN_PED). A mesma estação física aparece duas vezes em `station`. A unificação é por coordenada
-- (até 150 m); a semelhança de nome só vai para o registro.

-- 1. Uma linha por estação física. O identificador é o código da estação PED quando há uma no grupo (o Cemaden usa o
--    mesmo código nas duas rotas para 25 das 46 estações, contagem de 2026-11-15); senão, o código da coleta ao vivo.
CREATE TABLE station_canonical (
    canonical_id    VARCHAR(120) PRIMARY KEY,
    name            VARCHAR(255),
    lat             DOUBLE PRECISION NOT NULL,
    lon             DOUBLE PRECISION NOT NULL,
    municipality    VARCHAR(255),
    ibge_code       VARCHAR(7),
    primary_source  VARCHAR(40)  NOT NULL,
    n_aliases       INTEGER      NOT NULL,
    rule_version    VARCHAR(40)  NOT NULL,
    built_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. Apelidos: cada (source, station_id) de origem aponta para a canônica. A distância e a semelhança de nome ficam
--    registradas; a semelhança nunca decide sozinha.
CREATE TABLE station_alias (
    source           VARCHAR(40)  NOT NULL,
    station_id       VARCHAR(120) NOT NULL,
    canonical_id     VARCHAR(120) NOT NULL REFERENCES station_canonical (canonical_id) ON DELETE CASCADE,
    source_name      VARCHAR(255),
    distance_m       DOUBLE PRECISION NOT NULL,
    name_similarity  DOUBLE PRECISION,
    rule_version     VARCHAR(40)  NOT NULL,
    PRIMARY KEY (source, station_id),
    FOREIGN KEY (source, station_id) REFERENCES station (source, station_id)
);
CREATE INDEX idx_station_alias_canonical ON station_alias (canonical_id);

-- 3. Hora em que as duas fontes têm valor para a mesma estação canônica e diferem acima da tolerância. A série
--    canônica usa a PED (resolução de 10 min, fuso verificado na V24); a linha registra o valor descartado.
CREATE TABLE station_hour_divergence (
    canonical_id     VARCHAR(120) NOT NULL REFERENCES station_canonical (canonical_id) ON DELETE CASCADE,
    hour_end_utc     TIMESTAMPTZ  NOT NULL,
    kept_source      VARCHAR(40)  NOT NULL,
    kept_station_id  VARCHAR(120) NOT NULL,
    kept_value       DOUBLE PRECISION NOT NULL,
    other_source     VARCHAR(40)  NOT NULL,
    other_station_id VARCHAR(120) NOT NULL,
    other_value      DOUBLE PRECISION NOT NULL,
    diff_mm          DOUBLE PRECISION NOT NULL,
    tolerance_mm     DOUBLE PRECISION NOT NULL,
    rule_version     VARCHAR(40)  NOT NULL,
    PRIMARY KEY (canonical_id, hour_end_utc, other_source, other_station_id)
);

-- 4. Leitura para quem consome: a canônica com os apelidos em texto "SOURCE:station_id".
CREATE VIEW station_canonical_v AS
SELECT c.canonical_id, c.name, c.lat, c.lon, c.municipality, c.ibge_code, c.primary_source, c.rule_version,
       array_agg(a.source || ':' || a.station_id ORDER BY a.source, a.station_id) AS aliases
FROM station_canonical c
JOIN station_alias a ON a.canonical_id = c.canonical_id
GROUP BY c.canonical_id;

-- 5. Chuva horária por estação canônica: com as duas fontes na mesma hora, fica a PED. Filtrar por hora e canônica.
CREATE VIEW canonical_rain_hourly_v AS
SELECT DISTINCT ON (a.canonical_id, h.hour_end_utc)
       a.canonical_id, h.hour_end_utc, h.value, h.source, h.station_id, h.n_readings, h.blocks_covered_min
FROM observation_hourly_v h
JOIN station_alias a ON a.source = h.source AND a.station_id = h.station_id
WHERE h.variable = 'precipitation' AND h.value IS NOT NULL
ORDER BY a.canonical_id, h.hour_end_utc, (h.source = 'CEMADEN_PED') DESC, h.station_id;

-- 6. Acumulados de 1, 3, 6, 12 e 24 h da série do ponto (point_rain_hourly, ADR 0022). A tabela tem uma linha por hora,
--    inclusive as lacunas, então a janela por linhas é a janela por horas. Acumulado com qualquer hora sem valor é
--    NULL (lacuna não vira zero); `rain_<N>h_n` diz quantas horas da janela tinham valor.
CREATE VIEW point_rain_accum_v AS
SELECT p.flood_point_id, f.slug, p.rule_version, p.hour_end_utc, p.source, p.station_id, p.station_distance_m,
       p.n_valid, p.n_valid_with_value,
       p.value AS rain_1h_mm,
       CASE WHEN count(p.value) OVER w3  = 3  THEN sum(p.value) OVER w3  END AS rain_3h_mm,
       CASE WHEN count(p.value) OVER w6  = 6  THEN sum(p.value) OVER w6  END AS rain_6h_mm,
       CASE WHEN count(p.value) OVER w12 = 12 THEN sum(p.value) OVER w12 END AS rain_12h_mm,
       CASE WHEN count(p.value) OVER w24 = 24 THEN sum(p.value) OVER w24 END AS rain_24h_mm,
       count(p.value) OVER w3  AS rain_3h_n,
       count(p.value) OVER w6  AS rain_6h_n,
       count(p.value) OVER w12 AS rain_12h_n,
       count(p.value) OVER w24 AS rain_24h_n
FROM point_rain_hourly p
JOIN flood_points f ON f.id = p.flood_point_id
WINDOW w3  AS (PARTITION BY p.flood_point_id, p.rule_version ORDER BY p.hour_end_utc ROWS BETWEEN 2 PRECEDING AND CURRENT ROW),
       w6  AS (PARTITION BY p.flood_point_id, p.rule_version ORDER BY p.hour_end_utc ROWS BETWEEN 5 PRECEDING AND CURRENT ROW),
       w12 AS (PARTITION BY p.flood_point_id, p.rule_version ORDER BY p.hour_end_utc ROWS BETWEEN 11 PRECEDING AND CURRENT ROW),
       w24 AS (PARTITION BY p.flood_point_id, p.rule_version ORDER BY p.hour_end_utc ROWS BETWEEN 23 PRECEDING AND CURRENT ROW);
