-- F2-BACK-15 (aprovada pelo autor, 2026-10-10): umidade do solo ERA5-Land via Open-Meteo archive.
-- Somente DML aditivo. Fonte nova OPEN_METEO_ERA5LAND (chamada separada com models=era5_land, para não
-- alterar valores/hashes já carregados de OPEN_METEO_ARCHIVE).
INSERT INTO source_registry (source, source_tz, verification_state, note) VALUES
    ('OPEN_METEO_ERA5LAND', 'UTC', 'VERIFIED',
     'timezone=GMT explícito e utc_offset_seconds=0 conferido a cada resposta; models=era5_land');

-- Estado (não fluxo): INSTANT + LAST. Faixa 0 a 1 m³/m³ é a definição física de fração volumétrica de água.
INSERT INTO variable_catalog
    (variable, canonical_unit, min_value, max_value, range_source, expected_resolution,
     interval_convention, hourly_aggregation) VALUES
    ('soil_moisture_0_7cm', 'm³/m³', 0, 1,
     'definição física (fração volumétrica); unidade: Open-Meteo historical-weather-api, 2026-10-10',
     'PT1H', 'INSTANT', 'LAST');
