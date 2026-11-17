-- V34 — Alerta de chuva por ponto (ADR 0026): estado atual, histórico de transições e medição de atraso por ciclo.
-- Só adição. Nenhum dado existente é alterado ou apagado. Todo instante em UTC (ADR 0001).

-- Ciclo do alerta: um por disparo (coleta ao vivo da APAC/Cemaden, a cada 3 min). Os instantes medem o atraso (item 2.1).
CREATE TABLE alert_cycle (
    id                    UUID         PRIMARY KEY,
    origin                VARCHAR(40)  NOT NULL,               -- coleta que disparou ou REPRISE
    started_at            TIMESTAMPTZ  NOT NULL,               -- 4. início do ciclo
    finished_at           TIMESTAMPTZ,
    sse_sent_at           TIMESTAMPTZ,                         -- 7. envio pelo SSE (do último ponto)
    n_points              INTEGER,
    duration_ms           INTEGER,
    bucket_ms_total       DOUBLE PRECISION,                    -- soma do tempo do balde na IA
    error                 TEXT
);
CREATE INDEX alert_cycle_started ON alert_cycle (started_at DESC);

-- Estado atual por ponto: uma linha por ponto, atualizada a cada ciclo.
CREATE TABLE alert_point_state (
    flood_point_id        BIGINT       PRIMARY KEY REFERENCES flood_points (id),
    rain_alert            VARCHAR(10)  NOT NULL,
    rain_reason           VARCHAR(30),
    point_flood           VARCHAR(10)  NOT NULL,
    flood_reason          VARCHAR(30),
    rain_since            TIMESTAMPTZ  NOT NULL,
    last_renewed_at       TIMESTAMPTZ,
    event_id              UUID,
    event_last_active_at  TIMESTAMPTZ,
    trigger               TEXT,
    prob_forte_1h         DOUBLE PRECISION,
    prob_on               DOUBLE PRECISION,
    prob_off              DOUBLE PRECISION,
    rain_te_mm            DOUBLE PRECISION,                    -- chuva medida na duração Te (10 min)
    capacity_te_mm        DOUBLE PRECISION,                    -- i(TR, Te) x Te / 60
    rain_30min_mm         DOUBLE PRECISION,
    h_now_mm              DOUBLE PRECISION,
    h_proj_q50_max_mm     DOUBLE PRECISION,
    h_proj_q80_max_mm     DOUBLE PRECISION,
    flood_in_minutes      INTEGER,
    projection            JSONB,                               -- lâmina por passo de 10 min, q50 e q80
    q50_1h_mm             DOUBLE PRECISION,
    q80_1h_mm             DOUBLE PRECISION,
    rain_model_version    VARCHAR(40),
    bucket_version        VARCHAR(40),
    tr_years              DOUBLE PRECISION,
    te_minutes            DOUBLE PRECISION,
    sources               JSONB,
    latest_reading_utc    TIMESTAMPTZ,
    latest_reading_age_min DOUBLE PRECISION,
    shadow                BOOLEAN      NOT NULL DEFAULT true,
    cycle_id              UUID         REFERENCES alert_cycle (id),
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT alert_point_state_rain CHECK (rain_alert IN ('NENHUM', 'ATENCAO', 'ALERTA', 'SEM_DADO')),
    CONSTRAINT alert_point_state_flood CHECK (point_flood IN ('NAO', 'PROVAVEL', 'SEM_DADO')),
    CONSTRAINT alert_point_state_motivo CHECK ((rain_alert <> 'SEM_DADO' OR rain_reason IS NOT NULL)
                                           AND (point_flood <> 'SEM_DADO' OR flood_reason IS NOT NULL))
);

-- Uma linha por ponto e ciclo: o que o alerta viu (reprodutível a partir do banco) e os instantes do caminho.
CREATE TABLE alert_point_cycle (
    cycle_id              UUID         NOT NULL REFERENCES alert_cycle (id),
    flood_point_id        BIGINT       NOT NULL REFERENCES flood_points (id),
    evaluated_at          TIMESTAMPTZ  NOT NULL,               -- relógio do ciclo (simulado na reprise)
    rain_alert            VARCHAR(10)  NOT NULL,
    rain_reason           VARCHAR(30),
    point_flood           VARCHAR(10)  NOT NULL,
    flood_reason          VARCHAR(30),
    prob_forte_1h         DOUBLE PRECISION,
    rain_te_mm            DOUBLE PRECISION,
    h_now_mm              DOUBLE PRECISION,
    h_proj_q50_max_mm     DOUBLE PRECISION,
    flood_in_minutes      INTEGER,
    event_id              UUID,
    reading_ts            TIMESTAMPTZ,                         -- 1. leitura mais nova na estação
    reading_received_at   TIMESTAMPTZ,                         -- 2. coleta recebeu
    reading_ingested_at   TIMESTAMPTZ,                         -- 3. entrou no banco
    cycle_started_at      TIMESTAMPTZ,                         -- 4. início do ciclo
    ai_responded_at       TIMESTAMPTZ,                         -- 5. resposta da IA (previsão e balde)
    state_written_at      TIMESTAMPTZ,                         -- 6. estado gravado
    sse_sent_at           TIMESTAMPTZ,                         -- 7. envio pelo SSE
    wet_hour              BOOLEAN,                             -- hora com chuva (>= 0,2 mm nos 60 min antes)
    bucket_ms             DOUBLE PRECISION,
    shadow                BOOLEAN      NOT NULL,
    PRIMARY KEY (cycle_id, flood_point_id)
);
CREATE INDEX alert_point_cycle_eval ON alert_point_cycle (flood_point_id, evaluated_at DESC);

-- Histórico de transições: liga, sobe, desce, desliga, cancela, expira, SEM_DADO, volta do dado.
CREATE TABLE alert_transition (
    id                    BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    flood_point_id        BIGINT       NOT NULL REFERENCES flood_points (id),
    at                    TIMESTAMPTZ  NOT NULL,
    field                 VARCHAR(5)   NOT NULL,               -- RAIN | FLOOD
    from_state            VARCHAR(10),
    to_state              VARCHAR(10)  NOT NULL,
    kind                  VARCHAR(12)  NOT NULL,
    reason                VARCHAR(30),
    event_id              UUID,
    prob_forte_1h         DOUBLE PRECISION,
    rain_te_mm            DOUBLE PRECISION,
    h_now_mm              DOUBLE PRECISION,
    h_proj_q50_max_mm     DOUBLE PRECISION,
    flood_in_minutes      INTEGER,
    shadow                BOOLEAN      NOT NULL,
    cycle_id              UUID         REFERENCES alert_cycle (id),
    CONSTRAINT alert_transition_field CHECK (field IN ('RAIN', 'FLOOD'))
);
CREATE INDEX alert_transition_point_at ON alert_transition (flood_point_id, at DESC);
CREATE INDEX alert_transition_event ON alert_transition (event_id);

-- Leituras retiradas pelo controle de qualidade ao vivo (item 2.4), como a quarentena: com motivo, nada apagado.
CREATE TABLE live_qc_drop (
    canonical_id          VARCHAR(40)  NOT NULL,
    ts_utc                TIMESTAMPTZ  NOT NULL,
    mm                    DOUBLE PRECISION NOT NULL,
    reason                VARCHAR(40)  NOT NULL,
    first_seen_at         TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (canonical_id, ts_utc, reason)
);

-- A previsão de alagamento continua; esta fonte só registra a idade da IA para o alarme de fonte velha.
INSERT INTO source_registry (source, source_tz, verification_state, max_age_minutes, note)
VALUES ('MAPI_ALERTA', 'UTC', 'DECLARED', 15,
        'Ciclo do alerta de chuva por ponto (ADR 0026): idade do último ciclo com previsão da IA. Alarme de fonte velha do caminho do alerta.')
ON CONFLICT (source) DO NOTHING;
