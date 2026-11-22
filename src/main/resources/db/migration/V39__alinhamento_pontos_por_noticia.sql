-- Alinhamento dos pontos de alagamento pelo local citado em notícia/lista da CTTU (2026-11-21; docs/ALINHAMENTO-PONTOS.md).
-- Só a coordenada muda; slug e id ficam. Jaboatão e CIn não mudam (nenhuma notícia localiza a rua).
--   AV_RECIFE_IBURA: cruzamento Av. Recife x Av. Dom Hélder Câmara ("Av. Recife / Entrada do Ibura", CTTU) — 8 m do valor antigo
--   AGAMENON_DERBY: Av. Gov. Agamenon Magalhães, pista sentido Olinda, lado oposto ao Hospital da Restauração — ~340 m
--   MASCARENHAS_IMBIRIBEIRA: Av. Mal. Mascarenhas de Moraes entre os nº 2557 e 2857 (CTTU, ambos os sentidos) — ~1,4 km
UPDATE flood_points SET latitude = -8.107984, longitude = -34.927161 WHERE slug = 'AV_RECIFE_IBURA';
UPDATE flood_points SET latitude = -8.054324, longitude = -34.896875 WHERE slug = 'AGAMENON_DERBY';
UPDATE flood_points SET latitude = -8.107178, longitude = -34.911237 WHERE slug = 'MASCARENHAS_IMBIRIBEIRA';
