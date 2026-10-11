# F4-FRONT-01b — rótulo experimental (RC-03) e resíduos visuais

Repositório do front, branch `wip/preservacao-2026-10-11`, 2 commits novos, sem push. `tsc -b --noEmit`: 0 erros; `npm run lint`: 0 erros (3 avisos antigos); `npm test`: 16 arquivos, 73 testes (eram 66; +7).

## 1. Contrato (ADR 0007, emenda 1)

- Schema (`floodPointResponse.schema.ts`): `modelValidationStatus` opcional, enum `NOT_VALIDATED | VALIDATED`; valor desconhecido vira `NOT_VALIDATED` (nunca presume validação). Ausente é aceito; `validationStatusOf()` (em `utils/risk.ts`) devolve `NOT_VALIDATED`.
- `ExperimentalBadge` mostra "Previsão experimental — modelo não validado" junto da classe no popup (`FloodPointPopup`) e no cartão de detalhe (`FloodPointDetailCard`), só para LOW/MEDIUM/HIGH com `NOT_VALIDATED`. UNKNOWN mantém o estado próprio ("Previsão indisponível") sem o rótulo. Texto visível (não só tooltip), ícone `aria-hidden`.
- Contraste: texto âmbar-200 (#fde68a) sobre fundo âmbar-950 a 70% sobre painel escuro; estimado > 10:1 (mínimo 4,5:1). Não medi com ferramenta; o teste de contraste existente (`riskContrast.test.ts`) não cobre o selo.
- Legenda de risco: rodapé "Classes experimentais: modelo não validado." (uma frase curta; substituiu a frase antiga "ainda em validação. Confirme em campo antes de decidir", cuja recomendação de confirmar em campo continua nos textos de Alto e Indisponível).
- Testes: schema (VALIDATED, NOT_VALIDATED, ausente, valor desconhecido), popup com rótulo (campo ausente), sem rótulo com VALIDATED, UNKNOWN sem rótulo, frase da legenda.

## 2. Resíduos visuais

1. **Legenda cortada em 1920x1080**: coluna da direita passou de `bottom-[17rem]` para `md:bottom-[12rem]` (termina em y = 840, logo acima do botão de zoom, que começa em ~830 no eixo dos cartões; sem sobreposição visível), `gap-2`, margens menores na legenda de terreno e de risco, rodapé de uma linha. Medição no navegador: antes o conteúdo tinha 668 px para 645 de área (cortava); agora 643 para 643 (sem rolagem; sobra 2 px). Em telas mais baixas ainda rola, agora com barra de rolagem fina visível (`custom-scrollbar` com cor mais clara).
2. **"PONTOS CRÍTICOS" truncado em 390 px**: painel de camadas de `w-48` para `w-56`; as 8 camadas aparecem inteiras (screenshot `mapa-com-terreno-camadas-...-390x844-v3.png`).
3. **Opacidade da hipsometria x ruas**: em vez de baixar a opacidade (o que apagaria a distinção das cotas), mantive 0,7 e **mudei a posição da camada**: agora logo abaixo da primeira camada de via do mapa-base (`tunnel*`, `road*`, `bridge*`, `rail*`; no Carto dark-matter é `tunnel_service_case`), com queda para "abaixo do primeiro rótulo" em estilo sem vias. Resultado: o traço das ruas e os rótulos ficam por cima, íntegros; a cor do terreno continua distinguível. Justificativa: no v2 as ruas ficavam sob o raster a 70%, o que escurecia/apagava o traço; opacidade menor só amenizaria o problema à custa da legibilidade das cotas. Teste de `TerrainLayer` atualizado (insere antes de `road_minor_case`).

## 3. Verificação visual (v3)

Screenshots em `docs/revisao/fase-4/screenshots/*-v3.png` (390x844 e 1920x1080; sem terreno, com terreno, falha, e painel de camadas aberto), zoom 15, API desligada, Playwright 1.48.2 + Chromium headless; Vite parado pelo PID.

Conferido: legenda de risco completa em 1920; ruas, quarteirões e rótulos nítidos com terreno ligado; banner, cartões e botões sem sobreposição; 390 px com terreno recolhido por padrão e legenda de risco recolhida.

Ainda errado ou discutível:
- Com as vias por cima, o mapa com terreno fica visualmente mais denso (prédios, contornos de quadras e vias do Carto aparecem sobre a cor). É o custo de preservar as ruas; opacidade ou posição dependem de aprovação do autor.
- Os prédios do mapa-base (camada `building`) ficam acima do raster em zoom alto, escurecendo partes do terreno nos quarteirões. Não resolvi para não cobrir mais camadas.
- Em 390 px a legenda de risco começa recolhida; o aviso "Classes experimentais" só aparece ao expandir. O rótulo por ponto (popup/cartão) é o que sempre acompanha a classe.
- Popup e cartão com o rótulo não foram fotografados (sem API não há pontos; compose não foi subido); cobertos só por teste unitário.
- Alturas de tela abaixo de 1080 ainda rolam dentro da coluna.

## 4. Regressão da camada de terreno (mesmo `measure.mjs`, perfil F3-FRONT-02: 9 Mbps / 1,5 Mbps / 170 ms, CPU 4x, 1280x800, zoom 14 a 17)

| Lote | Até aparecer (ms), 3 rodadas | Mediana | Bytes z14 / z15 / z16=z17 | Requisições |
|---|---|---|---|---|
| F3-FRONT-02 (referência) | 2.851 / 2.891 / 3.021 | 2.891 | 997.945 / 2.195.148 / 3.387.886 | 75 |
| F4 lote 1 | 3.002 / 2.662 / 3.039 | **3.002** | idem | 75 |
| F4 lote 2 (repetição) | 2.806 / 3.136 / 2.983 | **2.983** | idem | 75 |

- Tráfego: **idêntico byte a byte** (3,39 MB; limite 5 MB), 75 requisições, todas 206. A mudança de posição da camada não altera o que é baixado.
- Tempo: o lote 1 teve mediana 3.002 ms, 2 ms acima do limite de 3 s; o lote 2, 2.983 ms (dentro). As rodadas variam de 2,66 a 3,14 s (±0,25 s), maior que a folga. O resultado é **na margem do limite, não uma regressão atribuível à mudança** (mesmo download, mesma camada, só outra posição no empilhamento), mas o critério "mediana <= 3 s" não é satisfeito de forma estável nesta máquina. Registrado como risco; o limite não foi alterado. Limitações iguais às do F3-FRONT-02 (sem GPU, CPU e rede emuladas; não é celular real). Arquivos brutos: `scratchpad/pw/measure-result-f4-run1.json` e `measure-result.json` (lote 2).

## Commits (front)

- `cb0dc70` Contrato: modelValidationStatus e rótulo experimental junto da classe
- commit seguinte: Visual (legenda, painel de camadas, hipsometria abaixo das vias)
