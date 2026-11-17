#!/usr/bin/env bash
# Sobe os serviços com o commit de cada repositório gravado na imagem (alerta 20 min, critério 8).
# Uso: scripts/implantar.sh [serviço...]   (padrão: mapi-ai mapi-api mapi-front)
# Confere depois: /actuator/info (api), /health (ia), /version.json (front) contra `git rev-parse HEAD` da main.
set -euo pipefail
cd "$(dirname "$0")/.."
raiz=..
commit() { git -C "$1" rev-parse HEAD; }
sujo() { [ -z "$(git -C "$1" status --porcelain --untracked-files=no)" ] && echo false || echo true; }
export GIT_COMMIT=$(commit .) GIT_DIRTY=$(sujo .) GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
export AI_GIT_COMMIT=$(commit $raiz/projeto-mapi-ai) AI_GIT_DIRTY=$(sujo $raiz/projeto-mapi-ai)
export FRONT_GIT_COMMIT=$(commit $raiz/projeto-mapi-front) FRONT_GIT_DIRTY=$(sujo $raiz/projeto-mapi-front)
servicos=("$@"); [ ${#servicos[@]} -eq 0 ] && servicos=(mapi-ai mapi-api mapi-front)
docker compose up -d --build "${servicos[@]}"
echo "api=$GIT_COMMIT ($GIT_DIRTY) ia=$AI_GIT_COMMIT ($AI_GIT_DIRTY) front=$FRONT_GIT_COMMIT ($FRONT_GIT_DIRTY)"
