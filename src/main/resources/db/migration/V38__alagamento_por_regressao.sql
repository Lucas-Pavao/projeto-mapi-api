-- V38 — Alerta de alagamento por regressão (prompt de 20/11/2026): chuva prevista pelo rain_v1 -> balde M1a a 10 min ->
-- regra fixa de 15 cm, calculado pela MAPI AI em POST /v1/flood/forecast a cada ciclo do alerta por ponto (ADR 0026).
-- Só adição: nenhuma tabela existente é alterada, e o histórico de flood_predictions (classificador retirado) fica.
-- Todo instante em UTC (ADR 0001).

CREATE TABLE flood_forecast_point (
    id                     BIGSERIAL    PRIMARY KEY,
    cycle_id               UUID         NOT NULL,              -- alert_cycle.id do ciclo que avaliou
    flood_point_id         BIGINT       NOT NULL REFERENCES flood_points (id),
    evaluated_at           TIMESTAMPTZ  NOT NULL,              -- relógio do ciclo (injetado; a reprise usa tempo simulado)
    issued_at              TIMESTAMPTZ,                        -- instante t da previsão (nulo em SEM_DADO sem previsão)
    level                  VARCHAR(12)  NOT NULL,              -- ALERTA | ATENCAO | SEM_ALERTA | SEM_DADO
    worst_horizon_hours    SMALLINT,
    reason                 VARCHAR(30),                        -- motivo do SEM_DADO; nulo nos outros níveis
    h_now_cm               DOUBLE PRECISION,
    flood_version          VARCHAR(40),
    rain_model_version     VARCHAR(40),
    bucket_version         VARCHAR(40),
    code_version           VARCHAR(64),                        -- commit da MAPI AI que respondeu
    payload                JSONB        NOT NULL,              -- resposta inteira da IA (horizontes, explicação, decomposição)
    rain_station           VARCHAR(120),                       -- estação canônica da leitura mais nova (station_canonical, V25)
    rain_station_km        DOUBLE PRECISION,
    shadow                 BOOLEAN      NOT NULL
);
CREATE INDEX flood_forecast_point_latest ON flood_forecast_point (flood_point_id, evaluated_at DESC);
CREATE INDEX flood_forecast_point_cycle ON flood_forecast_point (cycle_id);
