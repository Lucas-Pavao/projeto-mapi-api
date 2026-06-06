-- SQL para Otimização do Banco de Dados MAPI com TimescaleDB
-- Este script inicializa as tabelas como Hypertables para alta performance em séries temporais.

-- 1. Habilitar a extensão
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- 2. Criar tabela sensor_data (Estrutura compatível com Hibernate + Timescale)
CREATE TABLE IF NOT EXISTS sensor_data (
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
    soil_humidity TEXT,
    water_level DOUBLE PRECISION,
    flow_rate DOUBLE PRECISION,
    basin_name VARCHAR(255),
    tide_height DOUBLE PRECISION,
    PRIMARY KEY (id, timestamp)
);

-- Converter para Hypertable
SELECT create_hypertable('sensor_data', 'timestamp', if_not_exists => TRUE);

-- 3. Criar tabela weather_data (Estrutura compatível com Hibernate + Timescale)
CREATE TABLE IF NOT EXISTS weather_data (
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

-- Converter para Hypertable
SELECT create_hypertable('weather_data', 'timestamp', if_not_exists => TRUE);

-- 4. Criar View de Agregados Contínuos (Chuva por Hora)
CREATE MATERIALIZED VIEW IF NOT EXISTS hourly_precipitation
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', timestamp) AS bucket,
       sensor_id,
       avg(accumulated_precipitation) as avg_precip,
       max(accumulated_precipitation) as max_precip
FROM sensor_data
GROUP BY bucket, sensor_id
WITH NO DATA;

-- 5. Índices Adicionais
CREATE INDEX IF NOT EXISTS idx_sensor_id_timestamp ON sensor_data (sensor_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_weather_location_timestamp ON weather_data (latitude, longitude, timestamp DESC);
