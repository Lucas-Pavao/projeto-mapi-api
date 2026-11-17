-- V35 — Fonte nova de maré prevista por análise harmônica (operação O3), registrada e NÃO ligada.
-- Só adição. Nenhuma rotina lê esta fonte: a troca da fonte de maré é decisão do Lucas.
-- Previsão de 2027 em projeto-mapi-ai/data/tide/harmonic-pe02-2027.csv; validação em projeto-mapi-ai/reports/tide/harmonica_2027.json.
INSERT INTO source_registry (source, source_tz, verification_state, note)
VALUES ('TIDE_HARMONIC_F41', 'Etc/GMT+3', 'DECLARED',
        'Maré do Porto do Recife (estação 30645) por análise harmônica (UTide) sobre a tábua DHN 2021-10..2024-12, porque a ficha F-41 não traz constantes. NÃO LIGADA. Validação 2025: altura p95 1,8 cm, horário p95 8 min.')
ON CONFLICT (source) DO NOTHING;
