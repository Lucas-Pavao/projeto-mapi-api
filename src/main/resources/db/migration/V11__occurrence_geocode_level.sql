-- F2-BACK-20: nivel de precisao da geocodificacao e bairro de origem em flood_occurrence. ADITIVA (so ADD COLUMN, NULL-aveis).
--
-- geocode_level: STREET (rua/endereco/ponto), NEIGHBORHOOD (centroide de bairro), CITY (so a cidade), NONE (sem coordenada).
-- NULL = legado cujo nivel nao foi verificavel: nao se inventa nivel para coordenada gravada antes desta migracao.
-- neighborhood: texto do campo de bairro do bruto (CKAN), aparado; NULL se o bruto nao traz bairro.
ALTER TABLE flood_occurrence ADD COLUMN geocode_level VARCHAR(12)
    CONSTRAINT flood_occurrence_geocode_level_chk CHECK (geocode_level IN ('STREET', 'NEIGHBORHOOD', 'CITY', 'NONE'));
ALTER TABLE flood_occurrence ADD COLUMN neighborhood VARCHAR(120);
