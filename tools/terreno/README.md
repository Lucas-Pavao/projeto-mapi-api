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
