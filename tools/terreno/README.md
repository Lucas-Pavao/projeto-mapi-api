# tools/terreno — terreno PE3D (F3-GEO-01/04/05/07)

Reproduz inventário, mosaico VRT, recortes COG, atributos por ponto crítico, a comparação
com a altitude atual e a validação da hidrologia. Procedimento e resultados: `docs/TERRENO-PE3D.md`.

```bash
tools/terreno/run.sh            # todas as etapas, incluindo validar e testes; ou: run.sh atributos validar testes
```

- Requer `docker` e `curl`. Usa `ghcr.io/osgeo/gdal:ubuntu-small-3.10.0` fixada por digest
  (GDAL 3.10.0, Python 3.12, numpy 1.26.4). Nada é instalado no sistema.
- Lê os zips por `/vsizip` (nunca descompacta). Entrada: `../Modelo digital de terreno (RASTER)/`
  (variável `DADOS`). Saída: `../terreno/` (variável `OUT`), fora do git. Num git worktree o caminho
  relativo muda: defina `DADOS` e `OUT` explicitamente.
- Antes de cada etapa confere o disco: aborta com menos de 1,5 GB livres em `/home` ou se a
  saída passar de 1 GB.
- Etapas: `altitude inventario vrt cobertura recortes atributos comparar estimativas validar testes`
  (`whitebox` roda sozinho antes de `atributos` e `validar`).
- `pontos_criticos.csv`: os 5 pontos de `MapiServiceImpl.seedPilotData()`. Se o seed mudar, atualize.
- `terreno.py` roda com `python3 -I`; usa GDAL (osgeo), numpy e a biblioteca padrão.

## COG em EPSG:3857 para o mapa (ADR 0008, C1)

`DADOS=... OUT=... tools/terreno/run.sh cog3857` (precisa da etapa `vrt` feita antes). Gera `OUT/cog3857/terrain_3857.tif`
(`gdalwarp -of COG -r near`, DEFLATE, PREDICTOR=3, blocos 256, overviews internas por vizinho mais próximo, recorte = bbox dos
pontos de `cobertura.csv` + 2 km) e `terrain_manifest.json` (hash do VRT, bbox, CRS, resampling, GDAL, overviews, SHA-256 do COG,
tempo de geração). Aborta com menos de 1,5 GB livres; o driver COG usa um temporário de ~0,8 GB além do COG final (~0,46 GB).
O `.tif` não vai ao git: copie para `public/terrain/` do front (dev) ou para o volume servido pelo nginx.

## Tiles PMTiles para o mapa (ADR 0008, C3 — decisão efetiva após C1 reprovar)

`DADOS=... OUT=... tools/terreno/run.sh tiles` (precisa de `cog3857`). `gdaldem color-relief` com `paleta_hipsometrica.txt` -> RGBA
temporário -> MBTiles PNG no zoom 17 -> `gdaladdo -r average` -> corta z < 12 -> `pmtiles convert` (go-pmtiles 1.31.2, BSD-3,
tarball conferido por SHA-256 no `run.sh`). Saída: `OUT/tiles/terrain_hypsometry.pmtiles` (zooms 12–17) e `terrain_manifest.json`
(zooms, bbox, paleta e seu hash, versões, contagem de tiles, tamanho, SHA-256, tempo). Os intermediários são apagados. A paleta
é a mesma de `src/features/map/utils/terrain.ts` do front: mudou uma, mude a outra e regenere. Copie o `.pmtiles` para `public/terrain/`
do front (dev) ou para o volume do nginx (precisa de `Range`).

Variantes (F3-FRONT-03), por ambiente: `ZOOM_MAX=16` (corta o zoom 17; o MapLibre amplia o z16), `TILE_FORMAT=PNG|PNG8|WEBP` e `TILE_QUALITY` (WebP), `TILES_VARIANT=nome` (saída em `OUT/tiles-nome/`). Sem variáveis o resultado é o de sempre (SHA-256 `3e375bf1…`, reproduzido em 2026-10-11). Medido: WebP (qualidade 50–90) não reduz o arquivo (20,7–25,1 MB contra 21,3 MB de PNG em z12–16); `ZOOM_MAX=16` reduz o tráfego de 3,39 para 2,20 MB.

## Hidrologia (fill, D8, área contribuinte)

- Motor padrão: **WhiteboxTools v2.4.0** (binário oficial baixado para `OUT/whitebox/`; o zip é conferido contra
  o SHA-256 fixado em `run.sh` e a execução aborta se diferir). `FillDepressions`, `D8Pointer`, `D8FlowAccumulation`.
- Motor alternativo sem download: `MOTOR=proprio tools/terreno/run.sh atributos` (Priority-Flood + D8 de
  máxima declividade próprios). Os resultados de área contribuinte diferem (docs, seção 7); o padrão é o Whitebox.
- **Regra de snap**: a área contribuinte é a acumulação do pixel de maior acumulação a ≤ 10 m do ponto
  (`maximo_no_raio`; empate: mais próximo, depois menor linha/coluna). Detalhes e sensibilidade: docs, seção 5.1.
- `run.sh validar`: compara o código próprio (e o Whitebox com breaching) com o Whitebox de referência nas janelas de 2 km;
  grava `validacao_whitebox.csv/json` em `OUT`.
- `run.sh testes`: pytest (baixado, versões fixadas e SHA-256 conferido pelo PyPI) no mesmo container, sobre DEMs sintéticos
  (cone, plano inclinado, poço único, duas bacias, platô; `tests/test_hidrologia.py`).
- Reprodutibilidade: duas execuções de `atributos` geram o mesmo SHA-256 de `ponto_terreno.csv`.
