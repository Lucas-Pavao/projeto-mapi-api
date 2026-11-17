-- V37 — Conferência diária do alerta (item 4.2) e relatório de atraso por dia com chuva (item 2.1). Só adição.

-- Uma linha por dia (UTC) conferido: eventos FORTE medidos, avisados antes, antecedência, falsos por evento,
-- tempo em SEM_DADO e episódios de ALAGAMENTO_PROVAVEL por ponto (guardados para cruzar com ocorrências reais).
CREATE TABLE alert_daily_check (
    day_utc               DATE         NOT NULL,
    flood_point_id        BIGINT       NOT NULL REFERENCES flood_points (id),
    forte_events          INTEGER      NOT NULL,             -- cruzamentos de 25,15 mm em 60 min medidos no ponto
    forte_warned          INTEGER      NOT NULL,             -- com rain_alert ligado antes do cruzamento (até 60 min)
    lead_minutes          JSONB,                             -- antecedência de cada evento (0 = sem aviso)
    alert_episodes        INTEGER      NOT NULL,             -- episódios de rain_alert ligado
    false_episodes        INTEGER      NOT NULL,             -- episódios sem cruzamento até 60 min depois do fim
    sem_dado_minutes      DOUBLE PRECISION NOT NULL,
    evaluated_minutes     DOUBLE PRECISION NOT NULL,
    flood_episodes        JSONB,                             -- [{inicio, fim, lamina_max_cm}] de point_flood = PROVAVEL
    shadow                BOOLEAN      NOT NULL,
    checked_at            TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (day_utc, flood_point_id)
);

-- Relatório de atraso gerado sozinho no primeiro dia com chuva acima de 10 mm em algum ponto (item 2.1).
CREATE TABLE alert_latency_report (
    day_utc               DATE         PRIMARY KEY,
    max_point_rain_mm     DOUBLE PRECISION NOT NULL,
    report                JSONB        NOT NULL,             -- mediana, p90 e máximo por trecho, hora com chuva e seca
    within_budget_wet     BOOLEAN,
    generated_at          TIMESTAMPTZ  NOT NULL
);
