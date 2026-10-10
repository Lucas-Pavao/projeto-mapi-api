-- F2-BACK-14: maré astronômica prevista (tábua da DHN, Porto do Recife, pe02) no pipeline do ADR 0006.
-- ADITIVA: só linhas novas em source_registry e variable_catalog, mais a faixa física de tide_height (antes
-- [-2, 5], sem uso; nenhuma observação dela existia). Altura referida ao NR da DHN (vertical_reference =
-- NR_DHN_pe02), NÃO convertida ao datum do MDT (ADR 0009).

-- Hora da tábua: "Fuso +03.0 horas" (PDF 2021-2024) e "Fuso UTC -03.0 horas" (PDF 2025-2026); a API TabuaMare declara
-- "timezone":"UTC -03.0". Offset fixo UTC-03:00; a validação converte para UTC. Estado DECLARED (declarado
-- pelo cabeçalho da tábua; a API foi conferida contra o PDF de 2026: 1407 extremos, diferença máxima 0).
INSERT INTO source_registry (source, source_tz, verification_state, note) VALUES
    ('TABUAMARE_PE02',   'UTC-03:00', 'DECLARED',
     'API TabuaMare, porto pe02, só serve o ano corrente (sem parâmetro de ano); hora local UTC-03:00; altura no NR da DHN'),
    ('DHN_TABUA_PE02',   'UTC-03:00', 'DECLARED',
     'Tábua anual da DHN/CHM (PDF -> CSV em src/main/resources/tide), hora local UTC-03:00; altura no NR da DHN'),
    ('TIDE_PE02_HOURLY', 'UTC',       'DECLARED',
     'derivada: interpolação por cosseno entre extremos consecutivos de tide_height (EVENT); instante em UTC');

-- Faixa física de maré no Recife: conservadora, [sem fonte] (excursão da tábua no Recife: -0,03 a ~2,7 m).
UPDATE variable_catalog
   SET min_value = -0.5, max_value = 3.0,
       range_source = '[sem fonte, conservadora] altura da tábua DHN acima do NR (NR_DHN_pe02)'
 WHERE variable = 'tide_height';

INSERT INTO variable_catalog
    (variable, canonical_unit, min_value, max_value, range_source, expected_resolution,
     interval_convention, hourly_aggregation) VALUES
    ('tide_height_hourly', 'm', -0.5, 3.0,
     '[sem fonte, conservadora] altura interpolada (cosseno) acima do NR da DHN (NR_DHN_pe02)',
     'PT1H', 'INSTANT', 'LAST');
