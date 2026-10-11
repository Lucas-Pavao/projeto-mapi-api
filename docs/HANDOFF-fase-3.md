# Handoff — Fase 3, terreno PE3D (2026-10-10)

**Feito.** ADR 0008 (armazenamento) e ADR 0009 (referência vertical), ambos aprovados pelo Lucas
depois de checkpoint Máximo (cada um com 1 reprovação e refação). Pipeline reprodutível
`tools/terreno/run.sh`: GDAL 3.10.0 por digest, leitura direta dos zips e hidrologia via
WhiteboxTools v2.4.0 com hash fixado. Duas execuções produzem o mesmo `ponto_terreno.csv`
(SHA-256 `114ddd32…`). Backend: V9 (`terrain_run`, `point_terrain`, `point_terrain_curve`), carga
idempotente e `GET /api/pontos/{slug}/terrain`, além das colunas `terrain_*` no export. Front: camada
de hipsometria por tiles PMTiles. Quatro pedidos institucionais redigidos em `docs/pedidos/`.

**Números (origem: `docs/TERRENO-PE3D.md`, `docs/revisao/fase-3/`).**
- **Cobertura:** 4 dos 5 pontos. Jaboatão Centro fica a 1 410 m da folha mais próxima.
- **Cota da rua:** Ibura 1,39; CIn 7,68; Derby 2,58; Imbiribeira 2,07 m. Datum não confirmado; só valem cotas relativas.
- **Comparação com o Open-Meteo:** MDT − Open-Meteo dá −2,82 m em média e 5,42 m de diferença máxima (Derby).
- **Hidrologia validada:** o D8 próprio tinha um bug (53–55% de concordância), pego pelo teste; o motor passou a ser o Whitebox, com 97–99,5% de concordância. Área contribuinte: Ibura 208, CIn 82 378, Derby 62, Imbiribeira 10 103 m². A regra de snap (≤ 10 m) é uma premissa documentada.
- **Camada visual:** o critério foi fixado antes de medir (≤ 5 MB por visita, ≤ 3 s em Fast 4G com CPU 4×). C1 (COG) **reprovou**, com 20,95 MB e 5,8 s. C3 (tiles PMTiles de 63,8 MiB) **passou**, com 3,39 MB e 2,89 s, mas a margem é estreita: 1 das 3 rodadas deu 3,02 s, numa máquina sem GPU.

**O que não pode ser feito ainda (ADR 0009).** O desnível entre o NR da DHN e o datum do MDT não foi
fechado (estimativa de trabalho: Δ ≈ +1,04 ± 0,3 m, inutilizável). Por isso estão proibidos: "nível lá
fora − cota da rua", folga até a soleira com maré e "áreas abaixo da maré". A soleira só vale com fonte
de campo ou de cadastro de drenagem.

**Pendências.**
- Respostas do CHM/DHN (F-41), do IBGE (RN do marégrafo e NMM) e do PE3D (datum/geoide) → fixar Δ.
- Testar a camada de terreno em celular real, por causa da margem de 3 s (F4-FRONT-01b).
- Resíduos visuais: legenda cortada em 1920 px, "PONTOS CRÍTICOS" truncado e opacidade a validar.
- O front segue sem commit, sobre o WIP do Lucas; os patches estão em `docs/revisao/fase-3/`.

**Branches locais (sem push):** `feat/terreno-pe3d` → `test/hidrologia-terreno` → `feat/terreno-cog3857`
(tiles), e `feat/terreno-backend`, integrado em `integracao/fase-2`.
