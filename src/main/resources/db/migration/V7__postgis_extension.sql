-- V7 — Extensão PostGIS (ADR 0005, aprovado em 2026-10-10).
-- Necessária para a Fase 3: amostrar o MDT do PE3D, recortar raster e calcular declividade/bacia no banco
-- (ADR 0008, armazenamento do terreno). A imagem timescale/timescaledb-ha:pg16.15-ts2.30.2 traz PostGIS 3.6.4.
-- Aditiva: só cria extensões; não altera tabela existente.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_raster;
