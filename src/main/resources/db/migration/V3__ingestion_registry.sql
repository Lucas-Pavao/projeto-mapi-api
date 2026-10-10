-- ADR 0006 (aprovado), passo V3: registro do pipeline de ingestão. ADITIVA: só cria tabelas novas;
-- nenhuma tabela existente é tocada. Nomes em inglês snake_case; tempos sempre timestamptz (UTC, ADR 0001).

-- Uma linha por execução do pipeline (seção 2 do ADR).
CREATE TABLE ingestion_run (
    id                UUID PRIMARY KEY,
    source            VARCHAR(40)  NOT NULL,
    kind              VARCHAR(10)  NOT NULL CHECK (kind IN ('LIVE', 'BACKFILL', 'REPROCESS')),
    mode              VARCHAR(10)  CHECK (mode IN ('REFETCH', 'REVALIDATE')),
    params            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    interval_start    TIMESTAMPTZ,
    interval_end      TIMESTAMPTZ,
    code_version      VARCHAR(200) NOT NULL,
    parser_version    VARCHAR(40)  NOT NULL,
    rule_version      VARCHAR(40)  NOT NULL,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ,
    status            VARCHAR(10)  NOT NULL CHECK (status IN ('RUNNING', 'OK', 'PARTIAL', 'FAILED')),
    fetched           INTEGER NOT NULL DEFAULT 0,
    batch_deduped     INTEGER NOT NULL DEFAULT 0,
    raw_inserted      INTEGER NOT NULL DEFAULT 0,
    raw_unchanged     INTEGER NOT NULL DEFAULT 0,
    valid             INTEGER NOT NULL DEFAULT 0,
    quarantined       INTEGER NOT NULL DEFAULT 0,
    gaps              INTEGER NOT NULL DEFAULT 0,
    obs_inserted      INTEGER NOT NULL DEFAULT 0,
    obs_updated       INTEGER NOT NULL DEFAULT 0,
    obs_unchanged     INTEGER NOT NULL DEFAULT 0,
    obs_retracted     INTEGER NOT NULL DEFAULT 0,
    absent_in_refetch INTEGER NOT NULL DEFAULT 0,
    parent_run_id     UUID REFERENCES ingestion_run (id),
    error_summary     TEXT
);
CREATE INDEX idx_ingestion_run_source_started ON ingestion_run (source, started_at DESC);

-- Estação DA FONTE (nunca o slug do ponto). Open-Meteo: a "estação" é a coordenada solicitada.
CREATE TABLE station (
    source       VARCHAR(40)  NOT NULL,
    station_id   VARCHAR(120) NOT NULL,
    name         VARCHAR(255),
    lat          DOUBLE PRECISION,
    lon          DOUBLE PRECISION,
    municipality VARCHAR(255),
    attrs        JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (source, station_id)
);

-- Fuso e estado de verificação por fonte. A VALIDAÇÃO lê o fuso efetivo daqui (ADR 0006 seção 4);
-- o parser só declara o fuso que gravou no bruto. UNVERIFIED => quarentena TZ_UNVERIFIED.
CREATE TABLE source_registry (
    source             VARCHAR(40) PRIMARY KEY,
    source_tz          VARCHAR(40) NOT NULL,
    verification_state VARCHAR(12) NOT NULL CHECK (verification_state IN ('VERIFIED', 'DECLARED', 'UNVERIFIED')),
    note               TEXT,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO source_registry (source, source_tz, verification_state, note) VALUES
    ('OPEN_METEO_ARCHIVE', 'UTC',            'VERIFIED',   'timezone=GMT explícito e utc_offset_seconds=0 conferido a cada resposta'),
    ('OPEN_METEO_CURRENT', 'UTC',            'DECLARED',   'coletor atual ainda usa timezone=auto; só entra no pipeline após troca por GMT (pendente)'),
    ('OPEN_METEO_MARINE',  'UTC',            'DECLARED',   'idem OPEN_METEO_CURRENT'),
    ('APAC_PORTAL',        'America/Recife', 'DECLARED',   'dia civil local (ADR 0006 seção 4)'),
    ('APAC_METEO24H',      'UNVERIFIED',     'UNVERIFIED', 'bloqueante: F2-DADOS-07'),
    ('CEMADEN',            'UNVERIFIED',     'UNVERIFIED', 'bloqueante: F2-DADOS-07'),
    ('ANA_SOAP',           'UNVERIFIED',     'UNVERIFIED', 'bloqueante: F2-DADOS-07'),
    ('TABUA_MARE',         'UNVERIFIED',     'UNVERIFIED', 'ler tide_tables.timezone (a consultar no banco)'),
    ('CKAN_RECIFE',        'America/Recife', 'DECLARED',   'data sem hora = dia local (precisão DAY)');

-- Catálogo de variáveis: unidade canônica, faixa física, convenção de intervalo e agregação horária.
-- ATENÇÃO: as faixas abaixo são limites CONSERVADORES de plausibilidade física declarados pelo
-- backend, todos [sem fonte] (range_source) até serem preenchidos com norma da agência ou literatura.
CREATE TABLE variable_catalog (
    variable            VARCHAR(40) PRIMARY KEY,
    canonical_unit      VARCHAR(20) NOT NULL,
    min_value           DOUBLE PRECISION,
    max_value           DOUBLE PRECISION,
    range_source        VARCHAR(200) NOT NULL DEFAULT '[sem fonte]',
    expected_resolution VARCHAR(10),
    interval_convention VARCHAR(10) NOT NULL CHECK (interval_convention IN ('END', 'INSTANT')),
    hourly_aggregation  VARCHAR(5)  NOT NULL CHECK (hourly_aggregation IN ('SUM', 'LAST', 'NONE')),
    min_completeness    DOUBLE PRECISION NOT NULL DEFAULT 0.8
);

INSERT INTO variable_catalog
    (variable, canonical_unit, min_value, max_value, expected_resolution, interval_convention, hourly_aggregation) VALUES
    ('precipitation',             'mm',       0,   300,  'PT1H',  'END',     'SUM'),
    ('temperature',               '°C',       -10, 60,   'PT1H',  'INSTANT', 'LAST'),
    ('humidity',                  '%',        0,   100,  'PT1H',  'INSTANT', 'LAST'),
    ('pressure',                  'hPa',      800, 1100, 'PT1H',  'INSTANT', 'LAST'),
    ('weather_code',              'wmo code', 0,   99,   'PT1H',  'INSTANT', 'LAST'),
    ('wind_speed',                'km/h',     0,   400,  'PT1H',  'INSTANT', 'LAST'),
    ('solar_radiation',           'W/m²',     0,   1500, 'PT1H',  'END',     'LAST'),
    ('water_level',               'm',        -5,  50,   NULL,    'INSTANT', 'LAST'),
    ('tide_height',               'm',        -2,  5,    'EVENT', 'INSTANT', 'NONE'),
    ('precipitation_daily_total', 'mm',       0,   1000, 'P1D',   'END',     'NONE');
