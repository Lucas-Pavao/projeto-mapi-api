#!/usr/bin/env bash
# MAPi — terreno PE3D: reproduz inventário, mosaico, recortes COG, atributos por ponto e comparação.
# Uso: tools/terreno/run.sh [etapa ...]   (sem argumentos roda todas)
# Etapas: altitude inventario vrt cobertura recortes atributos comparar estimativas validar testes cog3857 tiles grade_altitude (whitebox roda sozinho dentro de atributos e validar)
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
TETO_KB=$(( ${TETO_MB:-1024} * 1024 ))                     # teto das saídas (padrão 1 GB; TETO_MB sobrescreve)

checa_disco() {
  local livre; livre=$(df --output=avail -k "$OUT" | tail -1)
  df -h /home / | cat
  [ "$livre" -ge "$MIN_LIVRE_KB" ] || { echo "ABORTA: menos de 1,5 GB livres" >&2; exit 1; }
  local usado; usado=$(du -sk "$OUT" | cut -f1)
  [ "$usado" -le "$TETO_KB" ] || { echo "ABORTA: saídas passam do teto (TETO_MB)" >&2; exit 1; }
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

# Recorte do terreno em EPSG:31985 (xmin ymin xmax ymax). BBOX_MODE=pontos (padrão histórico): bbox dos pontos de
# cobertura.csv + 2 km. BBOX_MODE=mosaico: extensão inteira do mosaico.vrt (todas as folhas baixadas), para a camada
# cobrir o Recife todo e não só a faixa em volta dos pontos.
BBOX_MODE="${BBOX_MODE:-pontos}"
bbox_recorte() {
  if [ "$BBOX_MODE" = mosaico ]; then
    dk gdalinfo -json /terreno/mosaico.vrt | python3 -c 'import sys,json; c=json.load(sys.stdin)["cornerCoordinates"]; print("%.3f %.3f %.3f %.3f" % (c["lowerLeft"][0], c["lowerLeft"][1], c["upperRight"][0], c["upperRight"][1]))'
  else
    tail -n +2 "$OUT/cobertura.csv" | awk -F, 'NR==1{xa=xb=$3;ya=yb=$4} {if($3<xa)xa=$3;if($3>xb)xb=$3;if($4<ya)ya=$4;if($4>yb)yb=$4}
      END{printf "%.3f %.3f %.3f %.3f", xa-2000, ya-2000, xb+2000, yb+2000}'
  fi
}

# cog3857 (ADR 0008, decisão C1): COG único em EPSG:3857 da cota bruta, vizinho mais próximo, para o front ler por range requests.
# Recorte = bbox dos pontos de cobertura.csv + 2 km (em EPSG:31985). Saída: $OUT/cog3857/terrain_3857.tif e terrain_manifest.json.
# Espaço: o driver COG grava um temporário não comprimido (~0,8 GB) além do COG final; exige 1,5 GB livres antes de começar.
etapa_cog3857() {
  checa_disco
  [ -s "$OUT/mosaico.vrt" ] || { echo "ABORTA: rode a etapa vrt antes" >&2; exit 1; }
  mkdir -p "$OUT/cog3857"
  local bbox; bbox=$(bbox_recorte)
  echo "bbox EPSG:31985 (xmin ymin xmax ymax) = $bbox"
  rm -f "$OUT/cog3857/terrain_3857.tif"   # o driver COG não abre arquivo existente para atualizar
  local t0 t1; t0=$(date +%s.%N)
  # shellcheck disable=SC2086
  dk gdalwarp -of COG -t_srs EPSG:3857 -r near -te_srs EPSG:31985 -te $bbox -srcnodata -9999 -dstnodata -9999 \
    -co COMPRESS=DEFLATE -co PREDICTOR=3 -co BLOCKSIZE=256 -co OVERVIEWS=AUTO -co OVERVIEW_RESAMPLING=NEAREST \
    -co BIGTIFF=IF_SAFER -co NUM_THREADS=ALL_CPUS -wo NUM_THREADS=ALL_CPUS --config GDAL_CACHEMAX 1024 \
    /terreno/mosaico.vrt /terreno/cog3857/terrain_3857.tif
  t1=$(date +%s.%N)
  local seg; seg=$(awk -v a="$t0" -v b="$t1" 'BEGIN{printf "%.1f", b-a}')
  dk gdalinfo -json /terreno/cog3857/terrain_3857.tif > "$OUT/cog3857/terrain_gdalinfo.json"
  local vrt_sha cog_sha; vrt_sha=$(sha256sum "$OUT/mosaico.vrt" | cut -d' ' -f1); cog_sha=$(sha256sum "$OUT/cog3857/terrain_3857.tif" | cut -d' ' -f1)
  dk python3 -I /tools/terreno/manifesto_cog.py "$vrt_sha" "$cog_sha" "$seg" "$bbox" "$IMG_TAG@$IMG_DIGEST"
  ls -l "$OUT/cog3857"
}

# tiles (ADR 0008, decisao efetiva C3 apos C1 reprovar): PMTiles de PNG ja coloridos, zooms 12-17, a partir do COG 3857.
# Cadeia: gdaldem color-relief (paleta_hipsometrica.txt) -> RGBA temporario -> gdal_translate MBTiles (zoom 17) -> gdaladdo average -> corta z<12 -> pmtiles convert.
# Intermediarios (RGBA, MBTiles) sao apagados ao fim; fica tiles/terrain_hypsometry.pmtiles + terrain_manifest.json. Exige 1,5 GB livres.
PMTILES_VER="1.31.2"
PMTILES_TGZ_SHA256="3ed7dbf4ec2e6dfe5e25b6f70d1ffc932729f93c86db353bf514dd71010a312f"   # go-pmtiles_1.31.2_Linux_x86_64.tar.gz (BSD-3-Clause)
# Variantes (F3-FRONT-03), por ambiente: ZOOM_MIN (padrao 12), BBOX_MODE (ver bbox_recorte), TILE_FORMAT=PNG|PNG8|WEBP (padrao PNG), TILE_QUALITY (WEBP, padrao 90), ZOOM_MAX (padrao 17; o MBTiles nasce no zoom nativo 17 e z > ZOOM_MAX e cortado),
# TILES_VARIANT (sufixo da pasta de saida: vazio = tiles/; "webp-z16" = tiles-webp-z16/). Sem variaveis, o resultado e o de F3-FRONT-02.
ZOOM_MIN="${ZOOM_MIN:-12}"; ZOOM_MAX="${ZOOM_MAX:-17}"; TILE_FORMAT="${TILE_FORMAT:-PNG}"; TILE_QUALITY="${TILE_QUALITY:-90}"; TILES_VARIANT="${TILES_VARIANT:-}"
etapa_tiles() {
  checa_disco
  [ -s "$OUT/cog3857/terrain_3857.tif" ] || { echo "ABORTA: rode a etapa cog3857 antes" >&2; exit 1; }
  local P="$OUT/pmtiles"; local TD="tiles${TILES_VARIANT:+-$TILES_VARIANT}"; mkdir -p "$P" "$OUT/$TD"
  if [ ! -x "$P/pmtiles" ]; then
    curl -sL -m 300 -o "$P/p.tgz" "https://github.com/protomaps/go-pmtiles/releases/download/v${PMTILES_VER}/go-pmtiles_${PMTILES_VER}_Linux_x86_64.tar.gz"
    echo "$PMTILES_TGZ_SHA256  $P/p.tgz" | sha256sum -c - || { echo "ABORTA: hash do pmtiles difere" >&2; exit 1; }
    tar -xzf "$P/p.tgz" -C "$P" pmtiles LICENSE && rm "$P/p.tgz"
  fi
  local pms; pms=$(sha256sum "$P/pmtiles" | cut -d' ' -f1)
  local bbox; bbox=$(bbox_recorte)
  local T="$OUT/$TD"; rm -f "$T/_rgba.tif" "$T/_z.mbtiles" "$T/terrain_hypsometry.pmtiles"
  local TQ=(); if [ "$TILE_FORMAT" = WEBP ]; then TQ=(-co QUALITY="$TILE_QUALITY"); fi
  local t0 t1; t0=$(date +%s.%N)
  dk gdaldem color-relief -alpha -of GTiff -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=IF_SAFER \
    /terreno/cog3857/terrain_3857.tif /tools/terreno/paleta_hipsometrica.txt /terreno/$TD/_rgba.tif
  checa_disco >/dev/null
  dk gdal_translate -of MBTiles -co TILE_FORMAT=$TILE_FORMAT ${TQ[@]+"${TQ[@]}"} -co MINZOOM=17 -co MAXZOOM=17 -co RESAMPLING=NEAREST \
    /terreno/$TD/_rgba.tif /terreno/$TD/_z.mbtiles
  rm -f "$T/_rgba.tif"
  # Fatores de redução do zoom nativo 17 até ZOOM_MIN (12 -> 2..32; 10 -> 2..128).
  local niveis=(); local f=2; for ((z=16; z>=ZOOM_MIN; z--)); do niveis+=("$f"); f=$((f*2)); done
  dk gdaladdo -r average /terreno/$TD/_z.mbtiles "${niveis[@]}"
  dk python3 -I /tools/terreno/cortaz.py /terreno/$TD/_z.mbtiles $ZOOM_MIN $ZOOM_MAX
  "$P/pmtiles" convert "$T/_z.mbtiles" "$T/terrain_hypsometry.pmtiles"
  t1=$(date +%s.%N)
  rm -f "$T/_z.mbtiles"
  local seg; seg=$(awk -v a="$t0" -v b="$t1" 'BEGIN{printf "%.1f", b-a}')
  "$P/pmtiles" show "$T/terrain_hypsometry.pmtiles" > "$T/pmtiles_show.txt"
  "$P/pmtiles" show --header-json "$T/terrain_hypsometry.pmtiles" > "$T/pmtiles_header.json"
  local vrt_sha cog_sha pal_sha; vrt_sha=$(sha256sum "$OUT/mosaico.vrt" | cut -d' ' -f1); cog_sha=$(sha256sum "$OUT/cog3857/terrain_3857.tif" | cut -d' ' -f1)
  pal_sha=$(sha256sum "$AQUI/paleta_hipsometrica.txt" | cut -d' ' -f1)
  dk python3 -I /tools/terreno/manifesto_tiles.py "$vrt_sha" "$cog_sha" "$pal_sha" "$seg" "$bbox" "$IMG_TAG@$IMG_DIGEST" "$PMTILES_VER" "$pms" "$TD" "$TILE_FORMAT" "$TILE_QUALITY"
  ls -l "$T"
}

# grade_altitude: grade de 5 m (média) do mosaico inteiro, gravada como int16 em centímetros para a API consultar a cota
# de qualquer ponto clicado no mapa (GET /api/environmental-data -> localElevation). Saída: $OUT/altitude/ (≈ 46 MB).
etapa_grade_altitude() {
  checa_disco
  [ -s "$OUT/mosaico.vrt" ] || { echo "ABORTA: rode a etapa vrt antes" >&2; exit 1; }
  mkdir -p "$OUT/altitude"; rm -f "$OUT/altitude/mdt_5m.tif"
  dk gdalwarp -tr 5 5 -r average -srcnodata -9999 -dstnodata -9999 -ot Float32 -co COMPRESS=DEFLATE -co TILED=YES \
    /terreno/mosaico.vrt /terreno/altitude/mdt_5m.tif
  local vrt_sha; vrt_sha=$(sha256sum "$OUT/mosaico.vrt" | cut -d' ' -f1)
  dk python3 -I /tools/terreno/grade_altitude.py /terreno/altitude/mdt_5m.tif /terreno/altitude "$vrt_sha" "$IMG_TAG@$IMG_DIGEST"
  rm -f "$OUT/altitude/mdt_5m.tif"
  ls -l "$OUT/altitude"
}

ETAPAS=("$@"); [ ${#ETAPAS[@]} -gt 0 ] || ETAPAS=(altitude inventario vrt cobertura recortes atributos comparar estimativas validar testes)
for e in "${ETAPAS[@]}"; do echo "== $e"; "etapa_$e"; checa_disco >/dev/null; done
echo "OK. Saídas em $OUT ($(du -sh "$OUT" | cut -f1))"
