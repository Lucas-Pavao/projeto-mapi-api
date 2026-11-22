-- V42 — Open-Meteo com vários modelos (prompt dos horizontes, bloco B). Só adição.
-- 1. A coluna modelo na tabela da V30. As linhas existentes são do modelo padrão do previous-runs-api (best_match),
--    o que o rain_v1 usa; a chave da V30 não muda (o caminho do rain_v1 fica igual, regra de ouro do prompt).
-- 2. As rodadas por modelo vão numa tabela nova da mesma fonte (OPEN_METEO_FORECAST): a chave da V30 não tem o instante
--    da rodada, e cada rodada nova precisa de linha própria (emissão separada da validade).
-- É PREVISÃO, não medição. Fonte: Open-Meteo (ECMWF/DWD/NOAA), CC BY 4.0.

ALTER TABLE open_meteo_forecast_hour ADD COLUMN model VARCHAR(40) NOT NULL DEFAULT 'best_match';

-- product: 'run' = rodada ao vivo (api.open-meteo.com/v1/forecast); 'previous_dayN' = histórico (previous-runs-api,
-- valor previsto N x 24 h antes da hora válida).
-- issued_before_utc: a previsão estava publicada até esse instante.
--   run: last_run_availability_time do meta.json do modelo (best_match não tem rodada própria: instante da coleta);
--   previous_dayN: hour_end_utc - 24N h + 8 h (rodada iniciada até 24N h antes, publicada até ~7 h depois; margem 8 h).
-- Na montagem de qualquer entrada só vale linha com issued_before_utc <= t.
CREATE TABLE open_meteo_model_hour (
    flood_point_id    BIGINT           NOT NULL REFERENCES flood_points (id),
    model             VARCHAR(40)      NOT NULL,
    product           VARCHAR(20)      NOT NULL CHECK (product IN ('run', 'previous_day1', 'previous_day2', 'previous_day3')),
    hour_end_utc      TIMESTAMPTZ      NOT NULL,
    issued_before_utc TIMESTAMPTZ      NOT NULL,
    run_init_utc      TIMESTAMPTZ,
    precip_mm         DOUBLE PRECISION,
    cape              DOUBLE PRECISION,
    grid_lat          DOUBLE PRECISION,
    grid_lon          DOUBLE PRECISION,
    raw_hash          CHAR(64)         NOT NULL,
    fetched_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (flood_point_id, model, product, hour_end_utc, issued_before_utc)
);
CREATE INDEX idx_om_model_hour_issued ON open_meteo_model_hour (model, product, issued_before_utc DESC);
