-- A APAC (endpoint meteorologia24h) publica, para estações de nível de rio (ex.: Rio Duas Unas
-- em Jaboatão), os thresholds oficiais de pré-alerta/alerta/inundação usados pela própria agência
-- (campos Nivel/preAlertLevel/alertLevel/floodLevel). Esse dado nunca foi capturado pelo pipeline
-- (nem no fluxo MQTT antigo) — o parser só reconhecia o formato de estação meteorológica
-- convencional. Como esse é dado central para o propósito do app (alerta de alagamento), ganha
-- colunas dedicadas em vez de ficar só dentro do raw_data.
ALTER TABLE sensor_data
    ADD COLUMN river_name VARCHAR(255),
    ADD COLUMN river_pre_alert_level DOUBLE PRECISION,
    ADD COLUMN river_alert_level DOUBLE PRECISION,
    ADD COLUMN river_flood_level DOUBLE PRECISION;
