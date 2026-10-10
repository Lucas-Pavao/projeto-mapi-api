# F4-FRONT-01a — Acessibilidade, responsividade e estados de erro/vazio

Repositório `projeto-mapi-front` (mudanças não commitadas, sobre as do autor e as do F2-FRONT-01). Achados tratados: F1-F2-04, -09, -10, -11, -12, -17 (e a parte de legenda de -13). Patch: `scratchpad/F4-FRONT-01a.patch` (`diff -ruN`, 31 arquivos, só src/).

## Antes/depois

| Métrica | Antes | Depois | Como medi |
|---|---|---|---|
| Botões só-ícone sem nome acessível | 8 (logout, camadas, configurações, fechar análise, fechar detalhe do ponto, botão ArrowUpRight sem ação, fechar cenário, fechar detalhe do sensor) | 0 | script de regex em JSX + inspeção manual dos 34 `<button>/<Button>`; 3 sinalizações restantes são falsos positivos (têm texto dentro de ternário) |
| Controles sem ação | 2 (Configurações, ArrowUpRight) | 0 (removidos) | leitura |
| `alert()` bloqueantes | 9 (7 em `MapView`, 2 em `ui/map.tsx`) | 0 | `grep "alert("` |
| Menor fonte (raiz 16 px) | 7 px (3 usos de 7 px, 26 de 8 px, 39 de 9 px) | 12 px | token `text-2xs` = 0,75 rem |
| Menor fonte em projeção (>= 1920 px) | 7 px | 15 px (raiz 125 %) | `@media (min-width:1920px){html{font-size:125%}}`, conferido no CSS do build |
| Usos de `text-[7..11px]` | 145 | 0 (todos viraram `text-2xs`; `[12px]` -> `text-xs`) | grep |
| `text-zinc-500/600/700` em texto | 114 | 0 (viraram zinc-400) | grep |
| Largura mínima utilizável | 836 px (painel `left-416 + w-420`) | 360 px de conteúdo (cabe em 390) | por leitura das classes, sem navegador |
| Testes | 5 arquivos, 20 testes | 13 arquivos, 52 testes | `npm test` |
| `npx tsc -b --noEmit` / `npm run lint` | — | 0 erros / 0 erros (3 warnings já existentes de react-refresh) | |

## Contraste (WCAG 1.4.3 texto 4,5:1; 1.4.11 gráfico 3:1), fundo `#09090b`

| Nível | Antes (cor, branco sobre a cor, cor sobre o fundo) | Depois |
|---|---|---|
| LOW | `#10b981` 2,54 / 7,84 | `#059669` (emerald-600) 3,77 / 5,28 |
| MEDIUM | `#f59e0b` 2,15 / 9,26 | `#d97706` (amber-600) 3,19 / 6,24 |
| HIGH | `#f97316` 2,80 / 7,10 | `#dc2626` (red-600) 4,83 / 4,12 |
| UNKNOWN | marcador caía em vermelho; popup `#3f3f46` 1,91 | `#71717a` (zinc-500) 4,83 / 4,12 |
| Texto secundário | zinc-600 2,57; zinc-500 4,12 | zinc-400 `#a1a1aa` 7,76 |
| Cabeçalho do card (branco sobre `-600/80`) | não medido | 4,57 a 6,70 |

HIGH passou de laranja para vermelho porque laranja e âmbar quase não se distinguem (daltonismo). O teste `riskContrast.test.ts` trava >= 3:1 para as 4 cores. Os textos coloridos `text-*-500` já passavam (5,29 a 9,26).

Risco sem depender só de cor: ícone de forma distinta por nível no marcador e na legenda (escudo, triângulo, octógono, interrogação) e borda tracejada em UNKNOWN; o `aria-label` do marcador diz nome e nível.

## O que mudou

1. Erro e vazio: `useMapData` expõe `hasError`, `isRefreshing`, `lastSuccessAt` e mantém os últimos dados em tela na falha. O "Sistemas Online" fixo virou `ApiStatusIndicator` (Conectando / Online · dados das HH:MM / Sem conexão · último dado HH:MM, em America/Recife via `utils/datetime.ts`). `ApiErrorBanner` (`role="alert"`) mostra o horário do último dado válido e o botão "Tentar de novo". `EmptyDataNotice` cobre o caso de a API responder sem pontos nem sensores. Falha do status em tempo real de um ponto também avisa. O rótulo "Admin" fixo (informação falsa) foi removido.
2. Notificações: `hooks/useNotices` + `components/ui/notice-region` (`aria-live="polite"`; erro com `role="alert"` e permanente até dispensar; sucesso some em 6 s). Não há toast/sonner/Radix no projeto, então é um componente pequeno, sem dependência nova.
3. Teclado: marcadores viram `role=button`, `tabindex=0`, nome acessível, Enter/Espaço (`lib/a11y.ts`); popups com `role=dialog`, foco ao abrir, Esc fecha e devolve o foco ao marcador; Esc global fecha lista, camadas e popups. Modais (relato, cenário, detalhe do ponto e do sensor) usam `ModalDialog`: `role=dialog`, `aria-modal`, foco preso, Esc, retorno de foco. Foco visível global (`:focus-visible`) e `prefers-reduced-motion`. Leitura da lista de sensores do painel de local virou `<button>`; abas com `role=tab`/`aria-selected`; botões de camada com `aria-pressed`; severidade do relato com `aria-pressed`; campo de busca e textarea rotulados.
4. Legenda permanente `RiskLegend` (sempre no mapa; recolhível, começa recolhida no celular): Baixo, Moderado, Alto, Previsão indisponível, cada um com uma frase operacional. Não cita limiar nem promete evacuação; "Alto" diz que não confirma alagamento; "Previsão indisponível" diz que não significa risco baixo. Texto em `RISK_MEANING` (`utils/risk.ts`).
5. Responsividade: a lista de sensores/pontos vira sheet inferior em < 768 px (botão "Sensores e pontos", fecha com botão ou Esc); painel "Análise de Local" vira sheet inferior (`inset-x-2`) até 1024 px e só vai ao lado em >= lg; popup do ponto `min(22.5rem, 100vw-2rem)`; ilhas do topo compactas; botão de relato vira só ícone abaixo de `sm`. Larguras fixas em px (sidebar, painel, popup) viraram rem, para acompanhar a escala da projeção.
6. Tipografia: token global `--text-2xs` (0,75 rem) no lugar de 145 tamanhos arbitrários; em >= 1920 px a raiz sobe para 125 %.
7. Testes novos: indicador de status online/erro/carregando, banner de erro (horário + retry), estado vazio, legenda (4 estados, sem linguagem prometedora, recolher), `apiStatus` (virada de dia em Recife, nulo/inválido), `useMapData` (sucesso, falha após sucesso preserva horário, retry limpa erro), `ModalDialog` (role, foco inicial, Esc, foco preso, retorno de foco), `makeOperable` (Enter/Espaço), `NoticeRegion`/`useNotices`, contraste das 4 cores.

## Arquivos tocados

Novos: `src/components/ui/{modal-dialog,notice-region}.tsx`, `src/hooks/useNotices.ts`, `src/lib/a11y.ts`, `src/features/map/components/{ApiStatusIndicator,ApiStatusBanner,RiskLegend,RiskIcon}.tsx`, `src/features/map/utils/apiStatus.ts` e 7 arquivos `*.test.*`.
Editados: `src/index.css`, `src/components/ui/map.tsx`, `src/features/map/hooks/useMapData.ts`, `utils/{risk,sensor,weather}.ts`, `components/{MapView,SensorSidebar,SensorPopup,FloodPointPopup,FloodPointDetailCard,SensorDetailCard,ReportFloodModal,ReportScenarioModal}.tsx`. Cópias de antes em `scratchpad/front-before-a11y/`.

## Como testar

`npm run lint && npx tsc -b --noEmit && npm test`. Manual: derrubar o backend e esperar o polling (30 s) ou recarregar: aparece o banner com o último horário e o indicador vai a "Sem conexão"; subir o backend e clicar "Tentar de novo". Tab até um marcador e Enter abre o popup, Esc fecha. DevTools em 390 px: sheet de lista e painel de local sem rolagem horizontal. Janela >= 1920 px: texto mínimo 15 px.

## Ficou de fora

- Sem screenshots nem medição em navegador (não há Playwright/navegador nesta sessão): larguras e tamanhos acima vêm de leitura das classes e do CSS gerado. Falta verificar visualmente sobreposições no iPhone SE (375 px) e a legenda sobre o painel de camadas aberto.
- Não há tema claro (F1-F2-12 pede modo de alto contraste/claro para projeção; só subi a escala).
- Hierarquia terciária em cinza perdeu um degrau (zinc-500/600 -> 400).
- Texto placeholder "Coletando telemetria..." do card de detalhe (F1-F2-13) e a ausência de causa/lâmina/cota continuam.
- Popups ainda fecham por clique no mapa/Esc, sem botão "X" visível; `onClose` dos popups limpa só o id, e a seleção "manual" persiste (comportamento anterior, não alterado).
- Marcadores seguem DOM com `animate-ping` (F1-F2-07); o popup da lista de leituras do painel de local usa `<p>` dentro de `<button>`.
- Rótulos "CRITICAL" do relato manual (`ReportFloodModal`) continuam em inglês.
- Não adicionei dependência; Radix/Dialog não existia e o `ModalDialog` próprio é pequeno (se o ADR de UI preferir Radix, trocar é local).
