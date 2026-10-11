# F2-FRONT-01 — ADR 0007, passo 1 (front)

Compatível com o backend atual (0.0 + UNKNOWN) e com o novo (null + `degradationReason`). Sem commit; trabalho sobre as 14 mudanças não commitadas do autor.

## Arquivos (repo projeto-mapi-front, `src/features/map/`)
- Editados: `schemas/floodPointResponse.schema.ts` (enum com `.catch` + warn, `floodProbability` nullable, campos novos, `validateFloodPoint` agora devolve o parse, novo `validateFloodPrediction`), `types/flood.ts` (DTO deriva do schema via `z.infer`), `services/flood.service.ts` (valida `createFloodPrediction`), `components/MapView.tsx`, `FloodPointPopup.tsx`, `FloodPointDetailCard.tsx`, `schemas/floodPointResponse.schema.test.ts` (`toBe` virou `toEqual`, pois o parse devolve nova cópia).
- Novos: `utils/risk.ts` (mapeamento único, `switch` com `never`, `formatProbability`, `degradationText`), `utils/datetime.ts` (`formatRecife`, `Intl` com `America/Recife`), `components/PredictionUnavailableNotice.tsx` e 3 arquivos de teste.
- Removidos: CRITICAL/EXTREME da predição e as animações atreladas a CRITICAL; o texto fixo "Neural Engine v2.4" (agora "Modelo <modelVersion>", só quando houver). `CRITICAL` da severidade de relato manual (`ReportFloodModal`, `types/flood.ts`) permanece: é outro conceito.

## Como testar
`npm run lint` (0 erros, 3 warnings já existentes), `npx tsc -b --noEmit` (limpo), `npm test` (20 passam). Manual: ponto com `riskLevel: UNKNOWN` mostra cinza "Previsão indisponível" + motivo no marcador, popup e card, sem "%".

## Medidas (antes → depois)
- Tratamentos distintos de UNKNOWN: 3 → 1 (`getRiskVisual('UNKNOWN')`).
- Cópias do mapeamento risco→visual: 4 (MapView x2, Popup, Card) → 1 função usada pelos 3 componentes.
- Testes: 5 → 20 (2 → 5 arquivos).
- Linhas com `riskLevel|CRITICAL|EXTREME` em código não-teste (`grep` em `src/`): 30 → 19 (o restante é schema, mapeamento e relato manual).
- Chamadas `toLocale*` restantes fora do utilitário novo: 6 (SensorDetailCard, SensorPopup, FloodPointPopup, FloodPointDetailCard x2, SensorSidebar), fora `utils/sensor.ts`. Não refatoradas.

## Ficou de fora
- Hora de `precise.timestamp` no popup/card continua em hora do navegador (F1-F2-19 aberto); só issuedAt/validUntil/lastValidIssuedAt usam `formatRecife`.
- Remoção do `.catch` e de `estimatedTimeToEvent` (passo 4, fim da Fase 4); "Sistemas Online" fixo, estado de erro de API, legenda, POST em leitura (F1-F2-06).
- `contributors`/`imputedFeatures` só no schema, sem UI.
- Teste de render só para `PredictionUnavailableNotice`; popup e card não têm teste de render.
- Contraste AA da cor neutra (zinc) não validado (F1-F2-10).

## Patch
`/tmp/claude-1000/-home-lucas-Documents-Projetos-MAPI-projeto-mapi-api/e2d0d341-b55c-4a7a-ae2c-e5b91c63ce09/scratchpad/F2-FRONT-01.patch` (`diff -ruN front-before front-after`, só as mudanças desta tarefa).
