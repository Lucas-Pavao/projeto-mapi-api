-- Alarme de fonte velha (plano de melhorias de 2026-11-16, item 1.4). ADITIVA: duas colunas novas em source_registry e
-- os valores delas; nenhuma coluna ou linha existente muda.
--
-- max_age_minutes: idade máxima aceita da leitura ACEITA mais nova (observation, status ACTIVE, ts_utc <= agora).
--   Olha leitura aceita, não execução: a meteorologia de 24 h da APAC roda a cada 5 min e nunca aceitou nada.
-- min_coverage_ahead_minutes: para fonte de PREVISÃO (maré), cobertura mínima à frente de agora.
-- NULL nas duas = fonte não monitorada (carga manual, sem frequência esperada).
-- Valores propostos a partir da frequência medida em 2026-11-16; ver docs/adr/0006 (emenda) e PROGRESSO.
ALTER TABLE source_registry ADD COLUMN max_age_minutes INTEGER CHECK (max_age_minutes > 0);
ALTER TABLE source_registry ADD COLUMN min_coverage_ahead_minutes INTEGER CHECK (min_coverage_ahead_minutes > 0);

UPDATE source_registry SET max_age_minutes = 90    WHERE source = 'CEMADEN';            -- feed da APAC, 3 min; hora seca chega em bloco de 60 min
UPDATE source_registry SET max_age_minutes = 180   WHERE source = 'CEMADEN_PED';        -- carga horária; a PED publica com ~1 h de atraso
UPDATE source_registry SET max_age_minutes = 120   WHERE source = 'ANA_SOAP';           -- telemetria de 15 min
UPDATE source_registry SET max_age_minutes = 60    WHERE source = 'APAC_METEO24H';      -- coleta de 5 min
UPDATE source_registry SET max_age_minutes = 14400 WHERE source IN ('OPEN_METEO_ARCHIVE', 'OPEN_METEO_ERA5LAND'); -- reanálise: ~5 dias de atraso natural
UPDATE source_registry SET min_coverage_ahead_minutes = 43200 WHERE source = 'TABUAMARE_PE02';   -- 30 dias de tábua à frente
UPDATE source_registry SET min_coverage_ahead_minutes = 2880  WHERE source = 'TIDE_PE02_HOURLY'; -- horária derivada: 2 dias
