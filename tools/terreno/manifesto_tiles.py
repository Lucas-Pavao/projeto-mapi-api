"""Grava terrain_manifest.json de tiles/ (chamado por run.sh tiles). Argumentos: vrt_sha cog_sha pal_sha seg_total bbox img pmtiles_ver pmtiles_sha"""
import json, sys, os, hashlib, sqlite3, subprocess
vrt, cog, pal, seg, bbox, img, pmv, pms = sys.argv[1:9]
D = '/terreno/tiles'
f = f'{D}/terrain_hypsometry.pmtiles'
h = hashlib.sha256()
with open(f, 'rb') as fh:
    for b in iter(lambda: fh.read(1 << 20), b''):
        h.update(b)
hd = json.load(open(f'{D}/pmtiles_header.json'))
txt = dict(l.split(': ', 1) for l in open(f'{D}/pmtiles_show.txt').read().splitlines() if ': ' in l)
m = {
 'artefato': 'terrain_hypsometry.pmtiles', 'formato': 'PMTiles v3, tiles PNG RGBA 256 px, ja coloridos (cota nao esta no tile)',
 'zoom_min': hd['minzoom'], 'zoom_max': hd['maxzoom'],
 'bbox_epsg31985': dict(zip(['xmin','ymin','xmax','ymax'], map(float, bbox.split()))),
 'bbox_regra': 'bbox dos pontos de cobertura.csv + 2000 m', 'bbox_wgs84': hd['bounds'],
 'paleta': {'arquivo': 'tools/terreno/paleta_hipsometrica.txt', 'sha256': pal, 'conteudo': open('/tools/terreno/paleta_hipsometrica.txt').read()},
 'entrada_cog_sha256': cog, 'vrt_sha256': vrt,
 'ferramentas': {'gdal_imagem': img, 'gdal_versao': open('/terreno/gdal_version.txt').read().strip(), 'pmtiles_versao': pmv, 'pmtiles_binario_sha256': pms},
 'resampling': {'gdaldem': 'color-relief (linear em RGB)', 'zoom_max': 'NEAREST (grade do COG)', 'zooms_menores': 'AVERAGE (gdaladdo)'},
 'n_tiles_enderecados': int(txt['addressed tiles count']), 'n_tiles_unicos': int(txt['tile contents count']),
 'tamanho_bytes': os.path.getsize(f), 'sha256': h.hexdigest(),
 'tempo_geracao_s': float(seg), 'tempo_geracao_nota': 'soma das etapas gdaldem + MBTiles + overviews + pmtiles convert',
 'referencia_vertical': 'cota do MDT (datum nao confirmado, ADR 0009); sem relacao com mare',
}
json.dump(m, open(f'{D}/terrain_manifest.json', 'w'), indent=2, ensure_ascii=False)
print(json.dumps({k: v for k, v in m.items() if k != 'paleta'}, indent=2, ensure_ascii=False))
