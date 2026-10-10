-- F2-BACK-11 (ADR 0007): colunas próprias para a auditoria da predição, que hoje vai como JSON em `message`.
-- Aditiva: nenhuma coluna existente muda de tipo ou é removida.
--
-- Linhas antigas ficam com as colunas novas NULL (imputed_features recebe '[]' pelo DEFAULT).
-- `issued_at` NÃO é preenchida a partir de `timestamp`: esta é TIMESTAMP sem fuso e o fuso em que as
-- linhas antigas foram escritas é incerto (LocalDateTime.now() da JVM); converter inventaria um instante.
-- Só gravações novas preenchem `issued_at` (instante UTC real da previsão).
-- `top_contributors` (jsonb, [{featureId, contribution}]) também é nova: sem ela os contribuintes, que
-- estavam no JSON de `message`, se perderiam ao deixar de gravar JSON ali.
ALTER TABLE flood_predictions
    ADD COLUMN model_version      TEXT,
    ADD COLUMN model_version_full TEXT,
    ADD COLUMN horizon_hours      INTEGER,
    ADD COLUMN degradation_reason TEXT
        CONSTRAINT chk_flood_predictions_degradation_reason
        CHECK (degradation_reason IN ('INSUFFICIENT_DATA', 'AI_TIMEOUT', 'AI_UNAVAILABLE', 'AI_ERROR')),
    ADD COLUMN imputed_features   JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN top_contributors   JSONB,
    ADD COLUMN flood_point_id     BIGINT,
    ADD COLUMN issued_at          TIMESTAMPTZ;

-- NULL permitido: linhas antigas e POST de cenário (coordenada avulsa, sem ponto crítico).
-- FK de hypertable PARA tabela comum é suportada pelo TimescaleDB (a restrição é no sentido inverso).
ALTER TABLE flood_predictions
    ADD CONSTRAINT fk_flood_predictions_flood_point
    FOREIGN KEY (flood_point_id) REFERENCES flood_points (id);

-- Inclui a coluna de tempo (hypertable) e serve a "última previsão / última SUCCESS do ponto".
CREATE INDEX idx_prediction_point_timestamp ON flood_predictions (flood_point_id, timestamp DESC);
