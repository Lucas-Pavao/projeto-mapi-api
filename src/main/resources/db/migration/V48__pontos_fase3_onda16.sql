-- V48 — Onda 16, Fase 3 (docs/ONDA16-FASE3-PONTOS.md). Só a coordenada muda; slug e id ficam.
--   MASCARENHAS_IMBIRIBEIRA: projeção perpendicular no eixo da Av. Mal. Mascarenhas de Moraes (OSM) do ponto da V39, que
--     caía no centro dos lotes 2557 e 2857 do Nominatim, 24,4 m fora da via e 0,9 m acima dela. Mesmo lugar, na rua.
--   JABOATAO_CENTRO: Av. Barão de Lucena na margem do Rio Duas Unas (lugar citado em fontes públicas; nenhuma data nem
--     intensidade usada): pixel da avenida a 20–50 m do eixo do rio com a maior profundidade de depressão do MDT
--     (PE3D de Jaboatão). Antes: encosta a 64,8 m, 595 m ao norte.
UPDATE flood_points SET latitude = -8.107134, longitude = -34.911454 WHERE slug = 'MASCARENHAS_IMBIRIBEIRA';
UPDATE flood_points SET latitude = -8.111854, longitude = -35.013919 WHERE slug = 'JABOATAO_CENTRO';
