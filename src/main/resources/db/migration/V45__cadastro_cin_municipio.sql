-- V45 — Cadastro do CIn (onda 16, Fase 1.3): o ponto estava sem município, e o bloco "Hoje" dizia "município não
-- reconhecido". O CIn-UFPE fica no Recife (Cidade Universitária). Só preenche o vazio: não sobrescreve valor existente.
UPDATE flood_points
   SET municipality = 'RECIFE'
 WHERE slug = 'CIN_UFPE' AND (municipality IS NULL OR btrim(municipality) = '');
