-- Baseline: reproduz o schema que já está em produção/desenvolvimento hoje (extraído via
-- pg_dump --schema-only do banco real e reconciliado com TimescaleSetup.sql). Em bancos NOVOS,
-- o Flyway roda esta migration de verdade. Em bancos EXISTENTES (que já têm esse schema, criado
-- antes de existir controle de migration), o Flyway é configurado com baseline-on-migrate=true
-- e baseline-version=1, então esta V1 é só registrada como "já aplicada", sem re-executar.
--
-- A partir daqui, qualquer mudança de schema deve vir como uma nova migration Vn__descricao.sql
-- (nunca editando esta ou uma migration já aplicada), com ddl-auto continuando em "validate".

CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- ==========================================================================================
-- Hypertables (séries temporais)
-- ==========================================================================================

CREATE TABLE sensor_data (
    id BIGSERIAL,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    sensor_id VARCHAR(255),
    "value" DOUBLE PRECISION,
    unit VARCHAR(255),
    battery_status VARCHAR(255),
    raw_data TEXT,
    station_name VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    municipality VARCHAR(255),
    type VARCHAR(255),
    source VARCHAR(255),
    fog_value_reference DOUBLE PRECISION,
    code VARCHAR(255),
    temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction VARCHAR(255),
    solar_radiation DOUBLE PRECISION,
    accumulated_precipitation DOUBLE PRECISION,
    soil_humidity VARCHAR(255),
    water_level DOUBLE PRECISION,
    flow_rate DOUBLE PRECISION,
    basin_name VARCHAR(255),
    tide_height DOUBLE PRECISION,
    PRIMARY KEY (id, timestamp),
    CONSTRAINT uk1rrcr7ou9r4up2w3ba9jiwg4q UNIQUE (sensor_id, timestamp)
);
SELECT create_hypertable('sensor_data', 'timestamp', if_not_exists => TRUE);

CREATE TABLE weather_data (
    id BIGSERIAL,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    apparent_temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    weather_code INTEGER,
    is_day BOOLEAN,
    precipitation DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    solar_radiation DOUBLE PRECISION,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (id, timestamp)
);
SELECT create_hypertable('weather_data', 'timestamp', if_not_exists => TRUE);

CREATE MATERIALIZED VIEW IF NOT EXISTS hourly_precipitation
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', timestamp) AS bucket,
       sensor_id,
       avg(accumulated_precipitation) as avg_precip,
       max(accumulated_precipitation) as max_precip
FROM sensor_data
GROUP BY bucket, sensor_id
WITH NO DATA;

CREATE TABLE flood_predictions (
    id BIGSERIAL,
    timestamp TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    station_id VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    current_rainfall DOUBLE PRECISION,
    rainfall_3h_accumulated DOUBLE PRECISION,
    rainfall_6h_accumulated DOUBLE PRECISION,
    rainfall_12h_accumulated DOUBLE PRECISION,
    rainfall_24h_accumulated DOUBLE PRECISION,
    tide_level DOUBLE PRECISION,
    river_level DOUBLE PRECISION,
    flood_probability DOUBLE PRECISION,
    risk_level VARCHAR(255),
    status VARCHAR(255),
    message TEXT,
    PRIMARY KEY (id, timestamp)
);
SELECT create_hypertable('flood_predictions', 'timestamp', if_not_exists => TRUE);

CREATE TABLE flood_scenario_labels (
    id BIGSERIAL,
    timestamp TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_flooded BOOLEAN NOT NULL,
    current_rainfall DOUBLE PRECISION,
    rainfall_3h_accumulated DOUBLE PRECISION,
    rainfall_6h_accumulated DOUBLE PRECISION,
    rainfall_12h_accumulated DOUBLE PRECISION,
    rainfall_24h_accumulated DOUBLE PRECISION,
    tide_level DOUBLE PRECISION,
    river_level DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction VARCHAR(255),
    temperature DOUBLE PRECISION,
    apparent_temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    wave_height DOUBLE PRECISION,
    wave_period DOUBLE PRECISION,
    wave_direction DOUBLE PRECISION,
    solar_radiation DOUBLE PRECISION,
    PRIMARY KEY (id, timestamp)
);
SELECT create_hypertable('flood_scenario_labels', 'timestamp', if_not_exists => TRUE);

CREATE INDEX idx_sensor_id_timestamp ON sensor_data (sensor_id, timestamp DESC);
CREATE INDEX idx_sensor_timestamp ON sensor_data (sensor_id, timestamp);
CREATE INDEX idx_weather_location_timestamp ON weather_data (latitude, longitude, timestamp DESC);
CREATE INDEX idx_weather_timestamp ON weather_data (timestamp, latitude, longitude);
CREATE INDEX idx_prediction_timestamp ON flood_predictions (timestamp DESC);
CREATE INDEX idx_scenario_timestamp ON flood_scenario_labels (timestamp DESC);

-- ==========================================================================================
-- Tabelas relacionais (auth, pontos críticos, tábua de maré, eventos)
-- ==========================================================================================

CREATE TABLE users (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE refresh_token (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    user_id BIGINT UNIQUE REFERENCES users(id)
);

CREATE TABLE flood_points (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    municipality VARCHAR(255),
    basin_name VARCHAR(255),
    altitudem DOUBLE PRECISION,
    distance_to_channelm DOUBLE PRECISION,
    alert_threshold_mm DOUBLE PRECISION,
    active BOOLEAN
);

CREATE TABLE flood_point_pluviometers (
    flood_point_id BIGINT NOT NULL REFERENCES flood_points(id),
    station_id VARCHAR(255)
);

CREATE TABLE flood_point_river_levels (
    flood_point_id BIGINT NOT NULL REFERENCES flood_points(id),
    station_id VARCHAR(255)
);

CREATE TABLE flood_point_weather_stations (
    flood_point_id BIGINT NOT NULL REFERENCES flood_points(id),
    station_id VARCHAR(255)
);

CREATE TABLE flood_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    flood_point_id BIGINT NOT NULL REFERENCES flood_points(id),
    start_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP(6) WITHOUT TIME ZONE,
    severity VARCHAR(255) CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT,
    confirmed_by VARCHAR(255)
);

CREATE TABLE tide_tables (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    harbor_name VARCHAR(255),
    state VARCHAR(255),
    year INTEGER,
    timezone VARCHAR(255),
    card VARCHAR(255),
    data_collection_institution VARCHAR(255),
    mean_level REAL
);

CREATE TABLE geo_locations (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tide_table_id BIGINT REFERENCES tide_tables(id),
    lat VARCHAR(255),
    lng VARCHAR(255),
    decimal_lat VARCHAR(255),
    decimal_lng VARCHAR(255),
    lat_direction VARCHAR(255),
    lng_direction VARCHAR(255)
);

CREATE TABLE month_data (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tide_table_id BIGINT REFERENCES tide_tables(id),
    month INTEGER,
    month_name VARCHAR(255)
);

CREATE TABLE day_data (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    month_data_id BIGINT REFERENCES month_data(id),
    day INTEGER,
    weekday_name VARCHAR(255)
);

CREATE TABLE hour_data (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    day_data_id BIGINT REFERENCES day_data(id),
    hour VARCHAR(255),
    level REAL
);
