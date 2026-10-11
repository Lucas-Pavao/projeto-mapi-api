"""Grava terrain_manifest.json do COG 3857 (chamado por run.sh cog3857)."""
import json, sys, os
vrt, cog, seg, bbox, img = sys.argv[1:6]
info = json.load(open('/terreno/cog3857/terrain_gdalinfo.json'))
ov = info['bands'][0].get('overviews', [])
m = {
 'vrt_sha256': vrt, 'vrt_arquivo': 'mosaico.vrt',
 'bbox_epsg31985': dict(zip(['xmin','ymin','xmax','ymax'], map(float, bbox.split()))),
 'bbox_regra': 'bbox dos pontos de cobertura.csv + 2000 m',
 'bbox_epsg3857': {'xmin': info['cornerCoordinates']['lowerLeft'][0], 'ymin': info['cornerCoordinates']['lowerLeft'][1],
                   'xmax': info['cornerCoordinates']['upperRight'][0], 'ymax': info['cornerCoordinates']['upperRight'][1]},
 'crs_saida': 'EPSG:3857', 'resampling': 'near', 'resampling_overviews': 'NEAREST',
 'nodata': -9999, 'tipo': info['bands'][0]['type'],
 'compressao': {'COMPRESS': 'DEFLATE', 'PREDICTOR': 3, 'BLOCKSIZE': 256},
 'tamanho_pixels': info['size'], 'tamanho_pixel_m_3857': abs(info['geoTransform'][1]),
 'overviews': [{'tamanho': o['size']} for o in ov], 'n_overviews': len(ov),
 'gdal_versao': info.get('driverShortName') and open('/terreno/gdal_version.txt').read().strip(),
 'gdal_imagem': img,
 'cog_arquivo': 'terrain_3857.tif', 'cog_bytes': os.path.getsize('/terreno/cog3857/terrain_3857.tif'), 'cog_sha256': cog,
 'tempo_geracao_s': float(seg),
 'referencia_vertical': 'cota do MDT (datum nao confirmado, ADR 0009); sem relacao com mare',
}
json.dump(m, open('/terreno/cog3857/terrain_manifest.json', 'w'), indent=2, ensure_ascii=False)
print(json.dumps(m, indent=2, ensure_ascii=False))
