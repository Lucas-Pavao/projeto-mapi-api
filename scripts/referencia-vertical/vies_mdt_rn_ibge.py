"""Compara o MDT do PE3D com a altitude normal (Imbituba, REALT 2018) de RRNN do IBGE no Recife.

ADR 0009, emenda 1, opção B2. Altitudes e coordenadas: relatórios do BDG (docs/extratos/f41-porto-recife.md §3).
Uso (imagem ghcr.io/osgeo/gdal:ubuntu-small-3.10.0, com /terreno e /dados montados como na Fase 3):
    python3 vies_mdt_rn_ibge.py /terreno/mosaico.vrt rns_ibge_recife.csv
Saída: mediana do MDT num quadrado de 11 x 11 pixels (1 m) em volta da coordenada do BDG, p10/p90 e MDT - IBGE.
Ponto com p10 < 1,0 m é marcado AGUA: a coordenada do BDG (arredondada ao segundo, ~30 m) caiu na lâmina d'água.
"""
import csv
import sys

import numpy as np
from osgeo import gdal

gdal.UseExceptions()
LIMIAR_AGUA_M = 1.0
RAIO_PX = 5


def main(vrt, tabela):
    ds = gdal.Open(vrt)
    gt = ds.GetGeoTransform()
    banda = ds.GetRasterBand(1)
    nodata = banda.GetNoDataValue()
    terra = {"BOM": [], "NAO_ENCONTRADO": []}
    print("rn situacao alt_ibge mdt_mediana p10 p90 mdt_menos_ibge classe")
    for linha in csv.DictReader(open(tabela, encoding="utf-8")):
        alt = float(linha["altitude_normal_m"])
        col = int((float(linha["utm_e"]) - gt[0]) / gt[1])
        lin = int((float(linha["utm_n"]) - gt[3]) / gt[5])
        a = banda.ReadAsArray(col - RAIO_PX, lin - RAIO_PX, 2 * RAIO_PX + 1, 2 * RAIO_PX + 1).astype(float)
        if nodata is not None:
            a[a == nodata] = np.nan
        v = a[np.isfinite(a)]
        med, p10, p90 = np.median(v), np.percentile(v, 10), np.percentile(v, 90)
        classe = "AGUA" if p10 < LIMIAR_AGUA_M else "TERRA"
        if classe == "TERRA":
            terra[linha["situacao_marco"]].append(med - alt)
        print(f"{linha['rn']} {linha['situacao_marco']} {alt:.4f} {med:.3f} {p10:.3f} {p90:.3f} {med - alt:+.3f} {classe}")
    todos = np.array(terra["BOM"] + terra["NAO_ENCONTRADO"])
    for nome, d in (("TERRA, marco BOM", np.array(terra["BOM"])), ("TERRA, todos", todos)):
        print(f"{nome}: n={d.size} mediana {np.median(d):+.3f} media {d.mean():+.3f} dp {d.std(ddof=1):.3f} "
              f"min {d.min():+.3f} max {d.max():+.3f}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
