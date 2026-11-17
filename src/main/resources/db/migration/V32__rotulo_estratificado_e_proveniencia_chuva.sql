-- V32 — Rótulo híbrido estratificado e proveniência da chuva do ponto (ADR 0025, proposta).
-- Só adição e alargamento do CHECK de geocode_level para incluir ADDRESS. Nenhum dado existente é apagado.

-- 1. Nível de geocodificação ADDRESS (endereço com número/esquina retornado pelo Nominatim).
ALTER TABLE flood_occurrence DROP CONSTRAINT flood_occurrence_geocode_level_chk;
ALTER TABLE flood_occurrence ADD CONSTRAINT flood_occurrence_geocode_level_chk
    CHECK (geocode_level IN ('ADDRESS', 'STREET', 'NEIGHBORHOOD', 'CITY', 'NONE'));

-- 2. Estrato, peso e tipo de casamento temporal no vínculo ocorrência↔ponto.
ALTER TABLE occurrence_point_match
    ADD COLUMN tier VARCHAR(10) CHECK (tier IN ('HIGH', 'MEDIUM', 'LOW')),
    ADD COLUMN sample_weight DOUBLE PRECISION,
    ADD COLUMN temporal_match VARCHAR(24);

-- 3. Proveniência estratificada da chuva horária do ponto (sem mudar o valor nem a regra do ADR 0022).
ALTER TABLE point_rain_hourly
    ADD COLUMN rain_tier VARCHAR(24)
        CHECK (rain_tier IN ('MEASURED_PRIMARY', 'MEASURED_EXTENDED', 'REANALYSIS_FALLBACK')),
    ADD COLUMN rain_confidence DOUBLE PRECISION;

-- 4. Configuração de vínculo própria para o modo estratificado (não altera configs existentes).
INSERT INTO linkage_config (kind, radius_m, method, created_by, note)
SELECT 'OCCURRENCE_POINT', 600, 'STRATIFIED_TIER', 'V32',
       'ADR 0025 (proposta): Ouro<=250m (1.0), Prata<=400m (0.7), Bronze<=600m cluster>=3 (0.4)'
WHERE NOT EXISTS (
    SELECT 1 FROM linkage_config
    WHERE kind = 'OCCURRENCE_POINT' AND radius_m = 600 AND method = 'STRATIFIED_TIER'
);
