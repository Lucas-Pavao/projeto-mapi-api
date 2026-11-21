-- Limpezas e chamados de entupimento da Central 156 casados com os pontos (docs/PRE-REGISTRO-ENTUPIMENTO-156.md).
-- Fonte: CSV anual do CKAN da Prefeitura (conjunto central-de-atendimento-de-servicos-da-emlurb-156, ODbL).
-- A base não tem id de chamado: a chave é o SHA-256 da linha (ano + campos), igual ao do script de análise.
CREATE TABLE drainage_cleaning (
    id               BIGSERIAL PRIMARY KEY,
    row_sha256       CHAR(64)    NOT NULL,
    flood_point_id   BIGINT      NOT NULL REFERENCES flood_points(id),
    group_code       INTEGER     NOT NULL,
    service_code     INTEGER     NOT NULL,
    service_desc     TEXT,
    demand_date      DATE        NOT NULL,
    last_status_date DATE,
    status           TEXT        NOT NULL,
    match_rule       TEXT        NOT NULL,          -- COORDENADA_150M | LOGRADOURO_BAIRRO
    distance_m       DOUBLE PRECISION,
    source_year      INTEGER     NOT NULL,
    ingested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_drainage_cleaning UNIQUE (row_sha256, flood_point_id)
);
CREATE INDEX ix_drainage_cleaning_done ON drainage_cleaning (flood_point_id, status, last_status_date DESC);
