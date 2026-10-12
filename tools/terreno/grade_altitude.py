"""Grade de altitude do MDT (PE3D) para consulta pontual pela API (card "Análise de local").

Lê a reamostragem de 5 m (média) feita pelo run.sh e grava:
  - mdt_5m_cm.i16: inteiros de 16 bits little-endian, linha a linha de norte para sul, em centímetros; nodata = -32768;
  - mdt_5m.json: geotransform em EPSG:31985, dimensões, nodata, hashes e origem.
A API lê o arquivo por deslocamento (sem biblioteca geoespacial). Cota relativa: datum do MDT não confirmado (ADR 0009).
Uso: python3 -I grade_altitude.py /terreno/altitude/mdt_5m.tif /terreno/altitude <vrt_sha256> <imagem_gdal>
"""
import hashlib
import json
import sys

import numpy as np
from osgeo import gdal

gdal.UseExceptions()
NODATA_CM = -32768


def main(entrada, saida, vrt_sha, imagem):
    ds = gdal.Open(entrada)
    banda = ds.GetRasterBand(1)
    a = banda.ReadAsArray().astype(np.float64)
    nd = banda.GetNoDataValue()
    invalido = ~np.isfinite(a) | (a == nd if nd is not None else False) | (a < -100) | (a > 300)
    cm = np.rint(a * 100.0)
    cm[invalido] = NODATA_CM
    cm = cm.astype('<i2')
    bin_path = f"{saida}/mdt_5m_cm.i16"
    cm.tofile(bin_path)
    sha = hashlib.sha256(open(bin_path, 'rb').read()).hexdigest()
    gt = ds.GetGeoTransform()
    meta = {
        "arquivo": "mdt_5m_cm.i16",
        "formato": "int16 little-endian, linha a linha de norte para sul, centímetros; nodata -32768",
        "epsg": 31985,
        "x_min": gt[0], "y_max": gt[3], "pixel_x": gt[1], "pixel_y": gt[5],
        "colunas": ds.RasterXSize, "linhas": ds.RasterYSize,
        "nodata_cm": NODATA_CM,
        "reamostragem": "média de 5 m x 5 m a partir do mosaico de 1 m (gdalwarp -r average)",
        "celulas_validas": int((cm != NODATA_CM).sum()),
        "sha256": sha,
        "vrt_sha256": vrt_sha,
        "imagem_gdal": imagem,
        "referencia_vertical": "datum do MDT não declarado pelo PE3D; só cotas relativas (ADR 0009)",
        "voo": 2016,
    }
    json.dump(meta, open(f"{saida}/mdt_5m.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(json.dumps({k: meta[k] for k in ("colunas", "linhas", "celulas_validas", "sha256")}))


if __name__ == "__main__":
    main(*sys.argv[1:5])
