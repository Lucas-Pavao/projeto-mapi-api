# F1-FRONT-02 — Leitura dirigida do front (Agente 2)

Escopo: `projeto-mapi-front`, working tree (14 arquivos modificados, 1 removido: `flood.service.test.ts`). Somente leitura. Caminhos abaixo relativos a `projeto-mapi-front/`.
Severidade: critico / importante / desejavel. Esforço: P (<0,5 dia), M (1–3 dias), G (>3 dias).

## Resumo

- O front é pequeno (6.804 linhas em `src/`) mas concentrado: 3 arquivos somam 3.651 linhas (`ui/map.tsx` 2.037, `MapView.tsx` 1.106, `FloodPointDetailCard.tsx` 508).
- Risco `UNKNOWN` (e valor ausente) é tratado de **três formas diferentes** em três componentes, e em dois deles aparece como risco (vermelho no marcador, verde "Baixo" no card). Para a Defesa Civil é o achado mais grave.
- Erro de API é silencioso (apenas `console.error`); não há estado de erro, "último dado válido" nem aviso de dado velho.
- Tipos TS são escritos à mão; o schema zod valida mas descarta o resultado; não há geração a partir do OpenAPI.
- Datas assumem hora local do navegador; não há utilitário de fuso nem leitura de campo de fuso.
- Acessibilidade: 0 ocorrências de `aria-label` em `src/features` (31 `<button>/<Button>`); marcadores não são focáveis por teclado.

## 1. Estrutura por feature e contrato

**Conforme:**
- axios só em `src/lib/api.ts:1` e nos `services/` (grep de `axios|fetch(` fora de `services/` e `ui/map.tsx` retorna só `lib/api.ts`). Nenhum componente chama HTTP.
- Sem `any` encontrado em `features/`.
- `ui/map.tsx` não contém regra de domínio (é wrapper genérico do MapLibre).

**Violações / desvios:**

- **F1-F2-01 (importante, M)** Tipos duplicados à mão vs. schema. `types/flood.ts:4-92`, `types/common.ts`, `types/sensor.ts` são interfaces escritas à mão; `schemas/floodPointResponse.schema.ts:13-52` redeclara um subconjunto do mesmo contrato em paralelo (nenhum `z.infer`). Só `/api/pontos*` tem schema; `sensors/latest`, `environmental-data`, `flood-predictions`, `tide` e `eventos` não têm validação (`flood.service.ts:34-57`, `sensor.service.ts:6-13`). Não há geração a partir do OpenAPI: `package.json` não tem `openapi-typescript`/`orval` (grep em `package.json` e `vite.config.ts` vazio). Isto contraria a regra da skill (tipo vem de `z.infer` ou do gerado).
- **F1-F2-02 (importante, P)** `validateFloodPoint` só loga e **devolve o `raw`** (`schemas/floodPointResponse.schema.ts:54-63`): o resultado do `safeParse` é descartado, então o schema não protege o tipo. Além disso `riskLevel: z.string()` (`:18`, `types/flood.ts:58`) em vez de `z.enum(['LOW','MEDIUM','HIGH','UNKNOWN'])` — impede `switch` exaustivo com `never` e deixa passar `CRITICAL`, que o backend não emite (ver F1-F2-04).
- **F1-F2-03 (desejavel, P)** `types/flood.ts:4-92` tem nomes que divergem da convenção de campos do backend/glossário em pontos: `FloodPredictionResponseDTO.estimatedTimeToEvent: string` (no schema é `nullable().optional()`, `schema.ts:19`) — tipo e schema discordam. `common.ts:73-89` (`UnifiedDataDTO`) tem campos `number` não anuláveis para dado que pode faltar.
- **F1-F2-14 (desejavel, P)** Divergência com AGENTS.md §4 / skill §2: a base URL **não** usa `import.meta.env.VITE_API_URL` no código; `lib/api.ts:3-9` usa caminhos relativos (same-origin via nginx/proxy) e `VITE_API_URL` só aparece no proxy de `vite.config.ts:~28`. É decisão defensável (cookie httpOnly), mas diverge do texto normativo → ADR ou ajuste do AGENTS.md.
- **F1-F2-15 (desejavel, P)** Dependências mortas/mal classificadas: `react-map-gl` está em `package.json` mas não é importado em `src/` (só comentário em `App.tsx:6`); `shadcn` (CLI) está em `dependencies`.
- `services/map.service.ts:1-26` é um barril que espalha todos os serviços em um objeto único e reexporta `@/lib/api` (acopla tudo; `mapService.getLatestBySensorId` é usado em `MapView.tsx:173`). Desejável remover.

## 2. Estado de servidor × UI

- Único hook de dados: `hooks/useMapData.ts:6-43` — `useEffect` + `setInterval(fetchData, 30000)` (`:34-36`), sem TanStack Query, sem cache, sem `AbortController`.
- **F1-F2-05 (importante, M)** Sem cancelamento nem deduplicação: o polling de 30 s dispara `sensors/latest` + `/api/pontos` mesmo com a aba oculta (sem `visibilitychange`), e dois ciclos podem se sobrepor se a API ficar lenta (`setInterval` não espera o anterior). Em erro, o estado antigo é mantido silenciosamente (`useMapData.ts:22-27`).
- **F1-F2-06 (critico, M)** Busca de dado dentro de componente-contêiner e em hook de interação, com efeito de escrita no backend: `MapView.tsx:145-257` tem um `useEffect` de ~110 linhas que busca status do ponto, compõe/normaliza o DTO, faz fan-out de N chamadas `getLatestBySensorId` (`:170-179`, N = `nearbySensorIds`, sem limite) e muta o objeto retornado (`data.preciseData.temperature = …`, `:231-238`). `useMapInteractions.ts:44-96` repete o padrão em handlers async (`handleMapClick`, `handleSensorClick`). Cada seleção chama `floodService.getPreciseData` → `createFloodPrediction`, que é **POST** e "grava auditoria em flood_predictions" (comentário em `flood.service.ts:41-43`). Resultado: cada clique no mapa, em sensor, e cada seleção de ponto (mais um segundo POST via `getPreciseData` no `MapView.tsx:154`) cria linha de auditoria e aciona a IA. Em tempo de uso real isso polui a tabela de predições e acopla leitura a escrita. Sem cancelamento: cliques rápidos sobrepõem respostas (`handleMapClick` sem guarda de "última requisição vence"; só o efeito de ponto usa `isMounted`).
- `useMapInteractions` retorna 28 valores (inclui todos os setters), expondo estado de UI e de servidor misturados (`floodPointStatus`, `locationInfo`, `sensorStatus` são dado de servidor em `useState`).
- Estado de UI (camadas ativas, modais, severidade do relato) concentrado em `MapView` (`:128-140`, 13 `useState` no componente raiz).

## 3. Performance do mapa e bundle

Cleanup (conforme): `MapCircle`, `MapRoute`, `MapArc`, `MapClusterLayer` removem layer/source no retorno do efeito (`ui/map.tsx:1149-1150, 1325-1327, 1598-1600, 1848-1850`) e atualizam por `source.setData` em efeito separado (`:1159-1170, 1335-1341, 1609-1615`). `MapMarker` faz `marker.remove()` (`:485-493`). Listeners têm `off` correspondente (`:285-289, 471-481`). `Map` guarda `onClick` em ref (`:196-202`), então o `handleMapClick` não memoizado do `MapView.tsx:507` não recria o mapa.

Problemas:
- **F1-F2-07 (importante, G)** Sensores e pontos críticos são **marcadores DOM**, um `MapMarker` + portal React por item (`MapView.tsx:603-726`), cada um com `animate-ping` infinito (pontos: sempre, `:721`; sensores: ao selecionar) e `backdrop`/sombras. Não há camada GeoJSON/symbol nem cluster (existe `MapClusterLayer` em `ui/map.tsx:1746`, não usado). Escala mal com centenas de estações (APAC/ANA/Cemaden) e em celular. Não medi nº de sensores (depende da API); por inspeção, o custo cresce linearmente em nós DOM + re-render.
- **F1-F2-08 (importante, M)** Re-render amplo: `MapView` mantém `sensors`, `floodPoints`, `floodPointStatus`, `locationInfo`, `sensorStatus` e 13 `useState` no mesmo componente (AGENTS.md §2.1). Todo poll de 30 s faz `setSensors/setFloodPoints` com arrays novos (`useMapData.ts:20-21`) → todos os marcadores re-renderizam, e `getDataFreshness`/`getSensorConfig` são recalculados por sensor a cada render (`MapView.tsx:604-605`). Sem `React.memo` nos marcadores. Callbacks inline no JSX (`:477-496, 615-625, 691-701`) e `handleExportCsv`/`handleMapClick`/handlers de modal sem `useCallback` (`:259-354`, `useMapInteractions.ts:34-96`). `LocationInitializer` depende de `onLocationUpdate` (`MapView.tsx:93`) — é `setUserLocation` estável, ok.
- **F1-F2-16 (desejavel, P)** `MapPopup` em `selectedFloodPoint` com `floodPoints` re-criado no poll: `selectedFloodPoint` vem de `manualSelectedFloodPoint` (objeto antigo congelado, `useMapInteractions.ts:29-32`), então o popup **não** reflete o novo `floodPrediction` do poll enquanto estiver aberto — dado potencialmente velho sem aviso.
- **F1-F2-17 (desejavel, P)** `useMapInteractions.ts` e `MapView.tsx:259-274, 276-298, 300-333, 335-354` usam `alert()` para sucesso/erro (6 chamadas), bloqueante e sem acessibilidade.
- Sem GeoJSON grande em `useState`: conforme (não há camada GeoJSON do domínio). Sem camada raster de terreno ainda.

Bundle (`ls -la dist/assets`, build existente de 05/set):

| arquivo | bytes | gzip (medido com `gzip -c`) |
|---|---|---|
| `MapPage-Cgxpg_1S.js` | 1.215.244 | 313.875 |
| `index-DKqBql0-.js` | 314.748 | 102.075 |
| `index-b3yqTXoR.css` | 92.395 | — |
| `MapPage-B2k4QVOw.css` | 69.808 | — |
| `LoginPage-BS6R1Rhy.js` | 4.833 | — |
| 3 fontes Geist woff2 | 58.400 (soma) | — |

O code-splitting por rota existe (`App.tsx:8-9`, `lazy`), então o login não paga o mapa. O chunk do mapa (1,2 MB / ~314 KB gz) é dominado por `maplibre-gl` (dep dominante; `react-map-gl` ocioso, F1-F2-15). Em 4G lento (≈1,6 Mbps) ~314 KB gz leva ~1,6 s só de rede. `index-*.js` de 315 KB para login+router+axios+zod+lucide é alto (provável import amplo de `lucide-react`); não verificado com analisador. **F1-F2-18 (desejavel, P)**: medir com `rollup-plugin-visualizer`; não rodei build.

## 4. Acessibilidade

- **F1-F2-09 (importante, M)** Controles sem rótulo acessível: 0 `aria-label` em `src/features` com 31 botões. Icônicos sem nome: logout (`MapView.tsx:460-467`), camadas (`:548-558`), configurações (`:598-600`, **sem `onClick`**, botão morto), fechar análise (`:796-803`), toggle de camadas (`:577`, só texto visual). Só `ui/map.tsx` tem `aria-label` ("Close popup" `:562`, controles de zoom `:803`). `title=` é usado em um único lugar útil (`MapView.tsx:1086`).
- **Navegação por teclado:** marcadores são `div` criados por `new MapLibreGL.Marker({element: div})` (`ui/map.tsx:435-441`), sem `tabindex`/`role`/`keydown` (grep `tabindex|role|keydown` em `map.tsx` vazio). Sensor/ponto só abre por clique de mouse. Lista de sensores na análise é `<div onClick>` (`MapView.tsx:925-939`) sem foco. **O sidebar (`SensorSidebar.tsx:20-22, 77-79`) usa `<button>` e é o único caminho por teclado**, mas fica `hidden` abaixo de `md` (`MapView.tsx:472`). Modais (`FloodPointDetailCard.tsx:92`, Report*) são `div absolute` sem `role="dialog"`, sem trap de foco, sem `Escape` (grep de `role=` vazio fora de `ui/`).
- **Foco visível:** `button.tsx:7` e `input.tsx:12` têm `focus-visible:ring-3`; mas os `<button>` crus (`MapView.tsx:577`, `SensorSidebar.tsx:77`, `:85-97`) não têm estilo de foco (só `transition-all`), então o anel padrão do navegador some sobre fundo `bg-black/40`. Parcial.
- **Cor como único sinal:** marcadores de risco diferem só por cor, mas todos usam o mesmo ícone `AlertTriangle` (`MapView.tsx:712`); o texto do nível só existe dentro do popup. Pessoa daltônica não distingue LOW/MEDIUM/HIGH no mapa.
- **Contraste** (calculado, WCAG relativa; fundo `--background #09090b`, `theme.css`):

| nível | cor usada | origem | contraste vs. fundo | branco sobre a cor (ícone) |
|---|---|---|---|---|
| LOW | `#10b981` (emerald-500) | `MapView.tsx:676`, `FloodPointPopup.tsx:38` | 7,84:1 | 2,54:1 (reprova 3:1 de ícone) |
| MEDIUM | `#f59e0b` (amber-500) | `:675` | 9,26:1 | 2,15:1 (reprova) |
| HIGH | `#f97316` (orange-500) | `:674` | 7,10:1 | 2,80:1 (reprova) |
| CRITICAL | `#ef4444` / marcador `bg-red-600 #dc2626` | `:390, :673` | 5,29:1 / 4,12:1 | 4,83:1 (ok) |
| UNKNOWN | **sem cor própria no marcador: cai em `bg-red-600`** (`:677`); no popup `bg-zinc-700 #3f3f46` + texto `#a1a1aa` (`FloodPointPopup.tsx:40`) | — | 1,91:1 / texto 7,76:1 | 10,44:1 |

Os valores são hex Tailwind; não há oklch para risco (os `oklch` em `theme.css` são `--destructive`/`--chart-*`, não usados para risco). Texto secundário `zinc-600 #52525b` sobre fundo escuro = 2,57:1 e `zinc-500 #71717a` = 4,12:1 (ambos abaixo de 4,5:1) e é usado em rótulos de 8–9 px (125 ocorrências de `text-[7..10px]`, 65 delas de 8–9 px) → **F1-F2-10 (importante, M)**: contraste e tamanho de fonte reprovam AA para texto pequeno.

## 5. Responsividade

Breakpoints usados: `sm:` 3×, `md:` 12×, `lg:` 9× (só em `SensorDetailCard`, `FloodPointDetailCard`, `SensorSidebar`, `MapView:453,472`). Sem `xl`/`2xl`.

- **390 px (celular):** (a) o sidebar some (`hidden md:flex`, `MapView.tsx:472`) e **não há substituto** (sem drawer/lista): em celular o usuário só tem marcadores. (b) painel "Análise de Local" é `absolute left-[416px] w-[420px]` fixo (`MapView.tsx:782`) → começa a 416 px e tem 420 px: totalmente fora da tela de 390 px (inutilizável). (c) `FloodPointPopup` tem `w-[360px]` (`:45`) dentro de popup do MapLibre: ocupa 92% da largura, sem espaço para o marcador. (d) as duas ilhas do topo (`MapView.tsx:438, 451`) somam ≈ 270 + 190 px e se sobrepõem em 390 px (o nome do usuário some em <sm, `:453`). (e) o botão "Reportar Alagamento Local" (`:1078`) fica em `right-20` e colide com `MapControls`. **F1-F2-11 (importante, M)**.
- **Projeção 1920 px+:** layout cresce só no mapa; o sidebar fica fixo em 340 px, e a tipografia é de 8–11 px (`text-[8px]`, `text-[9px]` em quase todo rótulo), ilegível a 3–5 m de distância numa sala de situação. Sem modo "painel de projeção" (alto contraste, fontes maiores, sem backdrop-blur). Os cards de detalhe têm `max-w-6xl` (`FloodPointDetailCard.tsx:92`), ok em 1920. **F1-F2-12 (desejavel, M)**.
- Tema: `Map theme="dark"` fixo (`MapView.tsx:506`); `theme.css` define `.dark` igual a `:root` → não há modo claro real (AGENTS.md §6 pede Dark/Light).

## 6. Estados de erro e vazio

- **API cai:** `useMapData.ts:22-27` só faz `console.error`/`console.warn('Backend (localhost:8080)...')`; nenhum estado de erro na UI. `isLoading` vira `false` (`:28-30`) e o mapa aparece vazio como se não houvesse sensores/pontos; o cabeçalho continua dizendo **"Sistemas Online"** com bolinha verde pulsando, **fixo no código** (`MapView.tsx:443-444`). O usuário não distingue "API fora" de "nada a mostrar". Idem `MapView.tsx:245-246` (status do ponto: só console) e `useMapInteractions.ts:57-58, 92-93`. Não há horário do último dado válido (exigido pela skill §4). **F1-F2-04 (critico, M)** (junto do UNKNOWN abaixo).
- **Rótulo "Admin" fixo** para qualquer usuário (`MapView.tsx:455`), "Convidado" como fallback — informação falsa sobre o papel; desejável corrigir.
- **Risco UNKNOWN:** tratado de três formas:
  1. Marcador (`MapView.tsx:671-684`): `UNKNOWN`, `undefined` e qualquer outro valor → `bg-red-600` / `bg-red-400` (mesma cor que `CRITICAL`). Ou seja, ponto "sem previsão" parece crítico.
  2. Popup (`FloodPointPopup.tsx:34-40`): UNKNOWN → cinza "Em Análise" (estado neutro; é o mais próximo do desejado, mas diz "em análise", não "previsão indisponível").
  3. Card de detalhe (`FloodPointDetailCard.tsx:80-85`): o `return` final é **verde "Baixo"** para qualquer coisa que não seja CRITICAL/HIGH/MEDIUM, inclusive UNKNOWN e `prediction` ausente (header verde, borda verde, rótulo "Baixo").
  Resultado: o mesmo ponto sem previsão aparece vermelho no mapa, cinza no popup e **verde/"Baixo" no detalhe**. UNKNOWN **aparece com a mesma cor e rótulo de LOW** no card, violando a skill §4 ("nunca cor de risco baixo"). Cada componente reimplementa o mapeamento (ao menos 4 cópias: `MapView.tsx:389-394, 672-684`, `FloodPointPopup.tsx:33-40`, `FloodPointDetailCard.tsx:80-85`); não há utilitário único exaustivo. **F1-F2-04 (critico, M)**.
- `CRITICAL`: o front trata 4 níveis, com `CRITICAL` dominante; o contrato do backend/skill é LOW/MEDIUM/HIGH/UNKNOWN. `CRITICAL` existe só na severidade de relato manual (`types/flood.ts:41, 51`, `ReportFloodModal.tsx:46`). Confirmar com o ADR/glossário (texto "CRITICAL" no README do front `:30` também). Rótulos pt-BR: HIGH="Alto", MEDIUM="Moderado", LOW="Baixo" — consistentes entre popup e card.
- **Sem dado:** card sem previsão mostra texto de preenchimento "Coletando telemetria ambiental para processar recomendação automatizada via redes neurais..." (`FloodPointDetailCard.tsx:250`), que sugere processamento em curso mesmo quando a IA está fora (README admite que a IA "ainda não está funcional"). Probabilidade ausente → `--` (`FloodPointPopup.tsx:~108`). Métricas ausentes → `--` sem unidade.
- Falha na previsão é engolida em `getPreciseData` (`flood.service.ts:60-63`, `console.error` + `undefined`): coerente com o comentário, mas a UI não avisa que a previsão falhou.
- `ErrorBoundary` existe (`components/ErrorBoundary.tsx`, com teste), ok.

## 7. Hierarquia: atual × previsão × histórico

- Popup (`FloodPointPopup.tsx:45-197`): cabeçalho com nome + hora; "hero" de **probabilidade da IA** em fonte de 60 px (`:~95`, `text-6xl`) e chip de nível; depois "Rede de Sensores (3 km)" (top 3 leituras). Ou seja, **a previsão da IA domina**; o dado observado (chuva, nível) vem em segundo plano, e a IA hoje está indisponível.
- Card (`FloodPointDetailCard.tsx`): bloco de probabilidade + "Impacto Previsto" (`estimatedTimeToEvent`, `:209-216`) + mensagem da IA entre aspas (`:250`), depois métricas: Status da Maré (`:278`), **Acumulado Chuva 24 h** com 3 h/6 h/12 h (`:283-303`), previsto de maré do porto (`:346-349`), sensores (`:404-433`), ações de relato/exportação (`:454-474`).
- **Atual × previsão × histórico não são distinguidos visualmente:** não há rótulos "agora / previsão / histórico", nem horizonte da previsão, nem série histórica (só agregados 3/6/12/24 h de chuva). A "tábua de maré prevista" e a "maré monitorada" aparecem lado a lado no painel de local (`MapView.tsx:977-986`) sem indicar que um é previsão. O selo "Em tempo real" é fixo (`FloodPointDetailCard.tsx:~130`, com pulso), mesmo com dado de horas atrás — a única indicação de idade é `getDataFreshness` nos sensores.
- **Legenda de risco:** não existe (grep `legend|legenda` em `features/` vazio). A explicação de cor está só no rótulo dentro do popup. Defesa Civil não vê o que significa "Moderado" × "Alto" (probabilidade? ação recomendada?), nem a faixa de probabilidade por classe.
- **Painel por ponto mostra** (por ponto, `FloodPointDetailCard`): nível de risco (rótulo pt-BR), probabilidade, tempo estimado de impacto, chuva acumulada 24 h (+3/6/12 h) em mm, maré (m, 2 casas), nível d'água do sensor (m), vazão. **Não mostra:** lâmina d'água prevista/observada na via (o único "nível" é `waterLevel` de sensor de rio, não lâmina no ponto), **causa provável** (maré × chuva × rio — o front não recebe campo de causa; `FloodPredictionResponseDTO` só tem 4 campos, `types/flood.ts:56-61`), cota/altitude do ponto (`altitudeM` e `distanceToChannelM` existem no DTO `types/flood.ts:12-13` mas grep mostra que não são exibidos no card), nem confiança/AUC do modelo. **F1-F2-13 (importante, M)**. Para a dissertação: depende de o backend expor causa e lâmina.
- Unidades: parte dos valores usa unidade vinda da API (`unitPrecipitation || 'mm'`, `MapView.tsx:848`), mas tide/chuva do card usam `formatValue` sem unidade em linha (`FloodPointDetailCard.tsx:317`) e o painel de maré hardcoda `m` (`MapView.tsx:980, 984`).

## 8. Fuso horário

Todas as datas passam por `utils/sensor.ts:94-125`:
- `formatApiTimestamp` (`:94-116`): para array `[Y,M,D,h,m,s]` usa `new Date(y, m-1, d, h, m, s)` — **interpreta como hora local do navegador**; para string usa `new Date(String(ts))` — string ISO sem `Z`/offset também é lida como **local** (ECMAScript). Só com `Z`/offset é UTC. Em seguida `.toISOString()` converte para UTC, "lavando" o erro: o resultado parece UTC mas veio de hora local.
- Exibição: `toLocaleTimeString('pt-BR', {hour, minute})` sem `timeZone` em `SensorSidebar.tsx:192`, `FloodPointPopup.tsx:77`, `FloodPointDetailCard.tsx:132, 415`, `SensorPopup.tsx:190`; `toLocaleString('pt-BR')` em `SensorDetailCard.tsx:90`. Todos usam fuso do navegador, não `America/Recife`.
- Escritas: `new Date().toISOString()` (UTC) em `MapView.tsx:187, 285-286` (relato de evento `startTime/endTime`) — o backend hoje grava hora local sem fuso, logo o evento reportado fica deslocado em 3 h em relação aos dados lidos.
- Nome do CSV com `new Date().toISOString().split('T')[0]` (`MapView.tsx:266`): após 21:00 em Recife a data do arquivo já é do dia seguinte (UTC).
- `getDataFreshness` (`utils/sensor.ts:143-158`) compara `Date.now()` com o timestamp: se o backend manda hora local sem fuso e o navegador está em UTC-3, o sensor aparece como atrasado/adiantado em até 3 h (fresh ≤20 min, aging ≤120 min, stale >120 min), erro do mesmo tamanho dos limiares.
- Consequência para ADR 0001: enquanto o histórico é hora local sem fuso, o front funciona **só** se o navegador estiver em America/Recife; com navegador em outro fuso, o selo "Atualizado/Sem resposta" erra. Após a migração para UTC (strings com `Z`) o parser acima passa a funcionar sem mudança, mas a exibição ainda depende do fuso do navegador. Recomenda-se um único `formatRecife(ts)` com `Intl.DateTimeFormat('pt-BR', {timeZone:'America/Recife'})` e um teste de contrato com e sem `Z`. **F1-F2-19 (importante, P)**.

## 9. README (não editado)

`README.md` (já modificado no working tree; +8/−7):
- `:95` "Para executar todo o ecossistema MAPI (**Edge, AI, API e Front**)": desatualizado; o Edge foi descontinuado (o próprio README diz isso em `:24`). Contradição interna.
- `:97` link do guia aponta para a branch `feature/docs-readme-refactor` (`.../tree/feature/docs-readme-refactor`), branch de feature, não `main` — link frágil.
- `:24` nota de arquitetura OK (menciona Edge/MQTT apenas como descontinuado).
- `:8-17` diagrama ok; sem MQTT. `:30` cita CRITICAL como nível de IA; conferir com o contrato.
- `:41-48` tabela de tecnologias omite zod, axios, vitest, base-ui; `:51-66` estrutura de pastas omite `schemas/` e `utils/` de `features/map`.
- `:77-79` `VITE_API_URL=http://seu-backend...` descrito como configuração, mas o código usa caminhos relativos e só o proxy lê essa variável (ver F1-F2-14).
- **F1-F2-20 (desejavel, P)**.

## Observações adicionais

- O arquivo `flood.service.test.ts` foi removido no working tree (`D`); permanecem só `floodPointResponse.schema.test.ts` e `ErrorBoundary.test.tsx` (2 arquivos de teste para 6,8 kLOC). Nenhum teste de componente, de `getDataFreshness`/`formatApiTimestamp` ou do mapeamento de risco. **F1-F2-21 (importante, M)**: cobrir ao menos fuso e risco UNKNOWN.
- `useAuth` não expõe papel; "Admin" fixo (acima).

## Tabela de achados

| id | título | severidade | dissertação | arquivos | métrica |
|---|---|---|---|---|---|
| F1-F2-01 | Tipos TS escritos à mão, sem geração do OpenAPI; zod cobre só `/api/pontos` | importante | s | `types/flood.ts`, `types/common.ts`, `types/sensor.ts`, `schemas/floodPointResponse.schema.ts:13-52` | 1 de ~12 endpoints com schema; 0 geradores no `package.json` |
| F1-F2-02 | `validateFloodPoint` descarta o parse; `riskLevel` é `z.string()` | importante | s | `schemas/floodPointResponse.schema.ts:18,54-63`, `types/flood.ts:58` | 0 enums de risco tipados |
| F1-F2-03 | Tipo e schema discordam (`estimatedTimeToEvent`, anuláveis do `UnifiedDataDTO`) | desejavel | n | `types/flood.ts:56-61`, `schema.ts:19`, `types/common.ts:73-89` | — |
| F1-F2-04 | UNKNOWN: vermelho no marcador, cinza no popup, verde "Baixo" no card; sem estado de erro de API; "Sistemas Online" fixo | critico | s | `MapView.tsx:389-394,443-444,671-684`, `FloodPointPopup.tsx:34-40`, `FloodPointDetailCard.tsx:80-85`, `useMapData.ts:22-27` | 3 tratamentos distintos; ≥4 cópias do mapeamento; 0 telas de erro |
| F1-F2-05 | Polling sem abort, sem pausa em aba oculta, sobreposição possível | importante | n | `useMapData.ts:12-36` | 30 s fixos; 2 requests/ciclo |
| F1-F2-06 | Leitura dispara POST de predição (auditoria + IA) a cada clique/seleção; fan-out N+1; busca em contêiner de 110 linhas | critico | s | `MapView.tsx:145-257`, `useMapInteractions.ts:34-96`, `flood.service.ts:41-63` | 1–2 POST por interação; N chamadas `sensors/{id}/latest` sem teto |
| F1-F2-07 | Sensores e pontos são marcadores DOM com animação infinita; `MapClusterLayer` não usado | importante | n | `MapView.tsx:603-726`, `ui/map.tsx:1746` | 1 nó+portal por item; `animate-ping` em todo ponto |
| F1-F2-08 | `MapView` com 13 `useState`, 1.106 linhas, callbacks sem `useCallback`, sem memo nos marcadores | importante | n | `MapView.tsx:128-140,259-354,477-496,603-605` | 1.106 linhas; 28 valores no retorno do hook |
| F1-F2-09 | Botões e ícones sem rótulo; marcadores e modais sem teclado/`role="dialog"`; botão "Configurações" morto | importante | n | `MapView.tsx:460,548,598,796,925`, `ui/map.tsx:435-441` | 0 `aria-label` em 31 botões |
| F1-F2-10 | Contraste e fontes pequenas: ícone branco sobre LOW/MEDIUM/HIGH < 3:1; texto zinc-500/600 < 4,5:1; 125 usos de `text-[7–10px]` | importante | s | `MapView.tsx:672-684`, `FloodPointPopup.tsx`, `theme.css` | LOW 2,54:1; MEDIUM 2,15:1; HIGH 2,80:1; zinc-600 2,57:1; UNKNOWN fundo 1,91:1 |
| F1-F2-11 | Inutilizável em 390 px: sidebar oculto sem substituto; painel `left-[416px] w-[420px]`; popup `w-[360px]` | importante | n | `MapView.tsx:472,782,1078`, `FloodPointPopup.tsx:45` | 416+420 = 836 px mínimos |
| F1-F2-12 | Sem modo projeção (1920 px+): fontes 8–11 px, sem alto contraste; só tema escuro | desejavel | n | `MapView.tsx:506`, `theme.css`, vários | 65 usos de 8–9 px |
| F1-F2-13 | Painel por ponto sem causa provável, lâmina, cota, horizonte da previsão; sem legenda; atual/previsão/histórico não rotulados | importante | s | `FloodPointDetailCard.tsx:209-349`, `types/flood.ts:12-13,56-61` | 0 ocorrências de legenda; 4 campos de previsão |
| F1-F2-14 | Base URL relativa em vez de `VITE_API_URL` (diverge de AGENTS.md); documentar ou ADR | desejavel | n | `lib/api.ts:3-9`, `vite.config.ts`, `README.md:77` | — |
| F1-F2-15 | `react-map-gl` sem uso; `shadcn` em `dependencies` | desejavel | n | `package.json` | 1 dependência morta |
| F1-F2-16 | Popup aberto não atualiza com o poll (objeto selecionado congelado) | desejavel | s | `useMapInteractions.ts:29-32` | até 30 s+ de dado velho sem aviso |
| F1-F2-17 | `alert()` bloqueante para sucesso/erro | desejavel | n | `MapView.tsx:272,289,294,302,329,345,350` | 7 chamadas |
| F1-F2-18 | Bundle do mapa 1,2 MB (314 KB gz); índice de 315 KB (102 KB gz) sem análise | desejavel | n | `dist/assets/MapPage-Cgxpg_1S.js`, `index-DKqBql0-.js` | 1.215.244 B; 314.748 B |
| F1-F2-19 | Datas lidas e exibidas em hora local do navegador; sem utilitário `America/Recife`; ADR 0001 | importante | s | `utils/sensor.ts:94-125,143-158`, `SensorSidebar.tsx:192`, `FloodPointPopup.tsx:77`, `FloodPointDetailCard.tsx:132,415`, `SensorDetailCard.tsx:90`, `MapView.tsx:266,285` | 6 pontos de formatação; erro de 3 h vs. limiares de 20/120 min |
| F1-F2-20 | README desatualizado: "Edge, AI, API e Front"; link para branch de feature; estrutura incompleta | desejavel | n | `README.md:95,97,41-66` | 1 contradição interna |
| F1-F2-21 | Cobertura de teste mínima; `flood.service.test.ts` removido; sem teste de fuso/risco | importante | s | `src/**/*.test.*` | 2 arquivos de teste para 6.804 linhas |
