# tools/terreno — terreno PE3D (F3-GEO-01/04/05)

Reproduz inventário, mosaico VRT, recortes COG, atributos por ponto crítico e a comparação
com a altitude atual. Procedimento e resultados: `docs/TERRENO-PE3D.md`.

```bash
tools/terreno/run.sh            # todas as etapas (≈ 2 min); ou: run.sh atributos comparar
```

- Requer `docker` e `curl`. Usa `ghcr.io/osgeo/gdal:ubuntu-small-3.10.0` fixada por digest
  (GDAL 3.10.0, Python 3.12, numpy 1.26.4). Nada é instalado no sistema.
- Lê os zips por `/vsizip` (nunca descompacta). Entrada: `../Modelo digital de terreno (RASTER)/`
  (variável `DADOS`). Saída: `../terreno/` (variável `OUT`), fora do git.
- Antes de cada etapa confere o disco: aborta com menos de 1,5 GB livres em `/home` ou se a
  saída passar de 1 GB.
- Etapas: `altitude inventario vrt cobertura recortes atributos comparar estimativas`.
- `pontos_criticos.csv`: os 5 pontos de `MapiServiceImpl.seedPilotData()`. Se o seed mudar, atualize.
- `terreno.py` roda com `python3 -I`; só usa GDAL (osgeo), numpy e a biblioteca padrão.
