# Modelagem de IA do MAPi: tarefa, rótulo, features e baselines

- Tarefa: F4-IA-01 (seções A.1, A.2 e A.3 do relatório de ML) · matriz de prioridade, item 6
- Data: 2026-10-10 · Autor: Agente 3 (nível Máximo) · Revisão pendente: Lucas
- Status: **proposta para defesa**. Nenhum resultado numérico de modelo aparece aqui: ainda não existe dado
  carregado. O banco tem 0 eventos de alagamento, 555 leituras de sensor e 40 de clima
  (fonte: `docs/snapshots/2026-10-10-pre-revisao.md`), e a carga histórica será feita do zero pelo
  pipeline do ADR 0006.
- Respeita: skill `mapi-ml-pipeline` (regras 1 a 9), `docs/GLOSSARIO.md` v1, ADR 0006 (ingestão),
  0007 (contrato de predição), 0008 (terreno) e 0009 (referência vertical).
- Decisões do autor que este documento não reabre: o rótulo positivo é só "alagamento" (Cobrade), sem
  "inundação" (ADR 0006, status); `flood_predictions` fica fora dos resultados (ADR 0007 §7); ANA,
  APAC meteorologia24h e Cemaden ficam em quarentena `TZ_UNVERIFIED` até a verificação do fuso
  (ADR 0006 §4). A escolha entre rótulo diário e horizonte de 3/6 h (pergunta 17) foi delegada a esta
  formulação e está decidida na seção A.1.4.

---

## A.1 Tarefa formal

### A.1.1 Notação

| Símbolo | Significado |
|---|---|
| `p ∈ P` | ponto crítico (`flood_points.id`). Hoje `|P| = 5`; com terreno, 4 (JABOATAO_CENTRO sem folha do PE3D, `docs/TERRENO-PE3D.md` §3, ramo `feat/terreno-pe3d`) |
| `t` | instante de emissão da previsão, na grade horária UTC. Cada hora é rotulada pelo **fim** do intervalo: a linha `t` contém só dado com timestamp em `(t − 1 h, t]` (ADR 0006 §5) |
| `H` | horizonte, em horas. `H ∈ {3, 6}`, um modelo por horizonte (skill §1). `horizonHours` no contrato (ADR 0007 §5) |
| `B_k(t) = (t − k, t]` | janela para trás de `k` horas, usada em toda feature observada |
| `F_H(t) = (t, t + H]` | janela futura do rótulo |
| `x(p, t)` | vetor de features do ponto `p` disponível no instante `t` (seção A.2) |
| `y_H(p, t) ∈ {0, 1, ∅}` | rótulo: 1 = alagamento notificado em `F_H(t)`; 0 = nenhum; `∅` = ambíguo, fora do treino e da avaliação (A.1.4 e A.3) |
| `τ_e`, `I_e = [from_e, to_e)` | instante (precisão `HOUR`) ou intervalo (precisão `DAY`) da ocorrência `e` em `flood_occurrence` (ADR 0006 §6) |
| `p̂_H(p, t)` | probabilidade calibrada que o modelo emite (`floodProbability`) |

Universo de instâncias: 5 pontos × 8.760 h × 5 anos = **219.000 ponto-horas** por horizonte (derivado;
mesma conta de `docs/revisao/fase-1/F1-BACK-03.md` §6, citada no ADR 0006 §9).

```mermaid
flowchart LR
  subgraph passado[Features: janelas para trás B_k]
    R["chuva (t−k, t]"]
    Q["nível do rio, pressão, vento: última leitura em (t−1h, t]"]
  end
  T((t: emissão))
  subgraph futuro[Rótulo: F_H = t, t+H]
    Y["y = 1 se houver ocorrência 'alagamento' notificada em p com τ em (t, t+H]"]
  end
  M["Exceção declarada: maré prevista da tábua em t+1 … t+H"]
  passado --> T --> futuro
  M -.publicada antes de t.-> T
```

### A.1.2 Tarefa principal

Classificação binária probabilística: estimar `P(y_H(p, t) = 1 | x(p, t))` para `H = 6 h` (modelo
principal) e `H = 3 h` (segundo modelo). A saída é a probabilidade calibrada; `LOW/MEDIUM/HIGH` é
derivada por limiares (A.1.5). Divisão temporal: treino até a data `D₁`, validação em `(D₁, D₂]` (para
calibração e limiares), teste em `(D₂, fim]`. Validação cruzada dentro do treino: blocos por evento
(`GroupKFold` pelo identificador de evento, A.3.2) ou `TimeSeriesSplit` com intervalo de guarda ≥ `H`.
Nunca embaralhar (skill §2).

### A.1.3 Tarefas secundárias

| Tarefa | Formulação | Viável com o rótulo disponível? |
|---|---|---|
| Tempo até o evento | Risco discreto por hora: `h_k(p, t) = P(T = k | T ≥ k, x(p, t))`, `k = 1 … K`, com censura em `K`. Coerente com a tarefa principal, pois `P(T ≤ H) = 1 − ∏_{k ≤ H} (1 − h_k)`. Substituiria o campo `estimatedTimeToEvent`, hoje obsoleto e sempre `null` (ADR 0007 §5) | Só com eventos de precisão `HOUR`; o `T` medido é o tempo até a **notificação**, não até o início da lâmina (A.3.5). Exploratória |
| Severidade ordinal pela lâmina | Classes ordenadas de lâmina, com fronteiras múltiplas da ficha (5 mm), para casar com a rede de Petri | **Não com o CKAN**, até prova em contrário: nenhum documento lido mostra campo de lâmina no CKAN. O CSV atual do export tem uma coluna `severity` (`F1-IA-02.md` §2) de origem não documentada `[a verificar]`. Se não houver lâmina observada, a severidade só existe na saída dos modelos físicos (balde, Petri) e não é tarefa supervisionada |
| Duração | Horas com lâmina > 0 | **Não**: o CKAN registra notificação, não fim do alagamento; `to_e` é construído pelo pipeline (1 h ou 1 dia), não observado. Fica para medição de campo ou para a rede de Petri |

### A.1.4 Decisão: horizonte × precisão do rótulo (pergunta 17)

**Problema.** O CKAN tem ocorrências com data e hora (`HOUR`) e ocorrências só com data (`DAY`). O
ADR 0006 §6 proíbe converter `DAY` em hora. Com `H = 3` ou `6 h`, uma ocorrência `DAY` cobre 24 h e não
cabe em nenhuma janela `F_H(t)`. A proporção `HOUR`/`DAY` **não é conhecida** (0 eventos no banco; o
ADR 0009 registra a quantidade de eventos do CKAN como "sem fonte").

**Alternativas.**

**(a) `H = 3 h` e `6 h`, treinando e avaliando só com eventos `HOUR`.** Instâncias que tocam um dia com
evento `DAY` no ponto ficam `∅` (excluídas), nunca 0.
- Utilidade para a Defesa Civil: a maior das três. Uma previsão emitida com 3 a 6 h de antecedência
  permite mobilizar equipe, interditar via e avisar moradores antes da lâmina.
- Positivos: só `N_HOUR` eventos. Cada evento gera até `H` instâncias positivas por ponto (de
  `t = τ − H` até `t = τ − 1 h`), muito correlacionadas; o tamanho efetivo da amostra é o número de
  eventos, não o de horas.
- Vieses próprios: (i) seleção, se ocorrências com hora diferirem sistematicamente das sem hora (por
  exemplo, por canal de registro, turno ou gravidade); (ii) o horário registrado é o da notificação, que
  vem depois do início da lâmina (A.3.5), e esse atraso é da ordem do próprio `H`; (iii) a probabilidade
  calibrada passa a significar "probabilidade de um alagamento notificado **com hora**", que é um limite
  inferior da probabilidade de alagamento.

**(b) Tarefa diária: "haverá alagamento no ponto `p` no dia local `d`?"** Features até um corte fixo
(proposta: 06:00 em America/Recife = 09:00Z, valor `[sem fonte]` a fixar com a Defesa Civil), mais a maré
prevista do dia inteiro.
- Utilidade: boletim de prontidão diário. Não diz em que hora agir.
- Positivos: `N_HOUR + N_DAY` dias-evento distintos, o máximo possível.
- Limite físico: a chuva que causa o alagamento cai, em boa parte dos casos, **depois** do corte. Sem
  previsão de chuva como feature (o pipeline não coleta previsões arquivadas de chuva), o modelo diário
  só enxerga umidade antecedente, chuva até 06:00, estação do ano e maré do dia. É de esperar habilidade
  concentrada nos eventos de maré e de chuva longa, e baixa nos de chuva convectiva [hipótese a medir].
  Mudar o corte para mais tarde aumenta a habilidade e reduz a antecedência; com o corte no fim do dia, a
  tarefa vira diagnóstico, não previsão.

**(c) Rótulo intervalar / aprendizado multi-instância.** O dia `d` é uma "bolsa" de instâncias horárias;
um evento `DAY` diz que ao menos uma hora da bolsa é positiva. O modelo continua horário e agrega por
`máx` ou noisy-OR (`1 − ∏(1 − p̂)`).
- A favor: usa todos os eventos no treino e mantém o produto horário.
- Contra, e decisivo: (i) a calibração horária não é identificável a partir de rótulos de bolsa. O
  noisy-OR supõe horas independentes, e horas consecutivas de chuva são fortemente dependentes; a
  probabilidade horária que alimenta `LOW/MEDIUM/HIGH` ficaria sem garantia. (ii) Calibração, escolha de
  limiar e teste exigem rótulo no nível horário, que só os eventos `HOUR` dão. Logo (c) aumenta os
  positivos de **treino**, mas não os de **avaliação**, que é a restrição que importa para uma banca.
  (iii) Exige função de perda própria no XGBoost e mais graus de liberdade com poucos positivos.

**Decisão.** **(a) é a tarefa principal**, `H = 6 h` e `H = 3 h`, com três regras complementares:

1. **Máscara, não negativo.** Toda instância `(p, t)` cuja janela `F_H(t)` intersecta o intervalo de uma
   ocorrência `DAY` casada com `p` recebe `y = ∅` e sai do treino, da calibração e do teste. Rotulá-la 0
   seria ruído no rótulo exatamente nos dias de chuva.
2. **Verificação diária com os eventos `DAY`.** O mesmo modelo horário é avaliado também no nível do dia:
   `s(p, d) = máx_t p̂_H(p, t)` sobre as emissões do dia local `d`. Os eventos `DAY` (que não entraram
   no treino) servem de teste externo de discriminação diária. Essa verificação não mede antecedência e
   é reportada como tal.
3. **Regra de troca pré-registrada.** Depois da carga e **antes de treinar**, conta-se `N_HOUR,teste`
   (eventos `HOUR` de "alagamento" casados com algum ponto no período de teste, A.3.7). Se
   `N_HOUR,teste < N_min`, a tarefa principal passa a ser **(b)** e (a) fica como exploratória. Proposta:
   `N_min = 20` eventos. Origem: com 20 eventos e recall verdadeiro de 0,5, o erro-padrão binomial do
   recall é `√(0,25/20) = 0,112`, e o intervalo de 95% fica com ≈ ±0,22; com 10 eventos, ±0,31 (cálculo
   deste documento). Abaixo de 20, nenhuma comparação com baseline sai do ruído. O valor final de
   `N_min` é decisão do autor (lista ao fim), mas precisa ser fixado antes de olhar os dados.

(c) fica registrada como extensão: só faz sentido se `N_HOUR,teste ≥ N_min` e os positivos de treino
forem o gargalo.

| | (a) principal | (b) troca | (c) extensão |
|---|---|---|---|
| Antecedência útil | 3–6 h | até o fim do dia, sem hora | 3–6 h |
| Positivos de treino | `N_HOUR` | `N_HOUR + N_DAY` | `N_HOUR + N_DAY` |
| Positivos de avaliação horária | `N_HOUR` | não se aplica | `N_HOUR` |
| Probabilidade calibrada no nível de uso | sim | sim (diária) | não garantida |
| Casa com o contrato do ADR 0007 (`horizonHours`) | sim | `horizonHours` ≈ 24, com corte | sim |

### A.1.5 De probabilidade a `LOW/MEDIUM/HIGH`

Pré-condição: `p̂` calibrada (Platt ou isotônica) **em validação separada** do treino, com curva de
confiabilidade antes e depois (skill §5). Sem calibração, o limiar abaixo não tem sentido.

Critério de custo (teoria da decisão de Bayes): com custo `c_FN` de deixar de alertar um alagamento e
`c_FA` de um falso alarme, alertar é ótimo quando `p̂ ≥ θ = c_FA / (c_FA + c_FN)`. Dois limiares, um por
ação da Defesa Civil:

| Classe | Ação associada | Limiar | Razão de custo |
|---|---|---|---|
| `HIGH` | mobilização (equipe na rua, interdição) | `θ_H = c_FA,mob / (c_FA,mob + c_FN)` | falso alarme caro |
| `MEDIUM` | atenção / prontidão (aviso interno, monitorar) | `θ_M = c_FA,pront / (c_FA,pront + c_FN)`, com `θ_M < θ_H` | falso alarme barato |
| `LOW` | nenhuma | `p̂ < θ_M` | — |

Procedimento, sem inventar valores:

1. A razão `c_FN / c_FA` de cada ação vem do autor ou da Defesa Civil, em linguagem operacional
   (ex.: "aceito até `k` mobilizações falsas para não perder um alagamento"). Enquanto não existir, os
   resultados saem para uma faixa de razões, não para um valor.
2. Na validação: **curva de confiabilidade** (justifica usar `p̂` como probabilidade), **curva de
   benefício líquido** (decision curve: benefício líquido × limiar, comparando com "alertar sempre" e
   "nunca alertar") para mostrar em que faixa de `θ` o modelo tem valor, e **curva precisão-recall**
   para ler precisão e recall em `θ_M` e `θ_H`. Critério alternativo, se a Defesa Civil preferir: maior
   recall com taxa de falso alarme ≤ `m` por ponto por mês, convertida em limiar na mesma validação.
3. Os limiares são escolhidos na validação, congelados, gravados no `metadata.json` do artefato (ADR 0007
   §2; skill §7) e só então aplicados ao teste, com matriz de confusão em cada limiar e custo descrito em
   alarmes por mês e alagamentos perdidos.
4. Um par de limiares por horizonte (3 h e 6 h são modelos distintos).
5. Lembrar a escala: sob (a), `p̂` é probabilidade de alagamento **notificado com hora**. A comparação com
   o custo real precisa dessa ressalva.

---

## A.2 Cadeia dado → resposta e features

### A.2.1 Cadeia

```mermaid
flowchart LR
  subgraph fontes[Fontes]
    OM[Open-Meteo archive / current]
    APD[APAC portal diário]
    EST[APAC 24h, Cemaden, ANA]
    TAB[TabuaMare / DHN]
    MAR[Open-Meteo Marine]
    E5[ERA5-Land]
    PE3D[PE3D: point_terrain]
    MB[MapBiomas]
    CK[CKAN Recife]
  end
  OM --> OBS[(observation UTC<br/>ADR 0006)]
  APD --> OBS
  EST -->|TZ_UNVERIFIED| QT[(quarentena)]
  QT -.após verificação do fuso.-> OBS
  TAB --> OBS
  MAR -.histórico a verificar.-> OBS
  E5 -.não coletado.-> OBS
  OBS --> HV[observation_hourly_v<br/>fim do intervalo]
  HV --> FE[features x p,t<br/>janelas para trás + maré prevista]
  PE3D -->|LEFT JOIN flood_point_id<br/>ADR 0008| FE
  MB -.não coletado.-> FE
  CK --> OCC[(flood_occurrence)] --> MT[(occurrence_point_match)] --> Y[rótulo y_H p,t]
  FE --> DS[dataset + manifesto<br/>datasetId SHA-256]
  Y --> DS
  DS --> ML[treino / calibração / teste]
  DS --> BL[baselines: nunca, chuva > X, balde, Petri]
  ML --> API["/v1/predict/flood<br/>probability, riskLevel, modelVersion, horizonHours, contributors"]
```

Leitura fonte por fonte (o detalhe de cada feature está na tabela A.2.3):

- **Chuva.** Open-Meteo é a única fonte de chuva horária sem bloqueio: treino com `OPEN_METEO_ARCHIVE`,
  produção com `OPEN_METEO_CURRENT`; a diferença entre as duas (reanálise × tempo real) é limitação
  declarada no ADR 0006 (limitação 2) e precisa ser medida. APAC meteorologia24h, Cemaden e ANA
  (pluviômetros) entram só depois da verificação de fuso. O total diário do portal APAC (`P1D`) só fica
  disponível às 03:00Z do dia seguinte (ADR 0006 §5) e por isso entra apenas como "chuva do dia anterior".
- **Maré prevista (TabuaMare/DHN).** Determinística, publicada antes do evento: é a exceção legítima de
  janela futura (A.2.2). Em metros sobre o NR da DHN, usada **só como série temporal** (altura, fase,
  amplitude), nunca contra a cota da rua (ADR 0009, decisão C). Fuso: ler `tide_tables.timezone` na
  primeira carga (ADR 0006 §4, "a consultar no banco").
- **Nível do rio (ANA/APAC).** Em quarentena de fuso. Mesmo liberado, a régua tem referência própria
  (ADR 0009, Consequências), então entra como nível da própria régua, variação e distância à cota de
  alerta da mesma estação, nunca combinado com maré ou cota da rua.
- **Nível do mar (Open-Meteo Marine) e resíduo.** O resíduo do glossário é "observado/modelado − tábua".
  As duas séries têm referências verticais diferentes e não confirmadas; uma diferença constante entre
  referências, porém, some ao subtrair a média do treino. Por isso a feature é a **anomalia** do resíduo
  (média ajustada só no treino, dentro do `Pipeline`). Disponibilidade de histórico de 5 anos e
  presença de maré astronômica na variável do Open-Meteo Marine: `[a verificar]`.
- **Umidade do solo (ERA5-Land).** Não coletada. Além disso, a reanálise chega com atraso de dias
  `[a verificar a latência exata]`, então não estaria disponível em `t` na produção: usar só se houver
  equivalente em tempo real com a mesma definição. Substituto disponível já: índice de precipitação
  antecedente calculado da própria chuva.
- **Vento e pressão.** Open-Meteo. Componente do vento soprando do mar para a costa (empilhamento de água)
  e pressão ao nível do mar (efeito do barômetro inverso sobre o nível do mar `[referência a anexar]`).
  Mecanismos de resíduo meteorológico, úteis enquanto o resíduo direto não está disponível.
- **Terreno (PE3D).** Atributos estáticos por ponto, por `LEFT JOIN` com `point_terrain` vigente
  (`terrain_run.current`), nunca por cópia manual (skill §3; ADR 0008). 4/5 pontos cobertos. Cotas só
  relativas até o Δ do ADR 0009.
- **Impermeabilização (MapBiomas).** Não coletada. Fração impermeável na área contribuinte; serve mais
  como coeficiente de escoamento do modelo do balde do que como feature (ver A.2.4).
- **Calendário.** Hora local e mês em seno/cosseno. A hora do dia pode aprender o **padrão de
  notificação** (horário de atendimento) em vez do fenômeno; entra com ablação obrigatória (A.3.4).
- **Derivadas do modelo físico.** Lâmina simulada pelo balde em `t` e, se exportada, a probabilidade da
  rede de Petri. Entram como features só se seus parâmetros forem calibrados apenas no treino.

### A.2.2 Exceção declarada: maré prevista no futuro não é vazamento

As features `tide_height_tk` (`k = 1 … H`), `tide_max_next_m`, `hours_to_next_high_tide` e
`tide_range_day_m` usam valores da tábua em instantes **depois** de `t`. Isso não é vazamento porque:

1. A tábua é previsão astronômica determinística, calculada por constantes harmônicas e publicada pela
   DHN antes do ano a que se refere (glossário, "Tábua de maré"). Em `t` ela já é conhecida para
   `t + 1 … t + H`, na produção e no treino, com o mesmo valor.
2. Ela não contém informação do evento: não depende de chuva, vento nem notificação. O que ela não
   prevê (o resíduo meteorológico) só entra observado, até `t`.
3. A versão da tábua usada no treino precisa ser a mesma publicada antes do período (a registrar no
   manifesto: identificador da tábua, ano e porto `pe02`). Se a DHN republicar com constantes novas, usar
   a versão vigente na época evita vazamento por revisão.

Qualquer outra variável com valor posterior a `t` (chuva, rio, resíduo, rótulo) é proibida, inclusive
defasada como contagem de eventos passados no ponto (skill §2).

### A.2.3 Tabela de features

Convenções: `featureId` em inglês `snake_case`, como no contrato IA → backend (ADR 0007 §3, exemplo
`tide_height_t1`); o termo do glossário aparece entre parênteses. Toda janela é para trás, `B_k(t)`, salvo
as linhas marcadas com **(exceção)**. "Nulo": o pipeline não imputa constante (ADR 0006 §1); o valor
ausente vai como `NaN` com coluna `*_missing`; o XGBoost trata ausência nativamente; qualquer imputador
de baseline linear é ajustado só no treino (skill §2). Se faltar **toda** fonte de chuva, a IA responde
422 e o backend devolve `UNKNOWN` (ADR 0007 §3–4).

Legenda de status: **D** = disponível agora (fonte aprovada no pipeline, sem bloqueio além da carga);
**Q** = depende da quarentena de fuso; **V** = depende do ADR 0009 (Δ vertical); **N** = depende de dado
não coletado.

| # | featureId (termo) | Unid. | Fonte | Janela | Nulo | Justificativa física | Disponível em `t` | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | `rain_1h_mm` | mm | Open-Meteo `precipitation` | `B_1` | NaN + flag | intensidade atual excede a capacidade da galeria | sim (current; treino com archive) | D |
| 2 | `rain_3h_mm` | mm | Open-Meteo | `B_3` | NaN se < 80% das horas (limiar do ADR 0006, `[sem fonte]`) | volume do evento em curso | sim | D |
| 3 | `rain_6h_mm` | mm | Open-Meteo | `B_6` | idem | volume que enche a depressão | sim | D |
| 4 | `rain_24h_mm` | mm | Open-Meteo | `B_24` | idem | saturação e armazenamento ocupado | sim | D |
| 5 | `rain_72h_mm` | mm | Open-Meteo | `B_72` | idem | umidade antecedente | sim | D |
| 6 | `rain_max_1h_6h_mm` | mm/h | Open-Meteo | máximo horário em `B_6` | idem | pico de intensidade, diferente de volume | sim | D |
| 7 | `antecedent_precip_index` | mm | derivada de `rain_1h_mm` | recursiva, `API_t = κ·API_{t−1} + rain_1h`; `κ` ajustado só no treino | NaN se lacuna > 24 h | substituto da umidade do solo | sim | D |
| 8 | `gauge_rain_1h_mm` | mm | APAC 24h / Cemaden / ANA, estação de `point_station_link` | `B_1` | NaN + flag | chuva medida perto do ponto (Open-Meteo é grade) | sim, com atraso da agência `[a medir]` | Q |
| 9 | `gauge_rain_3h_mm` | mm | idem | `B_3` | idem | idem | idem | Q |
| 10 | `gauge_rain_24h_mm` | mm | idem | `B_24` | idem | idem | idem | Q |
| 11 | `apac_rain_prev_day_mm` | mm | APAC portal `P1D` | dia local anterior completo | NaN | umidade antecedente medida | sim a partir das 03:00Z (ADR 0006 §5) | D |
| 12 | `tide_height_m` | m (NR) | TabuaMare/DHN, interpolada entre extremos (ADR 0006 §5) | instante `t` | recusa: maré é obrigatória | nível do corpo receptor da galeria | sim | D |
| 13 | `tide_height_tk`, `k = 1…H` (H colunas) | m (NR) | TabuaMare/DHN | `t + k` **(exceção)** | recusa | galeria bloqueada por preamar durante a janela | sim (publicada antes) | D |
| 14 | `tide_max_next_m` | m (NR) | TabuaMare/DHN | máximo em `(t, t + H]` **(exceção)** | recusa | pior bloqueio da janela | sim | D |
| 15 | `hours_to_next_high_tide` (preamar) | h | TabuaMare/DHN | próximo extremo **(exceção)** | recusa | coincidência chuva × preamar | sim | D |
| 16 | `tide_range_day_m` (sizígia/quadratura) | m | TabuaMare/DHN | amplitude do dia local de `t` **(exceção)** | recusa | sizígia eleva a preamar | sim | D |
| 17 | `tide_rising` | 0/1 | TabuaMare/DHN | sinal da derivada em `t` | recusa | enchente × vazante | sim | D |
| 18 | `river_level_m` | m (régua própria) | ANA/APAC | última em `B_1` | NaN + flag | nível do rio receptor | sim | Q |
| 19 | `river_level_change_3h_m` | m | ANA/APAC | `nível(t) − nível(t − 3 h)` | NaN | rio subindo | sim | Q |
| 20 | `river_level_above_alert_m` | m | ANA/APAC + `station.attrs` (cota de alerta da mesma régua) | instante | NaN | proximidade do extravasamento (mesma referência) | sim | Q |
| 21 | `sea_level_residual_anom_m` (resíduo) | m | Open-Meteo Marine − tábua, menos a média do treino | última em `B_1` | NaN + flag | sobre-elevação meteorológica não prevista pela tábua | sim (observado/analisado até `t`) | N (histórico a verificar) |
| 22 | `soil_moisture_0_7cm` | m³/m³ | ERA5-Land | última disponível | NaN | infiltração reduzida | **não** em produção (latência da reanálise) | N |
| 23 | `wind_onshore_ms` | m/s | Open-Meteo vento + orientação da costa (parâmetro a definir) | média em `B_6` | NaN | empilhamento de água na costa | sim | D |
| 24 | `pressure_msl_hpa` | hPa | Open-Meteo | instante | NaN | barômetro inverso | sim | D |
| 25 | `pressure_change_24h_hpa` | hPa | Open-Meteo | `p(t) − p(t − 24 h)` | NaN | sistema frontal se aproximando | sim | D |
| 26 | `street_elevation_m` (cota da rua) | m, datum do MDT, **só relativa** | `point_terrain.street_elevation_m` | estática | ponto `NO_DATA` → modelo sem terreno (A.2.4) | ponto baixo acumula | sim | D (4/5 pontos; uso absoluto contra maré = V) |
| 27 | `depression_depth_m` | m | `point_terrain.depression_depth_m` | estática | idem | armazenamento local antes de espalhar | sim | D (4/5) |
| 28 | `contributing_area_m2` (área contribuinte) | m² | `point_terrain.contributing_area_m2` | estática | idem | volume de entrada ∝ área | sim | D (4/5, **em validação**: sensível ao snap, `TERRENO-PE3D.md` §5) |
| 29 | `mean_slope_pct` | % | `point_terrain.mean_slope_pct` | estática | idem | escoamento lento em terreno plano | sim | D (4/5) |
| 30 | `storage_m3_per_cm_10cm` (curva cota-volume) | m³/cm | `point_terrain_curve`, nível de 10 cm acima do fundo | estática | idem | lâmina sobe mais rápido onde o armazenamento é pequeno | sim | D (4/5) |
| 31 | `outside_level_m` (nível lá fora) | m | `máx(rio, maré prevista + resíduo)` | instante e `(t, t+H]` | — | bloqueio da galeria | — | V (e Q pelo rio) |
| 32 | `sill_clearance_m` (folga até a soleira) | m | soleira − nível lá fora | idem | — | galeria afogada se < 0 | — | V e N (soleira sem fonte própria, ADR 0009) |
| 33 | `tide_minus_street_m` | m | maré prevista − cota da rua | `(t, t+H]` | — | refluxo | — | V (**proibida** até Δ aprovado) |
| 34 | `impervious_fraction` | 0–1 | MapBiomas na área contribuinte | estática anual | — | coeficiente de escoamento | sim | N |
| 35 | `hour_local_sin`, `hour_local_cos` | — | calendário, America/Recife | `t` | — | ciclo diurno de convecção; **risco de aprender o horário de notificação** | sim | D |
| 36 | `month_sin`, `month_cos` | — | calendário | `t` | — | sazonalidade da chuva | sim | D |
| 37 | `bucket_depth_mm` (lâmina simulada) | mm (múltiplo da ficha, 5 mm) | modelo do balde (A.3.8), parâmetros do treino | estado em `t` + maré prevista | NaN se ponto sem terreno | balanço de massa | sim | D (versão sem bloqueio por nível lá fora); completa = V |
| 38 | `petri_exceed_prob` | 0–1 | rede de Petri estocástica (export, A.3.8) | estado em `t` + maré prevista | NaN | modelo físico estocástico | depende do tempo de simulação | N |

**Contagem por status** (linhas da tabela): **D = 25** (1–7, 11–17, 23–30, 35–37); **Q = 6** (8–10,
18–20); **V = 3** (31–33); **N = 4** (21, 22, 34, 38). Total 38 linhas. Das 25 "disponíveis", 5 são de
terreno com só 4 pontos cobertos e 1 (`contributing_area_m2`) está em validação.

**Excluídas de propósito**, com motivo: `latitude`/`longitude` (memorizam o ponto, `F1-IA-02.md` §3.4);
`is_high_tide` com limiar de 2,0 m sem origem nem referência vertical (`F1-IA-02.md` §4, item 28);
`altitude` de cadastro (vem do Open-Meteo, diferença média de −2,82 m para o MDT, `TERRENO-PE3D.md` §6);
`distance_to_*` (constantes ou com distribuição diferente entre treino e inferência, `F1-IA-02.md` §4);
temperatura, umidade do ar, radiação e ondas (sem mecanismo de alagamento nos pontos, que não estão na
orla, e 9 delas chegavam zeradas na inferência, F1-I2-01); qualquer rótulo defasado.

### A.2.4 Um modelo multiponto × um modelo por ponto

**Decisão: um único modelo multiponto por horizonte**, com métricas reportadas por ponto (skill §6).

Argumento principal, a quantidade de rótulo por ponto: os eventos se dividem entre 5 pontos. Um modelo
por ponto precisaria, **em cada ponto**, de positivos suficientes para treino, para calibração em
validação separada e para teste, cada um em bloco temporal próprio. Com o critério de `N_min = 20`
eventos só no teste (A.1.4), isso exigiria ao menos 20 eventos de teste por ponto, cinco vezes mais
eventos no total, e ainda sobraria a calibração com poucas dezenas de positivos, onde a isotônica é
instável. O modelo multiponto compartilha o efeito de chuva e maré, que é físico e comum, entre todos os
pontos.

Consequências que precisam constar na defesa:

1. **Feature estática com 4 valores = identidade do ponto.** `street_elevation_m`, `contributing_area_m2`
   e as demais de terreno assumem só 4 valores distintos. O modelo não aprende "efeito da área
   contribuinte"; aprende um efeito fixo por ponto, rotulado com um nome físico. As contribuições
   (`topContributors`, ADR 0007) atribuídas a terreno **não** podem ser lidas como efeito físico. O papel
   físico do terreno fica com o modelo do balde, onde entra por equação (A.3.8).
2. **Interações por ponto são legítimas e esperadas.** O Derby é dominado por maré e a Imbiribeira é plana
   com escoamento lento (glossário, "Ponto crítico"): árvores podem aprender maré × ponto.
3. **Generalização para ponto novo não é demonstrável** com 4–5 pontos. Avaliação deixando um ponto de
   fora entra como análise de sensibilidade e deve ser reportada mesmo se falhar.
4. **JABOATAO_CENTRO** (sem terreno): ou fica fora dos modelos que usam terreno, ou entra com as features
   de terreno ausentes e flag. Decisão do autor (lista ao fim).

---

## A.3 Rótulo e baselines

### A.3.1 Procedimento de casamento ocorrência ↔ ponto ↔ hora

```mermaid
flowchart TD
  A[CKAN Recife: registro bruto<br/>raw_observation, source = CKAN_RECIFE] --> B[flood_occurrence<br/>intervalo, precisão HOUR ou DAY]
  B --> C{label_filter Cobrade<br/>só alagamento}
  C -->|outro tipo| X1[fora do rótulo, contado no manifesto]
  C -->|alagamento| D{geocodificação ok?}
  D -->|não| X2[fora, contado por motivo]
  D -->|sim| E{ponto mais próximo<br/>a ≤ raio de linkage_config<br/>kind = OCCURRENCE_POINT}
  E -->|não| X3[fora: ocorrência sem ponto]
  E -->|sim| F[occurrence_point_match<br/>distance_m, radius_m, config_id]
  F --> G{precisão}
  G -->|HOUR| H["τ em UTC; y_H(p,t) = 1 se τ ∈ (t, t+H]"]
  G -->|DAY| I["I = dia local em UTC; y_H(p,t) = ∅ se I ∩ (t, t+H] ≠ ∅"]
  H --> J[agrupar em eventos por ponto]
  I --> J
  J --> K[dataset: y, labelPrecision, eventId]
```

1. **Filtro de tipo.** `label_filter` aceita só "alagamento". Na Cobrade, alagamento é o código
   1.2.3.0.0, separado de inundação (1.2.1.0.0) e enxurrada (1.2.2.0.0) (Classificação e Codificação
   Brasileira de Desastres; **conferir contra a base e contra o nome real do campo no CKAN**, pendência do
   ADR 0006 §6). Se o CKAN trouxer texto livre em vez de código, a tabela de mapeamento texto → código é
   versionada no `label_filter` e o número de registros por texto vai para o manifesto.
2. **Fuso e precisão.** Data com hora → instante em America/Recife convertido para UTC, precisão `HOUR`;
   só data → `[D 03:00Z, D+1 03:00Z)`, precisão `DAY` (ADR 0006 §4 e teste T3). Nunca se consulta a chuva
   para "adivinhar" a hora: o realinhamento ao pico de chuva foi removido porque tornava o rótulo circular
   (matriz, item 1).
3. **Geocodificação.** `geocode_method` e precisão gravados; falha conta no manifesto. Uma geocodificação
   pelo centroide do bairro tem erro maior que o raio proposto abaixo; essas ocorrências precisam de
   marcação própria e análise com e sem elas.
4. **Casamento espacial.** `NEAREST_WITHIN_RADIUS` com o raio de uma `linkage_config` de
   `kind = OCCURRENCE_POINT`. O ADR 0006 §7 só fixou o valor inicial para sensores (3.000 m, copiado do
   código, `[sem fonte]` como critério). Para ocorrências, 3 km é largo demais: AV_RECIFE_IBURA e
   MASCARENHAS_IMBIRIBEIRA estão a 2.695 m um do outro (cálculo sobre as coordenadas EPSG:31985 de
   `TERRENO-PE3D.md` §3), e os círculos de 3 km se sobrepõem; o rótulo passaria a significar "a região",
   não "o ponto". Os demais pares distam entre 6.268 m (CIN_UFPE–AGAMENON_DERBY) e 14.397 m. **Proposta
   `[sem fonte]`:** raio principal de 500 m, com sensibilidade em {250, 500, 1.000, 3.000} m (cada raio é
   uma `linkage_config` própria; o ADR 0006 já preserva as anteriores). Âncora física aproximada: o raio
   de um círculo com a área contribuinte do Ibura (573.046 m²) é 427 m (cálculo deste documento). O raio
   principal é fixado **antes** de treinar e não é escolhido pelo desempenho do modelo.
5. **Janela temporal.** `y_H(p, t) = 1` se existe ocorrência `HOUR` casada com `p` com `τ ∈ (t, t + H]`;
   `∅` se a janela encosta num dia `DAY` do ponto (A.1.4, regra 1); 0 caso contrário, desde que as
   features obrigatórias existam em `t`.
6. **Máscara pós-evento.** Instâncias com uma ocorrência no ponto em `(t − G, t]` também ficam `∅`: a rua
   pode continuar alagada, e rotular 0 ali ensinaria o modelo a "desligar" no meio do evento. Proposta
   `G = H` `[sem fonte]`.

### A.3.2 Eventos e independência

Ocorrências do mesmo ponto separadas por menos de `G` horas formam um só evento (`eventId`). O evento é a
unidade de contagem de positivos, de agrupamento na validação cruzada (`GroupKFold`) e de reamostragem no
bootstrap dos intervalos de confiança. Contar horas positivas como se fossem independentes infla a
evidência por um fator de até `H`.

### A.3.3 Viés de notificação e leitura do recall

O CKAN registra o que **foi comunicado** à Defesa Civil, não o que alagou. Consequências:

- **Recall medido = recall sobre alagamentos notificados.** Se a notificação depende de fatores que também
  aparecem nas features (chuva forte gera mais ligações, eventos noturnos geram menos), o recall medido
  pode ser maior que o recall sobre todos os alagamentos.
- **Precisão medida é pessimista.** Parte dos "falsos alarmes" são alagamentos não notificados. O rótulo 0
  significa "sem notificação", não "sem alagamento"; o problema tem estrutura de aprendizado com
  positivos e não rotulados.
- **Heterogeneidade.** A taxa de notificação pode variar entre pontos (renda, fluxo de pessoas, atenção da
  imprensa) e ao longo dos anos (mudança do sistema de registro). Medida obrigatória após a carga:
  ocorrências por ano e por ponto, para detectar quebra de série antes de escolher `D₁` e `D₂`.

### A.3.4 Hora do dia e notificação

Se o horário de registro se concentra no expediente, `hour_local_sin/cos` pode aprender quando as pessoas
ligam, não quando alaga. Controle: ablação com e sem as features de hora, e distribuição de `τ` por hora
local comparada com a distribuição da chuva intensa por hora local. Se a ablação não piorar o modelo, a
feature sai.

### A.3.5 Notificação não é início

O `τ` registrado é posterior ao início da lâmina, com atraso desconhecido. Com `H = 3 h`, uma notificação
às `t + 1 h` pode corresponder a uma lâmina que já começou antes de `t`, e o modelo estaria fazendo
detecção, não previsão. Medida: para cada positivo do teste, a chuva já caída em `B_3(τ)` e a fração dos
acertos em que `rain_1h_mm` no instante de emissão já era alta. Reportar a antecedência efetiva, não só a
nominal.

### A.3.6 Alagamento de maré em dia de sol (limitação declarada)

Alagamento por refluxo em preamar de sizígia sem chuva tende a ser **subnotificado** (é recorrente,
previsível e muitas vezes não gera chamado). O modelo vai aprender pouco desse mecanismo, e o recall por
mecanismo (exigido pela skill §6) terá poucos casos de maré. Definição operacional de mecanismo para o
relatório: "maré" se `rain_24h_mm` no ponto for desprezível e houver preamar em `(τ − 3 h, τ + 3 h]`;
"chuva" caso contrário; o limiar de "desprezível" é fixado antes do teste `[a definir]`. Os mesmos eventos
de maré servem à checagem do Δ do ADR 0009; pela ressalva 4 daquele ADR, os que entrarem em qualquer
varredura de Δ são congelados por id e retirados da avaliação do modelo.

### A.3.7 Quantos positivos esperar em 5 anos × 5 pontos

**Não há dado para estimar.** O banco tem 0 eventos (snapshot de 2026-10-10), a quantidade de registros do
CKAN não foi levantada (ADR 0009, "Como validar": "sem fonte; levantar antes") e a fração `HOUR`/`DAY` é
desconhecida. Qualquer número aqui seria inventado.

O que se pode dizer é a forma da conta. Com `N` eventos `HOUR` por ponto em 5 anos, sem sobreposição,
a taxa de instâncias positivas por ponto é `N·H / 43.800` (43.800 = 8.760 h × 5 anos). Por exemplo, **valores
hipotéticos, não estimativas**: `N = 10` e `H = 6` dão 0,14% de positivos; `N = 50`, 0,68%. Em qualquer
cenário plausível o problema é de evento raro, o que justifica PR-AUC, Brier e as estratégias de
desbalanceamento da skill §5.

**Como medir depois da carga** (sobre as tabelas do ADR 0006, antes de qualquer treino, com o resultado
registrado no manifesto e em `docs/medidas/`):

1. Registros CKAN brutos por ano; depois do filtro Cobrade; depois da geocodificação.
2. Casados com algum ponto, por raio (250/500/1.000/3.000 m), por ponto e por precisão (`HOUR`/`DAY`).
3. Eventos distintos (agrupamento por `G`) por ponto, precisão e ano.
4. `N_HOUR,teste` para os cortes `D₁`/`D₂` propostos, que decide a regra de troca da A.1.4.
5. Eventos de mecanismo "maré" pela definição de A.3.6.

### A.3.8 Baselines obrigatórios

Todos rodam no **mesmo split**, sobre o mesmo dataset (`datasetId`) e as mesmas instâncias não mascaradas,
e são avaliados com as mesmas métricas (skill §6).

**B0 — "Nunca alaga".** `p̂ = 0`. Recall 0 e zero falso alarme: ancora a matriz de confusão e o custo. Para
o Brier skill score, a referência é a **climatologia** do treino, `p̂ = π_treino` (taxa de positivos do
treino), porque `p̂ = 0` tem Brier igual à taxa de positivos e não é a referência usual.

**B1 — "Alaga se chuva > X mm/h".** Regra: `ŷ_H(p, t) = 1` se `rain_1h_mm(p, t) > X` (persistência da
chuva atual sobre a janela futura). Para as curvas (PR-AUC), o escore é a própria `rain_1h_mm`.
Escolha de `X` sem vazar: varre-se `X` **só no treino**, maximizando o mesmo critério usado para os
limiares do modelo (custo da A.1.5 ou recall a precisão fixa); `X` é congelado e aplicado à validação e
ao teste. Variante obrigatória: com `rain_max_1h_6h_mm` em vez da chuva da última hora. Se o modelo usar
pluviômetro (status Q liberado), o B1 usa a mesma fonte, para a comparação ser justa.

**B2 — Modelo do balde** (glossário). Estado por ponto: volume armazenado `V_p(t)` em m³; lâmina pela
curva cota-volume.

```
Entrada (m³/h):    Q_in(t)  = C_p · P(t) · A_p / 1000          P em mm/h, A_p em m²
Saída (m³/h):      Q_out(t) = q_p · g(t)                        q_p = capacidade da galeria
Balanço:           V_p(t)   = máx(0, V_p(t−1) + Q_in(t) − Q_out(t))
Lâmina (mm):       h_p(t)   = curva_p⁻¹(V_p(t))                 point_terrain_curve
Fichas:            n_p(t)   = ⌊h_p(t) / 5⌋                     1 ficha = 5 mm
Previsão em t:     simular t+1 … t+H com P = 0 (chuva futura desconhecida) e g da maré prevista;
                   ŷ = 1 se máx h_p ≥ h_crit em (t, t+H]; escore contínuo = máx h_p
```

- O que precisa: `A_p` = `contributing_area_m2`; curva cota-volume (`point_terrain_curve`); chuva
  horária; `C_p` (coeficiente de escoamento, idealmente da impermeabilização do MapBiomas, hoje não
  coletada); `q_p` e `h_crit` (lâmina crítica, em fichas). `C_p`, `q_p` e `h_crit` são calibrados **só no
  treino**, com no máximo 3 parâmetros por ponto ou compartilhados (decisão do autor).
- O termo de bloqueio `g(t)`: no balde completo, `g = 1` se nível lá fora < soleira e `g = 0` (ou
  parcial) se a galeria está afogada. Isso exige nível lá fora e soleira na mesma referência e soleira
  de fonte própria: **bloqueado pelo ADR 0009**. Até lá rodam duas variantes:
  - **B2a** sem bloqueio (`g ≡ 1`): totalmente disponível.
  - **B2b** com bloqueio por limiar de maré calibrado: `g = 0` se `tide_height_tk > λ_p` (em NR). `λ_p`
    é aprendido no treino como parâmetro, sem comparar maré com cota. Atenção: `λ_p` estima
    implicitamente Δ + cota, isto é, **é uma varredura de Δ**; os eventos de treino que o calibram não
    podem ser usados na checagem do Δ do ADR 0009 (ressalva 4 daquele ADR).
- Limitação já visível: AGAMENON_DERBY e MASCARENHAS_IMBIRIBEIRA têm área contribuinte de 61 m² e 34 m²,
  e a sensibilidade ao snap vai de 0 a 13.396 m² no Derby (`TERRENO-PE3D.md` §5). O MDT não vê galerias,
  então o balde nesses pontos recebe quase nenhuma água por construção. Sem cadastro de drenagem, o B2 é
  um baseline fraco nesses dois pontos, e isso deve ser dito, não corrigido com parâmetro livre.
  JABOATAO_CENTRO não tem terreno: B2 não se aplica.

**B3 — Rede de Petri estocástica (Mercury).** Para comparar no mesmo split, o modelo estocástico precisa
exportar, para **cada** instância `(p, t, H)` do teste (e da validação, se for usado em empilhamento):

| Campo | Conteúdo |
|---|---|
| `flood_point_id`, `t_utc`, `horizon_hours` | chave idêntica à do dataset |
| `p_exceed` | fração das replicações com lâmina ≥ `h_crit` em `(t, t+H]` (mesma `h_crit` do balde, em fichas) |
| `depth_mean_mm`, `depth_q50_mm`, `depth_q90_mm` | distribuição da lâmina máxima na janela |
| `n_replications`, `seed` | para refazer o número |
| `model_hash`, `params_hash`, `calibration_end` | versão da rede e dos parâmetros; `calibration_end ≤ D₁` |
| `dataset_id` | SHA-256 do CSV de entrada, o mesmo do manifesto |

Regras: entradas só até `t`, mais maré prevista; parâmetros calibrados só com dado até `D₁`; mesmas
máscaras. Se simular todas as instâncias for caro, simula-se todos os positivos e uma amostra aleatória
estratificada dos negativos, com peso inverso na métrica, e isso é declarado. Como o balde, a parte da
rede que compara nível lá fora com soleira herda o bloqueio do ADR 0009.

### A.3.9 Por que vencer o baseline físico é a prova

O balde tem 2 a 3 parâmetros e codifica conservação de massa: chuva × área entra, galeria tira, maré
bloqueia. Um classificador com dezenas de features que **não** supera B2 (e B3, quando existir) no mesmo
split não acrescentou conhecimento sobre o fenômeno: a capacidade a mais está ajustando ruído, a
identidade do ponto ou o padrão de notificação (A.2.4, A.3.4), e suas explicações (`topContributors`)
contam uma história que o dado não sustenta. Critério: diferença de PR-AUC e de Brier entre modelo e
baseline com intervalo de 95% por bootstrap **em blocos de evento**; "vence" só se o intervalo excluir
zero. Com poucos eventos, o resultado provável é "inconclusivo", e ele deve ser reportado assim (skill
§6), não arredondado para vitória.

---

## Riscos para a validade

1. **Positivos insuficientes.** Sem contagem, nada garante `N_HOUR,teste ≥ 20`; a regra de troca da A.1.4
   mitiga, mas a tarefa diária tem menos utilidade operacional.
2. **Rótulo é notificação.** Recall otimista, precisão pessimista, negativos ruidosos; a taxa de
   notificação pode mudar entre anos e pontos.
3. **Viés de seleção `HOUR`.** Se os eventos com hora não representam os sem hora, o modelo horário aprende
   um subconjunto; a verificação diária com eventos `DAY` é o único teste disso.
4. **Antecedência ilusória.** Notificação depois do início da lâmina transforma parte da previsão em
   detecção (A.3.5).
5. **Treino × produção.** Archive (reanálise) no treino e current na produção (ADR 0006, limitação 2);
   valores revisados no backtest (limitação 1).
6. **Terreno = identidade do ponto** com 4 pontos; nenhuma generalização espacial demonstrável.
7. **Mecanismo de maré pouco observável**: subnotificado e sem Δ vertical; as features que o explicariam
   (31–33) estão bloqueadas.
8. **Quarentena de fuso pode não se resolver**: perde-se a chuva medida e o rio (6 features).
9. **Escolhas de pesquisador** (raio, `G`, corte diário, `N_min`, limiar de "chuva desprezível"): cada uma
   é fixada antes do treino e testada em sensibilidade, ou vira caminho bifurcado não declarado.
10. **Área contribuinte instável** ao snap e cega a galerias; o baseline físico é fraco em 2 dos 4 pontos.

## Decisões que dependem do autor

1. Confirmar a decisão da pergunta 17: tarefa horária (a) com `H = 6 h` principal e `3 h` secundário,
   máscara dos dias `DAY` e verificação diária.
2. Fixar `N_min` (proposta: 20 eventos `HOUR` no teste) e aceitar a troca automática para a tarefa diária
   (b) abaixo dele.
3. Horário de corte da tarefa diária, se ela for acionada (proposta: 06:00 local).
4. Raio principal de casamento ocorrência ↔ ponto (proposta: 500 m) e o conjunto de sensibilidade.
5. Janela de agrupamento e máscara pós-evento `G` (proposta: `G = H`).
6. Razões de custo `c_FN/c_FA` para `MEDIUM` (prontidão) e `HIGH` (mobilização), ou a taxa máxima de falso
   alarme por ponto por mês aceita pela Defesa Civil.
7. JABOATAO_CENTRO: fora dos modelos com terreno, ou dentro com terreno ausente e flag.
8. Datas de corte `D₁` e `D₂`, depois de ver a série de ocorrências por ano (sem ver o desempenho do
   modelo).
9. Coletar ou não: histórico do Open-Meteo Marine (resíduo), umidade do solo ERA5-Land e MapBiomas
   (features 21, 22, 34).
10. Se o balde usa parâmetros compartilhados entre pontos ou por ponto, e se a variante B2b (limiar de
    maré calibrado) entra, sabendo que ela consome eventos que não podem checar o Δ do ADR 0009.
11. Quem exporta a rede de Petri no formato da A.3.8 e com que número de replicações.
12. Origem da coluna `severity` do export atual: se não for lâmina observada, a severidade ordinal sai das
    tarefas supervisionadas.
