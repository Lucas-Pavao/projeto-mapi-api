# Contrato — novos cenários (duas fontes Cemaden, chuva do ponto, xgb_v2, eventos de chuva)

- Data: 2026-11-15 · Tarefa: NC-0 (orquestrador) · Referência visual: canvas "MAPi — Telas", versão 36 (`sim.js`).
- Regras de domínio: ADRs 0017, 0018, 0019, 0021, 0022, 0023 e 0007 (emenda 2). Este arquivo só fixa a forma dos dados.
- Instantes em UTC (ISO-8601 com `Z`). Chuva em mm. Ausente = `null`, nunca `0`.
- Citação obrigatória onde houver dado Cemaden: `DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC` (campo `attribution`).

## Inventário (fase 1)

| Item | Estado | Ação |
|---|---|---|
| `GET /api/stations/canonical` | parcial: tem aliases; falta status do mês, quarentena e divergências | B1 |
| `GET /api/stations/rain-accumulations` (com `NO_DATA`) | existe | — |
| Série horária por estação com fonte usada e pico removido | falta | B2 |
| Candidatas da chuva do ponto (até 5 km) | falta | B3 |
| Grade estação × mês | falta | B4 |
| `POST /api/admin/stations/unify`, `POST /api/admin/chuva-ponto/rebuild` | existem | — |
| Campos do xgb_v2 na previsão | existem; `PredictionSummaryDTO` sem `rainSource` | B5 |
| Métricas do xgb_v2 | falta endpoint; vira JSON estático gerado do projeto-mapi-ai (`reports/model_card.json`) | orquestrador |
| Eventos de chuva por fonte | existem (`/api/analise/eventos-chuva` e `/resumo`, `source` = `CEMADEN_PED`, `ANA`, `OPEN_METEO_ARCHIVE`) | — |
| Front: limiares 0,5/0,8 fixos | `ProbabilityChart.tsx` e `MobilePointSheet.tsx` (faixas do SVG) | F |

## B1 — `GET /api/stations/canonical` (campos novos, por adição)

```json
{
  "canonicalId": "261160621A", "name": "Areias", "municipality": "RECIFE", "ibgeCode": "2611606",
  "lat": -8.102, "lon": -34.929, "primarySource": "CEMADEN_PED",
  "aliases": ["CEMADEN:261160621A", "CEMADEN_PED:261160621A"],
  "sources": ["CEMADEN", "CEMADEN_PED"],
  "monthCheck": { "monthStartUtc": "2026-10-01T00:00:00Z", "status": "OK", "reasons": [], "ruleVersion": "chuva-ponto-v4" },
  "quarantine": { "GAP": 0, "AFTER_GAP": 3, "SUSPECT_QUALIFIER": 1 },
  "divergenceCount": 2,
  "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"
}
```

- `sources`: fontes com apelido (`CEMADEN`, `CEMADEN_PED`). Só PED = `["CEMADEN_PED"]`.
- `monthCheck`: último mês de `station_month_check` da estação PED, `rule_version` vigente (`chuva-ponto-v4`); `null` se nunca checada. `status` ∈ `OK | UNCHECKED | FAIL | OFFLINE` (no banco é `PASS`; a API devolve `OK`). `reasons` ⊂ `PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH, OVERCATCH, FEW_WET_NEIGHBORS`.
- `quarantine`: contagem em `ingestion_quarantine` por motivo, todas as fontes da estação (apelidos).
- `divergenceCount`: linhas em `station_hour_divergence`.

## B2 — `GET /api/stations/{canonicalId}/hourly?from=&to=` (máx. 7 dias)

```json
{
  "canonicalId": "261160621A", "from": "...Z", "to": "...Z", "toleranceMm": 0.5,
  "hours": [
    { "hourEndUtc": "...Z", "valueMm": 12.4, "usedSource": "CEMADEN_PED", "pedMm": 12.4, "liveMm": 11.0,
      "divergent": true, "isolatedPeakRemoved": false }
  ],
  "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"
}
```

- Uma linha por hora da janela, inclusive sem dado (`valueMm = null`, `usedSource = null`).
- `usedSource`: PED se houver PED na hora; senão `CEMADEN` (ao vivo); senão `null`. Regra da `canonical_rain_hourly_v`.
- `divergent`: |PED − ao vivo| > 0,5 mm (mesma regra de `station_hour_divergence`).
- `isolatedPeakRemoved`: a hora tem ≥ 10 mm e ≥ 2 vizinhas a até 6 km < 1 mm (`RainSpatialCheck`). Nesse caso `valueMm = null`: a tela mostra o marcador, não o valor.

## B3 — `GET /api/pontos/{slug}/rain-candidates?at=` (`at` = fim da hora, UTC; padrão: última hora com série)

```json
{
  "slug": "AV_RECIFE_IBURA", "hourEndUtc": "...Z", "radiusM": 5000, "ruleVersion": "chuva-ponto-v4",
  "valueMm": 3.2, "usedStationId": "261160615A",
  "candidates": [
    { "stationId": "261160615A", "name": "Ibura", "distanceM": 690, "valueMm": 3.2,
      "monthStatus": "OK", "reasons": [], "role": "USED" }
  ],
  "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"
}
```

- `role`: `USED` (maior valor entre válidas), `LOWER` (válida, valor menor), `EXCLUDED` (mês `FAIL` ou `OFFLINE`; motivo em `reasons`), `NO_VALUE` (válida sem valor na hora).
- Válida = mês `OK` ou `UNCHECKED` (ADR 0022). Ordem: distância.
- Fonte: `point_station_link`, `station_month_check`, `observation_hourly_v` (CEMADEN_PED) e `point_rain_hourly`.

## B4 — `GET /api/admin/stations/month-checks?from=&to=` (ADMIN)

```json
{ "ruleVersion": "chuva-ponto-v4", "months": ["2026-06-01T00:00:00Z"],
  "stations": [ { "stationId": "261160621A", "name": "Areias",
    "cells": [ { "monthStartUtc": "...Z", "status": "OK", "reasons": [] } ] } ] }
```

## B5 — `PredictionSummaryDTO` ganha `rainSource`.

## Previsão (já existe, ADR 0007 emenda 2) — o front consome

`modelVersion`, `riskThresholds {medium, high, source}`, `rainSource`, `trainingData`, `syntheticWarning`,
`contributors[] {featureId, label, contribution, value, unit}`, `imputedFeatures[]`, `degradationReason`.

- Nível no front: **nunca** recalcular com constante. Quando precisar de nível a partir de uma probabilidade
  (série, relatório), usar `riskOf(p, riskThresholds)` com `>=`; sem `riskThresholds`, não classificar.
- Hoje: moderado ≥ 0,0159, alto ≥ 0,2441 (`source = "metadata"`).

## Métricas do xgb_v2 — JSON estático

`projeto-mapi-ai/reports/model_card.json` (gerado por `python -m mapi_ai.model_card`), copiado para
`projeto-mapi-front/src/features/model/data/xgb_v2_model_card.json`. Campos: `modelVersion`, `trainingData`,
`synthetic {n, positiveRate, seed}`, `thresholds`, `calibration`, `rows[] {id, name, prAuc, rocAuc, brier}`,
`ablations[] {label, delta, lo, hi}`, `xai {top5[], jaccard, spearman, lime10, delShap, delLime, delRandom, planBSpearman}`,
`sources {arquivo: sha256}`.

## B6 — `GET /api/stations/{canonicalId}/card?at=` (card do pluviômetro, seção 5.1)

Uma chamada com tudo do card. `at` = instante de referência (padrão: agora); blocos de 10 min das 3 h anteriores.

```json
{
  "canonicalId": "261160621A", "name": "Areias", "aliases": ["CEMADEN:261160621A", "CEMADEN_PED:261160621A"],
  "aliasDistanceM": 12, "sources": ["CEMADEN", "CEMADEN_PED"], "pedOnly": false,
  "state": "DIVERGENT",
  "nowMmH": 3.2, "nowSource": "CEMADEN_PED", "liveMmH": 2.0, "pedMmH": 3.2, "pedPending": false,
  "blocks": [ { "endUtc": "...Z", "mm": 0.4, "source": "CEMADEN_PED", "peakRemoved": false } ],
  "acc1hMm": 2.1, "acc3hMm": 5.0, "acc24hMm": null,
  "note": { "kind": "DIVERGENT", "hourEndUtc": "...Z", "liveMm": 2.0, "pedMm": 3.2, "diffMm": 1.2 },
  "monthCheck": { "monthStartUtc": "...Z", "status": "OK", "reasons": [] },
  "quarantineToday": { "GAP": 0, "AFTER_GAP": 1, "SUSPECT_QUALIFIER": 0 },
  "uses": [ { "slug": "AV_RECIFE_IBURA", "name": "Av. Recife - Entrada do Ibura", "distanceM": 688,
              "role": "USED", "usedStationName": null } ],
  "lastReadingUtc": "...Z", "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"
}
```

- `blocks`: uma entrada por bloco de 10 min das 3 h (18), inclusive sem dado (`mm = null`, `source = null`). Fonte por bloco: PED se houver; senão ao vivo.
- Acumulados: `null` ("incompleto") se qualquer bloco/hora da janela faltar. Nunca 0 por ausência.
- `state` (prioridade, o primeiro que vale): `NO_RESPONSE` (nenhuma leitura de nenhuma fonte nas 3 h) → `GAP_NOW` (nenhuma leitura de nenhuma fonte em (at − 70 min, at], com alguma nas 3 h: o bloco seco da coleta ao vivo dura 60 min, então "bloco de 10 min vazio" não é lacuna) → `FAIL_MONTH` → `DIVERGENT` (divergência > 0,5 mm nas últimas 2 h) → `PEAK_REMOVED` (pico isolado nas 3 h) → `UNCHECKED` → `NORMAL`. `pedOnly` é etiqueta à parte.
- `note`: o aviso do `state` (um só), com os números do caso; `null` em `NORMAL`.
- `uses`: pontos críticos a até 5 km; `role` = `USED` | `LOWER` (com `usedStationName`) | `EXCLUDED` (mês FAIL/OFFLINE) | `NO_VALUE`, na hora de `at` (série do ponto quando houver; senão o valor horário canônico).
- `pedPending`: `true` só se a PED não tem valor na hora, a coleta ao vivo tem, e há leitura PED nas 3 h anteriores. PED parada há mais tempo não é atraso (conferência com dado real, 2026-11-16: a carga PED parou em 2026-10-07).
- `lastReadingUtc`: última leitura até `at`. Em `NO_RESPONSE`, procura nos 30 dias anteriores; `null` se não houver.
- `monthCheck`: mês de `at`; `null` enquanto o mês não foi checado (a checagem roda sobre o mês fechado). Candidata sem checagem sai com `monthStatus = UNCHECKED` em B3 e B7, nunca `null`.
- 404 se a canônica não existe.

## B7 — `GET /api/locations/analysis?lat=&lon=&at=` (análise de local, seção 5.4)

Substitui o uso de `/api/environmental-data` no painel "Análise de local" do mapa. Cache de 10 min por célula de ~200 m.

```json
{
  "lat": -8.1039, "lon": -34.9509, "at": "...Z", "inArea": true, "isSea": false,
  "rain": {
    "mode": "MEASURED",
    "nowMmH": 3.2, "acc3hMm": 5.0, "acc24hMm": null,
    "station": { "stationId": "261160615A", "name": "Ibura", "distanceM": 2400 },
    "candidates": [ { "stationId": "...", "name": "...", "distanceM": 2400, "valueMmH": 3.2,
                      "monthStatus": "OK", "reasons": [], "role": "USED", "lat": -8.12, "lon": -34.95 } ],
    "nValid": 3
  },
  "nearestPoint": { "slug": "AV_RECIFE_IBURA", "name": "...", "distanceKm": 1.8, "riskLevel": "LOW", "probability": 0.002,
                    "degradationReason": null },
  "tide": { "levelM": 1.81, "rising": true, "harbor": "Porto do Recife", "verticalReference": "NR DHN" },
  "river": null,
  "terrain": { "elevationM": 4.2, "distanceToCoastKm": 3.1 },
  "weather": { "temperatureC": 27.1, "humidityPct": 76, "windKmh": 14, "source": "OPEN_METEO" },
  "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"
}
```

- `rain.mode`: `MEASURED` (há estação válida no raio de 5 km com valor na hora) | `GAP` (há estação válida, nenhuma com valor: **não** troca por estimativa; valores `null`) | `ESTIMATED` (nenhuma estação válida no raio: valores do Open-Meteo, `station = null`). Regra do ADR 0022: maior valor entre as válidas.
- `candidates`: todas as estações a até 5 km, com `role` `USED | LOWER | EXCLUDED | NO_VALUE` e coordenadas (o mapa desenha as linhas).
- `nearestPoint`: ponto crítico mais próximo, com a última previsão (UNKNOWN sem `probability`).
- `river`: régua mais próxima a até 6 km `{stationId, name, levelM, distanceKm, outsideModel: true}` ou `null`.
- `terrain`: `null` fora da cobertura PE3D. `isSea`/`inArea`: `isSea` = altitude ≤ 0 fora do MDT e a leste da costa; `inArea` = dentro do retângulo da RMR usado hoje (documentar a regra no código). Fora da área: `rain.mode = ESTIMATED`, `terrain = null`.
- `lat`/`lon` da resposta = coordenada pedida (o cache pode ser por célula de ~200 m, mas o eco é do clique).
- Pico isolado (≥ 10 mm, ≥ 2 vizinhas < 1 mm a até 6 km) sai antes da escolha da `USED`, como no B2/B3 (ADR 0022).
- `terrain.distanceToCoastKm`: `null` enquanto não houver linha de costa no sistema (limitação registrada).
- 400 se lat/lon inválidos.
