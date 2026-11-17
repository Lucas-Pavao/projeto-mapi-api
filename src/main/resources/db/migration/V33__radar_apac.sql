-- V33 — Gravação das imagens do radar da APAC (Chã Grande): manifesto no banco, arquivo em volume próprio.
-- Só adição. Nada existente é alterado ou apagado.
-- Fuso: o nome do arquivo (AAAAMMDDHHMM) está em UTC, verificado em 2026-11-17 contra o serviço do painel da APAC
-- (ServicoMonitoramentoRADAR.php devolve data_hora_utc = 202611171105 e data_hora_local = 08:05 Hora Local).
-- Termo de uso pendente: gravar e não publicar derivado (docs/extratos/radar-apac.md).

INSERT INTO source_registry (source, source_tz, verification_state, max_age_minutes, note)
VALUES (
    'APAC_RADAR',
    'UTC',
    'VERIFIED',
    30,
    'Imagens do radar da APAC (barramento2.apac.pe.gov.br/imagens_radar). CAPPI 1 km e acumulado de 1 h, a cada 10 min. Só gravação: não entra em modelo nem é publicado (termo de uso pendente).'
)
ON CONFLICT (source) DO NOTHING;

CREATE TABLE radar_image (
    product        VARCHAR(20)  NOT NULL,           -- cappi1km | Acum1h
    ts_utc         TIMESTAMPTZ  NOT NULL,           -- instante do nome do arquivo (UTC)
    status         VARCHAR(10)  NOT NULL,           -- OK | ABSENT (404 ou fora da listagem) | ERROR
    file_name      VARCHAR(80)  NOT NULL,
    rel_path       VARCHAR(160),                    -- relativo ao volume do radar; NULL se não gravado
    sha256         CHAR(64),
    size_bytes     INTEGER,
    http_status    INTEGER,
    fetched_at     TIMESTAMPTZ  NOT NULL,
    note           TEXT,
    PRIMARY KEY (product, ts_utc),
    CONSTRAINT radar_image_status CHECK (status IN ('OK', 'ABSENT', 'ERROR')),
    CONSTRAINT radar_image_ok_has_file CHECK (status <> 'OK' OR (rel_path IS NOT NULL AND sha256 IS NOT NULL))
);

CREATE INDEX radar_image_ts ON radar_image (ts_utc DESC);
