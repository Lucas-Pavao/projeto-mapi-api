-- V36 — Maré harmônica (O3) ligada só como RESERVA, com o ok do Lucas em 2026-11-17 ("pode seguir com todos").
-- Corrige a linha da própria V35: fuso no mesmo formato das tábuas (UTC-03:00) e nota. Nada mais é alterado.
UPDATE source_registry
   SET source_tz = 'UTC-03:00',
       note = 'Maré do Porto do Recife (estação 30645) por análise harmônica (UTide) sobre a tábua DHN 2021-10..2024-12 (a F-41 não traz constantes). RESERVA: a leitura dos extremos só a usa onde não há extremo da DHN nem da TabuaMare a até 12 h. Validação 2025: altura p95 1,8 cm, horário p95 8 min.'
 WHERE source = 'TIDE_HARMONIC_F41';
