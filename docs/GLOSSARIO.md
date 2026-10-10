# Glossário do MAPi

Vocabulário único dos três repositórios (projeto-mapi-api, projeto-mapi-ai, projeto-mapi-front)
e do modelo estocástico (rede de Petri / Mercury). Termo novo entra aqui antes de aparecer em
código, documento ou slide. Coluna "Campo" é o nome proposto em código (inglês, camelCase,
conforme a preferência registrada para DTOs); se ainda não existe no código, aparece como
*(proposto)*.

Status: **v0, 2026-10-10 (F0-ARQ-02)**. Definições marcadas com ⚠ precisam de confirmação do
Lucas contra a formulação do modelo estocástico.

## Hidrologia urbana e modelo físico

| Termo | Definição | Unidade | Campo |
|---|---|---|---|
| Ponto crítico | Local monitorado com histórico ou risco de alagamento; entidade `FloodPoint` no backend. Referências: Derby (dominado por maré) e Imbiribeira (plano, escoamento lento). | — | `floodPoint` |
| Lâmina | Altura da água acumulada sobre a superfície da rua no ponto crítico. | cm (exibição); mm no modelo estocástico | `waterDepth` *(proposto)* |
| Ficha | Unidade discreta da rede de Petri: 1 ficha = 5 mm de lâmina/volume equivalente. ⚠ | 5 mm | — |
| Cota da rua | Elevação do terreno no ponto crítico, amostrada do MDT do PE3D. Ortométrica (não é referida ao NR da DHN). | m | `streetElevation` *(proposto)* |
| Soleira | Cota da saída (exutório) da galeria no canal ou rio. Nível externo acima da soleira bloqueia, total ou parcialmente, o escoamento. ⚠ | m | `outletSillElevation` *(proposto)* |
| Nível lá fora | Nível da água no corpo receptor da galeria: `máx(nível do rio, maré prevista + resíduo)`. Precisa estar na mesma referência vertical da soleira para ser comparado. | m | `outsideLevel` *(proposto)* |
| Folga até a soleira | `soleira − nível lá fora`. Negativa = galeria afogada. | m | *(proposto)* |
| Refluxo | Entrada de água do corpo receptor pela galeria em direção à rua, quando o nível lá fora supera a cota da rua. | — | — |
| Área contribuinte | Área da micro-bacia cujo escoamento converge para o ponto crítico (derivada do MDT por direção e acumulação de fluxo). | m² | `contributingArea` *(proposto)* |
| Curva cota-volume | Volume armazenável na depressão do ponto em função do nível da água; converte volume em lâmina. | m³ por cm | *(proposto)* |
| Depressão fechada (sink) | Célula/região do MDT sem saída de escoamento por gravidade. Candidata a ponto crítico. | — | — |
| Modelo do balde | Baseline físico: balanço de massa simples (entra chuva × área contribuinte × coef. de escoamento; sai pela galeria se o nível lá fora permitir). | — | — |

## Maré e oceano

| Termo | Definição | Unidade |
|---|---|---|
| Preamar | Maré alta: máximo local da altura de maré num ciclo. | m (NR) |
| Baixa-mar | Maré baixa: mínimo local. | m (NR) |
| Nível de redução (NR) | Referência vertical das tábuas de maré da DHN para o porto. Diferente do datum do MDT. | — |
| Sizígia / quadratura | Fase de lua nova/cheia (amplitude máxima) / quarto crescente/minguante (amplitude mínima). | — |
| Resíduo (meteorológico) | Diferença entre o nível do mar observado/modelado (Open-Meteo Marine) e a maré astronômica da tábua. | m |
| Tábua de maré | Previsão astronômica determinística (DHN; via TabuaMare). Disponível antes do evento — por isso é feature legítima no futuro. | m (NR) |

## Terreno

| Termo | Definição |
|---|---|
| MDT | Modelo Digital de Terreno: solo nu, sem edificação/vegetação. É o que se usa para cota da rua. |
| MDE / MDS | Modelo Digital de Elevação/Superfície: inclui telhados e árvores. `MDE − MDT` ≈ altura de objetos. |
| PE3D | Programa Pernambuco Tridimensional: LiDAR 2016, 1 m/pixel, EPSG:31985. |
| COG | Cloud Optimized GeoTIFF: GeoTIFF com tiles internos e overviews, legível por faixa via HTTP. |

## Sistema e dados

| Termo | Definição |
|---|---|
| Risco (`riskLevel`) | Classe derivada da probabilidade do modelo por limiares: `LOW`, `MEDIUM`, `HIGH`. |
| `UNKNOWN` | Degradação: IA indisponível/timeout ou sem dado suficiente. Não é risco baixo. Status da predição fica `FAILED`. |
| Horizonte (H) | Janela futura da previsão `[t, t+H]`; propostos 3 h e 6 h. |
| Rótulo | Ocorrência de alagamento do CKAN Recife (filtrada por tipo Cobrade) casada com ponto e hora. |
| Proveniência | Metadados de origem de cada valor: fonte, identificador da estação, quando foi coletado, versão/correção da agência, raio de vínculo sensor↔ponto. |
| Fuso | Persistência em UTC (`hibernate.jdbc.time_zone: UTC`); exibição em America/Recife (UTC−3). Conflito documental aberto — ver PROGRESSO.md, pergunta 1. |
