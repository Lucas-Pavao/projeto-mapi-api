#!/usr/bin/env bash
# Etapa 0 (ADR 0023): verificação que exige ADMIN. Uso:
#   MAPI_USER=lucas MAPI_PASS='...' bash scripts/etapa0-verificar.sh
# 1) baixa a PED no período da coleta ao vivo (só acrescenta; janelas já gravadas são puladas),
# 2) refaz a unificação e imprime a tabela (canônicas, unificadas, divergências),
# 3) imprime o resumo do acumulado medido de 3 h por estação.
set -euo pipefail
API=${API:-http://localhost:8080}
: "${MAPI_USER:?defina MAPI_USER}" "${MAPI_PASS:?defina MAPI_PASS}"
TOKEN=$(curl -sf -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg u "$MAPI_USER" --arg p "$MAPI_PASS" '{username:$u,password:$p}')" | jq -r .accessToken)
H=(-H "Authorization: Bearer $TOKEN")

FROM=${FROM:-2026-11-07T00:00:00Z}
TO=${TO:-$(date -u +%Y-%m-%dT00:00:00Z)}
id=$(curl -sf "${H[@]}" -X POST "$API/api/admin/ingestion/runs" -H 'Content-Type: application/json' \
  -d "{\"source\":\"CEMADEN_PED\",\"from\":\"$FROM\",\"to\":\"$TO\",\"mode\":\"REFETCH\",\"kind\":\"BACKFILL\"}" | jq -r '.runId // (.[]|tostring)')
echo "run PED $id ($FROM a $TO)"
while :; do
  st=$(curl -sf "${H[@]}" "$API/api/admin/ingestion/runs/$id" | jq -r .status)
  [ "$st" != RUNNING ] && break; sleep 10
done
echo "status: $st"

echo "== unificação =="
curl -sf "${H[@]}" -X POST "$API/api/admin/stations/unify" | jq 'del(.merges)'

echo "== acumulado medido de 3 h =="
curl -sf "${H[@]}" "$API/api/stations/rain-accumulations" | jq '{computedAtUtc, stale, attribution,
  estacoes: (.stations|length), com_3h: ([.stations[]|select(.acc3hMm!=null)]|length),
  sem_dado: ([.stations[]|select(.status=="NO_DATA")]|length),
  maior_3h: ([.stations[]|select(.acc3hMm!=null)]|max_by(.acc3hMm)|{canonicalId,name,acc3hMm})}'
