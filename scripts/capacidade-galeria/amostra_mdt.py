"""Cota do MDT (PE3D) em cada nó do ESIG dos 4 pontos. Roda no container GDAL do tools/terreno/run.sh:

    docker run --rm -v "<DADOS>":/dados:ro -v "<OUT do terreno>":/terreno:ro -v "<repo>":/repo <imagem gdal> \
        python3 -I /repo/scripts/capacidade-galeria/amostra_mdt.py

Lê /terreno/mosaico.vrt e /repo/docs/extratos/esig-drenagem/geojson/<PONTO>__MI_c0.geojson; escreve
/repo/scripts/capacidade-galeria/entrada/mdt_nos.csv (ponto, OBJECTID, TIPO, x, y, z_esig, mdt_m). mdt_m vazio = nodata.
"""
import csv
import json
import os

from osgeo import gdal

gdal.UseExceptions()
PONTOS = ["AV_RECIFE_IBURA", "CIN_UFPE", "AGAMENON_DERBY", "MASCARENHAS_IMBIRIBEIRA"]
ds = gdal.Open("/terreno/mosaico.vrt")
gt = ds.GetGeoTransform(); b = ds.GetRasterBand(1)
os.makedirs("/repo/scripts/capacidade-galeria/entrada", exist_ok=True)
with open("/repo/scripts/capacidade-galeria/entrada/mdt_nos.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f, lineterminator="\n")
    w.writerow(["ponto", "objectid", "tipo", "x", "y", "z_esig", "mdt_m"])
    for p in PONTOS:
        d = json.load(open(f"/repo/docs/extratos/esig-drenagem/geojson/{p}__MI_c0.geojson", encoding="utf-8"))
        for ft in d["features"]:
            x, y = ft["geometry"]["coordinates"][:2]
            c = int((x - gt[0]) / gt[1]); l = int((y - gt[3]) / gt[5])
            v = float(b.ReadAsArray(c, l, 1, 1)[0, 0])
            pr = ft["properties"]
            w.writerow([p, pr["OBJECTID"], pr["TIPO"], round(x, 3), round(y, 3), pr.get("Z"), "" if v == -9999 else round(v, 3)])
print("ok")
