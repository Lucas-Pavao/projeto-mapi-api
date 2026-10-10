#!/usr/bin/env bash
# MAPi — terreno PE3D: reproduz inventário, mosaico, recortes COG, atributos por ponto e comparação.
# Uso: tools/terreno/run.sh [etapa ...]   (sem argumentos roda todas)
# Etapas: altitude inventario vrt cobertura recortes atributos comparar estimativas
# Requer: docker, curl. Nunca descompacta os zips (lê por /vsizip). Saídas em ../terreno (fora do git).
set -euo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"                      # .../MAPI
DADOS="${DADOS:-$RAIZ/Modelo digital de terreno (RASTER)}"
OUT="${OUT:-$RAIZ/terreno}"
IMG_TAG="ghcr.io/osgeo/gdal:ubuntu-small-3.10.0"
IMG_DIGEST="sha256:383e17c221d48031c5ac4fe7500d95adccb05d719a9685542c1e8d2d84ef50e0"
IMG="ghcr.io/osgeo/gdal@${IMG_DIGEST}"
MIN_LIVRE_KB=$((1500*1024))                               # aborta se /home < 1,5 GB livres
TETO_KB=$((1024*1024))                                    # teto de 1 GB para as saídas

checa_disco() {
  local livre; livre=$(df --output=avail -k "$OUT" | tail -1)
  df -h /home / | cat
  [ "$livre" -ge "$MIN_LIVRE_KB" ] || { echo "ABORTA: menos de 1,5 GB livres" >&2; exit 1; }
  local usado; usado=$(du -sk "$OUT" | cut -f1)
  [ "$usado" -le "$TETO_KB" ] || { echo "ABORTA: saídas passam de 1 GB" >&2; exit 1; }
}
mkdir -p "$OUT/recortes"
checa_disco
dk() { docker run --rm -u "$(id -u):$(id -g)" -e GDAL_IMAGE="$IMG_TAG@$IMG_DIGEST" \
  -v "$DADOS":/dados:ro -v "$OUT":/terreno -v "$AQUI":/tools/terreno:ro "$IMG" "$@"; }
py() { dk python3 -I /tools/terreno/terreno.py "$@"; }

etapa_altitude() {   # altitudeM atual = elevation do Open-Meteo (MapiServiceImpl.createFloodPoint, linhas 187-193)
  echo "slug,elevation_open_meteo_m,consultado_em_utc,url_base" > "$OUT/altitude_open_meteo.csv"
  tail -n +2 "$AQUI/pontos_criticos.csv" | while IFS=, read -r slug nome lat lon; do
    e=$(curl -s -m 30 "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&timezone=auto" \
        | sed -n 's/.*"elevation":\([-0-9.]*\).*/\1/p')
    echo "$slug,$e,$(date -u +%FT%TZ),https://api.open-meteo.com/v1/forecast" >> "$OUT/altitude_open_meteo.csv"
  done
  cat "$OUT/altitude_open_meteo.csv"
}
etapa_inventario() { py inventario; }
etapa_vrt() {
  dk sh -c 'ls /dados/MDT-*.zip | while read z; do b=$(basename "$z" .zip); echo "/vsizip/$z/$b.tif"; done > /terreno/lista_folhas.txt
    gdalbuildvrt -a_srs EPSG:31985 -srcnodata -9999 -vrtnodata -9999 -input_file_list /terreno/lista_folhas.txt /terreno/mosaico.vrt
    gdalinfo /terreno/mosaico.vrt > /terreno/mosaico_gdalinfo.txt; gdalinfo --version > /terreno/gdal_version.txt'
  sha256sum "$OUT/mosaico.vrt" | tee "$OUT/mosaico.vrt.sha256"
}
etapa_cobertura() { py cobertura; }
etapa_recortes() {   # COG 1 km x 1 km por ponto coberto (projwin alinhado à grade, vindo de cobertura.csv)
  tail -n +2 "$OUT/cobertura.csv" | while IFS=, read -r slug x y dentro col lin cota valido folha xo yo n fv ulx uly lrx lry; do
    [ "$valido" = "True" ] || { echo "pula $slug (sem pixel válido)"; continue; }
    dk gdal_translate -of COG -co COMPRESS=DEFLATE -co PREDICTOR=3 -projwin "$ulx" "$uly" "$lrx" "$lry" \
      /terreno/mosaico.vrt "/terreno/recortes/$slug.tif"
    ls -l "$OUT/recortes/$slug.tif"
  done
  du -sb "$OUT/recortes"/*.tif | tee "$OUT/recortes_tamanhos_bytes.txt"
}
etapa_atributos() { checa_disco; py atributos; }
etapa_comparar() { py comparar; }
etapa_estimativas() { py estimativas; }

ETAPAS=("$@"); [ ${#ETAPAS[@]} -gt 0 ] || ETAPAS=(altitude inventario vrt cobertura recortes atributos comparar estimativas)
for e in "${ETAPAS[@]}"; do echo "== $e"; "etapa_$e"; checa_disco >/dev/null; done
echo "OK. Saídas em $OUT ($(du -sh "$OUT" | cut -f1))"
