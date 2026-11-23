-- V47 — Alerta único por ponto (ADR 0030, onda 16, Fase 2). Só adição e relaxamento de CHECK; nada é apagado.
-- 1) flood_forecast_point (V38) grava os fatores por ponto: modo de cada um, efeito da maré na lâmina do cenário e do
--    horizonte servidos, chance de a galeria estar entupida e o nível no cenário de viés do MDT. A resposta inteira,
--    com as contribuições, continua em payload.
-- 2) O alerta do ADR 0026 deixa de gravar nível de alagamento: point_flood ganha o valor DESLIGADO (sem lâmina).

ALTER TABLE flood_forecast_point
    ADD COLUMN refluxo_mode         VARCHAR(5),          -- OFF | INFO | NIVEL (nulo antes do ADR 0030)
    ADD COLUMN limpeza_mode         VARCHAR(5),
    ADD COLUMN refluxo_delta_cm     DOUBLE PRECISION,    -- lâmina com maré menos sem maré (cenário e horizonte servidos)
    ADD COLUMN limpeza_p_entupida   DOUBLE PRECISION,    -- P(galeria entupida) usada no ciclo
    ADD COLUMN bias_scenario_level  VARCHAR(12);         -- nível com o viés do MDT de -0,369 m (só sensibilidade)

ALTER TABLE alert_point_state DROP CONSTRAINT alert_point_state_flood;
ALTER TABLE alert_point_state ADD CONSTRAINT alert_point_state_flood
    CHECK (point_flood IN ('NAO', 'PROVAVEL', 'SEM_DADO', 'DESLIGADO'));
