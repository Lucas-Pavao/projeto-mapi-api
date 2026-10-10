#!/usr/bin/env bash
# MAPi — terreno PE3D: reproduz inventário, mosaico, recortes COG, atributos por ponto e comparação.
# Uso: tools/terreno/run.sh [etapa ...]   (sem argumentos roda todas)
# Etapas: altitude inventario vrt cobertura recortes atributos comparar estimativas validar testes (whitebox roda sozinho dentro de atributos e validar)
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
dk() { docker run --rm -u "$(id -u):$(id -g)" -e GDAL_IMAGE="$IMG_TAG@$IMG_DIGEST" -e MOTOR="${MOTOR:-whitebox}" \
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
etapa_atributos() { checa_disco; etapa_whitebox; py atributos; }
etapa_comparar() { py comparar; }
etapa_testes() {   # pytest (pure-python, baixado e fixado por versão em $OUT/pylibs) no mesmo container GDAL
  [ -d "$OUT/pylibs/pytest" ] || dk python3 -I /tools/terreno/baixa_pytest.py /terreno/pylibs
  docker run --rm -u "$(id -u):$(id -g)" -e PYTHONPATH=/terreno/pylibs -e PYTHONDONTWRITEBYTECODE=1 \
    -v "$OUT/pylibs":/terreno/pylibs:ro -v "$AQUI":/tools/terreno:ro "$IMG" \
    python3 -m pytest -p no:cacheprovider -q /tools/terreno/tests
}
WBT_ZIP_SHA256="370493b569aaf84977fde95921f96d4a39c1820a2d36d907da9d53772295f440"   # WhiteboxTools v2.4.0 linux amd64
etapa_whitebox() {   # baixa o binário oficial (hash fixado) para $OUT/whitebox; o terreno.py o executa por janela
  local W="$OUT/whitebox"; mkdir -p "$W"
  if [ ! -x "$W/WBT/whitebox_tools" ]; then
    curl -sL -m 600 -o "$W/wbt.zip" https://www.whiteboxgeo.com/WBT_Linux/WhiteboxTools_linux_amd64.zip
    echo "$WBT_ZIP_SHA256  $W/wbt.zip" | sha256sum -c - || { echo "ABORTA: hash do Whitebox difere" >&2; exit 1; }
    dk python3 -I -c "import zipfile;z=zipfile.ZipFile('/terreno/whitebox/wbt.zip');[z.extract(n,'/terreno/whitebox') for n in z.namelist() if n.startswith('WhiteboxTools_linux_amd64/WBT/') and not n.endswith('/')]"
    mv "$W/WhiteboxTools_linux_amd64/WBT" "$W/WBT"; rm -rf "$W/WhiteboxTools_linux_amd64" "$W/wbt.zip"; chmod +x "$W/WBT/whitebox_tools"
  fi
  "$W/WBT/whitebox_tools" --version | head -1 | tee "$W/versao.txt"; sha256sum "$W/WBT/whitebox_tools" | tee "$W/binario.sha256"
}
etapa_validar() { etapa_whitebox; py validar; }   # Priority-Flood próprio x Whitebox -> validacao_whitebox.csv/json
etapa_estimativas() { py estimativas; }

ETAPAS=("$@"); [ ${#ETAPAS[@]} -gt 0 ] || ETAPAS=(altitude inventario vrt cobertura recortes atributos comparar estimativas validar testes)
for e in "${ETAPAS[@]}"; do echo "== $e"; "etapa_$e"; checa_disco >/dev/null; done
echo "OK. Saídas em $OUT ($(du -sh "$OUT" | cut -f1))"
