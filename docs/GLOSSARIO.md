# Glossário do MAPi

Vocabulário único dos três repositórios (projeto-mapi-api, projeto-mapi-ai, projeto-mapi-front)
e do modelo estocástico (rede de Petri / Mercury). Termo novo entra aqui antes de aparecer em
código, documento ou slide. Coluna "Campo" é o nome proposto em código (inglês, camelCase,
conforme a preferência registrada para DTOs); se ainda não existe no código, aparece como
*(proposto)*. Quando o nome no código existe mas diverge do termo, a célula traz **divergente** e o nome real
(fonte: `docs/API.md` e `docs/openapi/openapi-integracao-fase2.json`, em 2026-10-11).

Status: **v2, 2026-10-11**. v2 corrige os termos desatualizados da v1 (Lâmina, Rótulo, Horizonte, Modelo do balde,
Fuso; Preamar passa a "m (NR DHN)" para o painel não omitir a referência), registra os campos divergentes do código, acrescenta os termos de modelo e de dados que a banca
apontados no §4 de `docs/PRONTIDAO-ACADEMICA.md` e a nota fichas × degraus da curva. Definições de ficha, soleira, lâmina e
nível lá fora confirmadas pelo Lucas contra a formulação do modelo estocástico em 2026-10-10.

## Hidrologia urbana e modelo físico

| Termo | Definição | Unidade | Campo |
|---|---|---|---|
| Ponto crítico | Local monitorado com histórico ou risco de alagamento; entidade `FloodPoint` no backend. Referências: Derby (dominado por maré) e Imbiribeira (plano, escoamento lento). | — | `floodPoint` |
| Lâmina | Altura da água acumulada sobre a superfície da rua no ponto crítico. No modelo, a unidade canônica é mm (`h_p`); a exibição usa cm. Não há campo de lâmina no código hoje. | mm (modelo); cm (exibição) | `waterDepthMm` *(proposto; glossário v1 dizia `waterDepth`)* |
| Ficha | Unidade discreta da rede de Petri e do balde: 1 ficha = 5 mm de lâmina/volume equivalente. Não é o degrau de 5 cm da curva cota-volume (ver nota abaixo). | 5 mm | — |
| Lâmina máxima do dia (`S`) | `S(p, d) = máx` de `h_p(t)` sobre as horas do dia local `d`, em mm. Alerta do balde: `S ≥ h_crit`. | mm | — (modelo) |
| Razão de demanda (`R`) | `R(p, d) = máx` de `P(t)` no dia ÷ `(f · i_T)`. Desempate do escore lexicográfico quando `S = 0`. | — | — (modelo) |
| Lâmina crítica (`h_crit`) | Lâmina a partir da qual o ponto é contado como alagamento: 15 cm (= 30 fichas), faixa 10–30 cm (PARAMETROS, linha 9). | cm (= 30 fichas) | — (modelo) |
| Fração da capacidade (`f`) | Fração da capacidade de projeto da galeria: `q_p = f · C · i_T · A_p / 1000`. Valor central 1,0, hipótese declarada (`[sem fonte]`); só faixa de sensibilidade. | — | — (modelo) |
| Intensidade de projeto (`i_T`) | `i(TR, 60 min)`: intensidade de chuva de projeto para o tempo de retorno `TR` e duração de 60 min (Silva e Araújo, 2013, Eq. 16). Valor central 60,1 mm/h. | mm/h | — (modelo) |
| Cota da rua | Elevação do terreno no ponto crítico, amostrada do MDT do PE3D (mediana num raio de 3 m). Ortométrica, no datum do MDT, não referida ao NR da DHN. | m | `streetElevationM` **divergente** (glossário v1: `streetElevation`) |
| Altura da rua sobre o fundo da depressão (`rua_rel_m`) | `cota da rua − fundo da bacia`. Grandeza **relativa** entre a rua e o fundo do próprio ponto (0,037–0,109 m em F4-IA-13). Pode ser calculada sem Δ (ADR 0009). | m | — (modelo) |
| Cota relativa | Diferença de cota entre pontos ou entre pontos e o fundo, sempre no mesmo datum do MDT. Válida para comparar pontos entre si; não compara com maré. | m | — |
| Soleira | Cota da saída (exutório) da galeria no canal ou rio. Nível externo acima da soleira bloqueia, total ou parcialmente, o escoamento. Sem cadastro de microdrenagem com fonte, é só faixa (PRE-REGISTRO §4.2). | m | `outletSillElevation` *(proposto)* |
| Nível lá fora | Nível da água no corpo receptor da galeria: `máx(nível do rio, maré prevista + resíduo)`. Precisa estar na mesma referência vertical da soleira para ser comparado. **Não calculável até o Δ NR↔MDT ser aprovado (ADR 0009).** | m (NR DHN) | `outsideLevel` *(proposto)* |
| Folga até a soleira | `soleira − nível lá fora`. Negativa = galeria afogada. Não calculável até o Δ ser aprovado (ADR 0009). | m | *(proposto)* |
| Refluxo | Entrada de água do corpo receptor pela galeria em direção à rua, quando o nível lá fora supera a cota da rua. Sem Δ aprovado, nenhum refluxo é marcado (ADR 0009). | — | — |
| Δ (NR↔MDT) | Desnível entre o Nível de Redução da DHN e o datum altimétrico do MDT: `cota_NR = H + Δ`. Ainda não aprovado; a estimativa de trabalho (≈ +1,04 ± 0,3 m) não é usada em código (ADR 0009). | m | — (tabela de parâmetros, não constante) |
| Área contribuinte | Área da micro-bacia cujo escoamento converge para o ponto crítico (derivada do MDT por direção e acumulação de fluxo, D8). Depende da regra de snap ao ponto (padrão 10 m; faixa 5, 10 e 20 m). | m² | `contributingAreaM2` **divergente** (glossário v1: `contributingArea`) |
| Curva cota-volume | Volume armazenável na depressão do ponto em função do nível da água; converte volume em lâmina. Tem 41 níveis de **5 cm** acima do fundo, até +2 m (cada degrau vale 10 fichas; ver nota abaixo). | m³ por cm (`m3PerCm`); m³ acumulado (`cumulativeVolumeM3`) | `volumeCurve[]` (`CurvePoint`: `aboveBottomCm`, `m3PerCm`, `cumulativeVolumeM3`) |
| Depressão fechada (sink) | Célula/região do MDT sem saída de escoamento por gravidade. Candidata a ponto crítico. | — | — |
| Modelo do balde (M1) | Modelo físico **principal** (M1a, com `g ≡ 1`): balanço de massa horário entre chuva, capacidade `q_p` e volume da depressão, com lâmina em fichas. Não é baseline. Sem cadastro de galerias, o alerta depende basicamente de `P(t) > f · i_T` (MODELAGEM §2.1). | — | — (modelo) |
| Forçante | Série de entrada do modelo de entrada do modelo (chuva horária de reanálise, maré prevista). Em M1, a chuva por ponto vem da reanálise Open-Meteo archive. | mm/h | — |
| Reanálise | Série de chuva modelada (Open-Meteo archive), não medida. Suaviza extremos; o máximo de 2023–2024 foi 41,1 mm/dia (MEMO §1.5). Modelo do archive a fixar em todos os períodos (PRE-REGISTRO §10, item 5). | mm | — |

**Nota: fichas × degraus da curva cota-volume.** A ficha é de 5 mm de lâmina (1 ficha = 5 mm). O degrau da curva
cota-volume é de **5 cm** (50 mm). Logo, um degrau de 5 cm vale **10 fichas**. A conversão de nível para fichas
interpola a curva e não usa o degrau como unidade. Por isso `h_crit` de 15 cm são 30 fichas, e 5 cm e 5 mm não se confundem.
Qualquer número de fichas deve dizer de qual unidade se trata.

## Maré e oceano

| Termo | Definição | Unidade |
|---|---|---|
| Preamar | Maré alta: máximo local da altura de maré num ciclo. | m (NR DHN) |
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

| Termo | Definição | Unidade | Campo |
|---|---|---|---|
| Risco (`riskLevel`) | Classe derivada da probabilidade do modelo por limiares: `LOW`, `MEDIUM`, `HIGH`. | — | `riskLevel` |
| `UNKNOWN` | Degradação: IA indisponível/timeout ou sem dado suficiente. Não é risco baixo. Status da predição fica `FAILED`. | — | `riskLevel` |
| Horizonte (H) | Janela futura da previsão `[t, t+H]` no contrato de predição (`horizonHours`, em horas). A formulação horária supervisionada (3 h e 6 h) foi **abandonada**; a F1 trabalha no dia (MODELAGEM-IA, Histórico). | h | `horizonHours` |
| Rótulo | Dia com registro de alagamento no CKAN Recife (tipo Cobrade filtrado), atribuído ao dia local de Recife (America/Recife), com precisão DAY. Sem hora de ocorrência: o teste usa o dia inteiro (PRE-REGISTRO §2). | dia | — |
| Dia sem registro | Negativo da avaliação: dia sem registro de alagamento. Só informa quando o registro do ano funcionava (PRE-REGISTRO §2). Não equivale a "não alagou". | dia | — |
| Episódio | Dias a até 3 dias de distância entre si formam um episódio; é a unidade de independência nos intervalos de confiança (PRE-REGISTRO §2, MEMO §1.2). | — | — |
| `modelValidationStatus` | Estado de validação do modelo que emitiu a predição: `NOT_VALIDATED` (padrão) ou `VALIDATED`. Só vira `VALIDATED` por ADR que cite uma avaliação pré-registrada positiva; com `NOT_VALIDATED` o painel mostra "Previsão experimental — modelo não validado" (ADR 0007, emenda 1). | enum | `modelValidationStatus` |
| `degradationReason` | Motivo da degradação quando `riskLevel = UNKNOWN`: `INSUFFICIENT_DATA`, `AI_TIMEOUT`, `AI_UNAVAILABLE`, `AI_ERROR` (ADR 0007). Nulo com risco `LOW`, `MEDIUM` ou `HIGH`. | enum | `degradationReason` |
| Proveniência | Metadados de origem de cada valor: fonte, identificador da estação, quando foi coletado, versão/correção da agência, raio de vínculo sensor↔ponto. | — | `provenance` |
| Staging bruto (`raw_observation`) | Cópia append-only do que a fonte devolveu, com hash do payload e fuso de origem declarado. Nunca é editada (ADR 0006). | — | — |
| Quarentena | Registro reprovado na validação (faixa, unidade, fuso não verificado), guardado com a regra e o motivo; não entra no dataset (ADR 0006). | — | — |
| Série consolidada (`observation`) | Valor validado, em UTC, com chave única natural; correções da agência viram nova revisão (ADR 0006). | — | — |
| Execução de ingestão (`ingestion_run`) | Uma carga ou reprocessamento: fonte, intervalo, parâmetros, versão do código, contagens. | — | — |
| `provenanceId` | Identificador da execução de ingestão que produziu um valor; exposto nas respostas de dado (proposto no ADR 0006). | — | `provenanceId` |
| Grade horária | Resolução canônica do dataset. Cada hora é rotulada pelo **fim** do intervalo: o valor das 13:00 cobre (12:00, 13:00] (ADR 0006). | h | — |
| Fuso | Persistência em UTC; exibição em America/Recife (UTC−3) — ADR 0001. A série consolidada (`observation`) está em UTC, e cada fonte declara o `source_tz` na carga (ADR 0001, 0006). A convenção da hora da chuva (ANA × Open-Meteo, deslocamento de cerca de 1 h) ainda não está resolvida (PRONTIDAO §5.1, L8). | — | — |
