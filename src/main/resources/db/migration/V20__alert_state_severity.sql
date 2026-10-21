-- Decisão 46: a escalada dentro da condição (ex.: MEDIUM -> HIGH numa regra com minRisk MEDIUM) gera alerta novo.
-- Para detectá-la, o estado guarda a severidade do ciclo anterior (nula fora de condição). Aditiva.
ALTER TABLE alert_state ADD COLUMN severity VARCHAR(10) CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'));
