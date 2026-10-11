# Pré-registro do protocolo de avaliação (F1)

- Tarefa: F4-IA-10 · Data: 2026-10-11 · Autor: Agente 3 (nível Máximo) · Versão 1
- Desenho: `docs/MODELAGEM-IA.md` §0–11 (abaixo, "MODELAGEM"). Parâmetros: `docs/PARAMETROS-MODELO-FISICO.md` (abaixo,
  "PARAMETROS").
- **Commit do pré-registro:** `77e41dea4533a05288d53c4e0140a6a3ba1512f9` (2026-10-11, branch `revisao/fase-0`). Qualquer alteração posterior é **emenda**, registrada na seção de emendas com data e motivo, e só vale se feita antes da etapa de junção com os rótulos.
- **Escores cegos da etapa 1 (congelados antes da junção):** `avaliacao-f1/escores_cegos.csv` SHA-256 `4615d416e788729a7e5d5b34b1e4df8abff84cfc7e84a79860f14ed2e826b1a5` (1 371 dias, 49 356 linhas; código `projeto-mapi-ai` feat/avaliacao-f1 f6ddacf; 2 execuções idênticas).
- **Confirmação do autor:** solicitada em 2026-10-11 (pergunta 30 do PROGRESSO.md); a etapa 2 (junção com rótulos) só roda depois dela.
- **SHA-256 deste arquivo no commit:** `[preencher]`
- **Confirmação do autor (Lucas) das escolhas da §2 e da §4:** `[ ] confirmado em ____ ` (exigida pela REVISAO-CRITICA,
  RC-25)
- **Leitura do orientador (recomendada, RC-11):** `[ ] feita em ____`
- Regra-mãe: **nenhuma saída de modelo nos dias de 2021-04-01 a 2024-12-31 é juntada aos rótulos, e nenhuma métrica,
  gráfico ou inspeção que combine as duas coisas é feita, antes do commit acima e do commit dos escores da §5, passo 5.**
  Isso vale também para conferências parciais ("só ver se acerta 2024-06-15").

## 1. Hipóteses

Confirmatórias, testadas em ordem hierárquica. A H2 só é testada se a H1 for positiva, o que controla o erro de família
sem precisar de correção.

| Id | Hipótese | Comparação | Unidade |
|---|---|---|---|
| **H1** | M1a ordena os dias com registro acima dos dias sem registro melhor que a climatologia sazonal | ΔAUC = AUC(M1a) − AUC(B2) > 0 | dia × Recife, 2023–2024 |
| **H2** | M1a ordena melhor que a regra de chuva de IDF | ΔAUC = AUC(M1a) − AUC(B3) > 0 | idem |
| H3 | Mesmas H1 e H2 para a rede de Petri (M2) | idem, com `p_cidade` | idem. **Confirmatória só por emenda** registrada antes da §5, passo 5, se a Petri e a tabela de parâmetros dela (MODELAGEM §2.2 a, b) chegarem a tempo. Senão é exploratória |
| H4 | O emulador reproduz a Petri | §6.4 | ponto-dia, forçantes sem rótulo e 2021–2024 |

Secundárias pré-registradas (descritivas, sem correção para multiplicidade, rotuladas como secundárias): métricas
binárias da §6.2; AUC contra 0,5 de cada escore; análise com os 18 positivos; variantes de sensibilidade da §6.3; forçante
alternativa (pluviômetro, chuva corrigida por ML); Q3 por bairro; estudo de caso por ponto. Qualquer outra análise é
**exploratória**.

## 2. Conjunto de teste

### 2.1 Positivos: os 18 dias com alagamento registrado

Ocorrências canônicas de "alagamento" no CKAN Recife, todas com precisão `DAY`. Dia em America/Recife. Fontes:
F2-BACK-16 §3 (2021, 2023, 2024) e F2-BACK-17-20, F2-BACK-18 (2022). Consulta no Apêndice B.

| Ano | Dias (ocorrências) | n | Papel |
|---|---|---|---|
| 2021 | 05-13 (1) | 1 | P_extra |
| 2022 | 05-28 (1), 07-08 (15), 07-12 (12) | 3 | P_extra |
| 2023 | 02-06 (13), 05-24 (11), 06-14 (9), 06-27 (10), 06-30 (16), 07-07 (16) | 6 | **P_principal** |
| 2024 | 01-15 (3), 02-16 (21), 02-27 (9), 04-04 (13), 05-07 (13), 05-28 (6), 06-15 (28), 06-25 (28) | 8 | **P_principal** |
| **Total** | | **18** | 14 principais + 4 extras |

**Episódios:** dias a ≤ 3 dias de distância formam um episódio (MEMO §1.2). Só 2023-06-27 e 2023-06-30 se juntam
(2022-07-08 e 07-12 distam 4 dias). Resultado: 13 episódios em P_principal e 17 nos 18 dias. O episódio é a unidade de
reamostragem dos positivos.

### 2.2 O problema: ausência de registro não é ausência de alagamento

O negativo possível é "dia sem registro". Ele só informa quando o registro do ano funcionava. Sensibilidade do registro
por ano (F2-BACK-16 §1 e Reprocessamento; F2-BACK-17-20):

| Ano | Registros no recurso | Ocorrências "alagamento" | Leitura |
|---|---|---|---|
| 2021 | 64 855 | 1 | O registro de alagamento praticamente não existe. Negativos sem valor |
| 2022 | 90 425, com o tipo vazio em 84 879 (colunas deslocadas) | 28, em 3 dias | O tipo é desconhecido em 94% dos registros (84 879 / 90 425). Negativos sem valor |
| 2023 | 83 164 | 75 | Registro em funcionamento |
| 2024 | 80 173 | 121 | Registro em funcionamento |
| 2025 | 1 | 0 | Sem registro |
| 2026 | sem recurso | — | Sem registro |

**Decisão:**

1. **Negativos (N): só 2023-01-01 a 2024-12-31**, nos dias sem ocorrência canônica, com duas exclusões:
   (a) o dia anterior e o seguinte a cada positivo, porque a data `DAY` é a do registro e o alagamento pode ter começado
   na véspera ou continuado no dia seguinte (28 dias, sem sobreposição: 2023-06-27 e 06-30 distam 3 dias);
   (b) dias com forçante incompleta (MODELAGEM §2.4), contados e reportados.
   Conta antes de (b): 731 − 14 − 28 = **689 dias**.
2. **N significa "sem registro"**, não "sem alagamento". A métrica sobre N chama-se **taxa de alerta em dias sem
   registro**, nunca "taxa de falso alarme". Ela é pessimista para o modelo, porque parte desses alertas pode ser
   alagamento não registrado (Histórico A.3.3 da MODELAGEM).
3. **P_extra (2021, 2022):** os registros contam como positivos, mas esses anos não fornecem negativos. Entram só em
   análises secundárias de acerto: recall em 4 e em 18 dias, e posto percentil do escore do dia positivo na distribuição
   do escore em N.
4. **2021-10 a 2022-12 sem registro, e 2025–2026:** sem rótulo, fora da avaliação (MEMO §5, item 9, estendido aqui a
   2022 pela leitura da tabela acima). **2025-01-01 a 2026-09-30 é o período de desenvolvimento**: pode-se rodar os
   modelos ali para depuração (§5, passo 4), porque não existe rótulo nele.
5. Esta é a única definição de N. Não haverá "versão com 2022 como negativo" nem outra variante de N.

**Leitura sob positivos e não rotulados** (MODELAGEM §6): se os alagamentos não registrados se parecerem com os
registrados, `AUC_medido = (1 − θ) · AUC_real + θ · 0,5`, o que é conservador. Se o registro depender da intensidade da
chuva, o viés pode favorecer escores de chuva (B3). Isso é declarado, não corrigido.

### 2.3 Exclusões por dado

- Dia positivo sem forçante completa: sai, é listado e **não** é substituído. 2021-05-13 está fora da janela do export
  (2021-10-01 a 2026-10-01; MEMO §1.1) e depende de *backfill* da chuva desde 2021-04-01 (aquecimento de 30 dias).
- Os primeiros 30 dias de cada simulação contínua são aquecimento e não entram em nenhuma métrica.

## 3. Modelos e baselines congelados

Especificação na MODELAGEM §2–§5. Versão principal e variantes:

| Id | O que é | Papel |
|---|---|---|
| **M1a** | Balde, `g ≡ 1`, valores centrais da §4.2, chuva Open-Meteo archive, escore lexicográfico (S, depois R) | principal |
| M1-sens | M1a com cada parâmetro na faixa da §4.2 | sensibilidade (§6.3) |
| M1-ef | M1a com limiar por igual frequência (MODELAGEM §2.3) | secundária |
| M1-pluv | M1a com forçante de pluviômetro ou chuva corrigida por ML, se disponível antes da §5, passo 5 | secundária |
| M1b | Balde com bloqueio por maré | **não roda**: sem `λ_NR` com fonte, e a faixa de soleira do PARAMETROS exige Δ (ADR 0009) |
| M2 | Rede de Petri (`p_cidade` = máximo dos `p_exceed`; `p_any` como secundária) | H3 |
| B0 | Nunca alaga | âncora |
| B1 | Climatologia constante (taxa de positivos do teste) | referência do BSS |
| B2 | Normal mensal de chuva do Recife, 1991–2020 | H1 |
| B3 | Chuva horária máxima do dia nos 4 pontos; alerta se ≥ 50,3 mm em 60 min | H2 |
| B3-50 / B3-70 / B3-24h | 25,2 mm/h; 35,2 mm/h; 30 mm em 24 h (PLACON: mobilização por previsão, não limiar de alagamento) | secundárias |

Agregação para o dia × Recife (todos os modelos e baselines espaciais): máximo sobre AV_RECIFE_IBURA, CIN_UFPE,
AGAMENON_DERBY e MASCARENHAS_IMBIRIBEIRA (MODELAGEM §3).

## 4. Regra de parametrização

### 4.1 Regra geral

1. Todo parâmetro tem uma linha na tabela da §4.2 com: valor central, faixa, fonte citada (documento, página, equação ou
   tabela), data de acesso e qualidade (primária, secundária ou sem fonte).
2. **Fontes admissíveis:** norma, manual ou documento de órgão público; literatura (com a qualidade declarada); cadastro
   oficial; cálculo sobre dado externo que não use os registros de alagamento.
3. **Fontes inadmissíveis:** o CKAN de 2021–2024; notícias sobre esses dias; os números do MEMO, Apêndice B; qualquer
   métrica sobre o conjunto de teste; a memória do autor sobre eventos; estudos cujo resultado foi medido em ocorrências
   que se sobrepõem às do MAPi (estes só como verificação de consistência declarada, como Fonseca Neto et al., 2026).
4. **Mais de uma fonte para o mesmo parâmetro:** vale a decisão registrada na §4.2. Para parâmetros novos (por exemplo,
   os da Petri), a ordem é: documento oficial local > literatura revisada > secundária. Dentro do mesmo nível, o valor
   central é a mediana dos candidatos e a faixa vai do mínimo ao máximo.
5. **Parâmetro sem fonte:** recebe uma faixa justificada por limites físicos e um valor central fixado **neste
   documento**. **Nunca é ajustado aos eventos.** Os resultados são reportados em toda a faixa (§6.3), e a conclusão fica
   condicionada a ela (§7).
6. **Congelamento:** a tabela abaixo fica congelada pelo commit deste pré-registro. Mudança antes da §5, passo 5, só por
   emenda datada na §8 com motivo, por exemplo uma fonte nova que chegou (cadastro da EMLURB). Depois do passo 5, qualquer
   mudança gera análise exploratória.

### 4.2 Tabela congelada

Fonte: PARAMETROS (F4-IA-11, 2026-10-11) e decisões de método do Arquiteto na mesma data.

| Parâmetro | Central | Faixa | Fonte | Qualidade |
|---|---|---|---|---|
| `C` | 0,86 | 0,70 – 0,95 | PARAMETROS linha 1 (Silva Júnior 2015 via Xavier Filho 2016; URB Recife 2018, p. 7) | secundária |
| `TR` de projeto da microdrenagem | 5 anos | 2 – 10 anos | linha 7 (URB Recife 2018, p. 8) | primária |
| `i_T = i(TR, 60 min)` | 60,1 mm/h | 50,3 (TR 2) – 68,7 mm/h (TR 10) | Silva e Araújo (2013), Eq. 16; tabela de limiares do PARAMETROS | primária |
| `h_crit` | 15 cm (30 fichas) | 10 – 30 cm | linha 9 | secundária |
| `Ia` | 5,2 mm | 1,0 – 9,0 mm | linha 4 | registrada; **fora de M1a** (contaria a perda duas vezes junto com `C`) |
| `f`, fração da capacidade de projeto | 1,0 (hipótese declarada: galeria como projetada) | 0; 0,2; 0,4; 0,6; 0,8; 1,0 | `[sem fonte]` | só faixa |
| Curva cota-volume | MDT (`point_terrain_curve`) | ±30% no volume por cm | `[sem fonte]` | só faixa |
| `A_p` (snap) | 10 m | {5, 10, 20} m | `TERRENO-PE3D.md` §5.1, premissa da Fase 3 | terreno |
| Soleira / `λ_NR` | — | não executável (ADR 0009) | `[sem fonte]` | M1b fora |
| Limiar de B3 | 50,3 mm em 60 min (TR 2) | 47,4 – 55 mm/h (EMLURB/PDDR; Torres et al. 2025) | Silva e Araújo (2013), Eq. 16 | primária |
| Limiares secundários de B3 | 25,2 e 35,2 mm/h (50% e 70%); 30 mm/24 h | — | decisão do Arquiteto; PLACON 2024 | — |
| Normal mensal de chuva (B2) | `[a obter]` | — | APAC (Atlas 1991–2020) ou INMET (Normais 1991–2020) | **bloqueia H1** |
| Duração de referência | 60 min | — | resolução da grade horária | escolha fixada |
| Parâmetros da Petri | `[a declarar pelo autor]` | — | MODELAGEM §2.2 a | bloqueia H3 |

### 4.3 Chuva de reanálise × limiar de pluviômetro

Antes de aplicar qualquer limiar derivado de pluviômetro ou de IDF pontual, compara-se a distribuição da chuva horária
Open-Meteo com a do pluviômetro de fuso verificado mais próximo, **sem nenhuma informação de alagamento** (§5, passo 2).
O resultado não muda os valores da §4.2. Ele: (a) documenta se a análise principal está "limitada pela escala da
forçante"; (b) alimenta a variante M1-ef e o limiar equivalente de B3-ef (mesma transferência por igual frequência),
ambos secundários.

## 5. Procedimento de execução (cego em duas etapas)

1. **Commit** deste pré-registro, com a §4.2 congelada e o campo de commit preenchido. A confirmação do autor fica
   registrada no cabeçalho.
2. **Comparação de distribuições** (§4.3), feita só com chuva, sobre todas as horas comuns disponíveis. Relatório
   commitado.
3. **Implementação** de M1, dos baselines e do script de avaliação (F4-IA-13). O script é testado com **rótulos
   sintéticos** (datas sorteadas com semente) e precisa rodar de ponta a ponta sem os rótulos reais. Commit.
4. **Período de desenvolvimento** (2025-01-01 a 2026-09-30, sem rótulo): rodar M1 e conferir fechamento do balanço de
   massa, ausência de NaN, horas "fora da curva" e frequência de alertas. Tudo vai para um log commitado. **Nenhum
   parâmetro muda com base nisso.** Correção de bug é permitida só quando o código diverge das equações da MODELAGEM §2.1,
   e fica registrada na §8.
5. **Escores cegos:** rodar todos os modelos e variantes de 2021-04-01 a 2024-12-31 e gravar um CSV
   `(modelo, variante, data, escore, alerta)`. O SHA-256 do CSV é **commitado antes** da junção. As entradas são
   identificadas pelo SHA-256 do export e pelo dump pós-carga (`24aa31ce…`, HANDOFF-fase-2).
6. **Junção e avaliação:** a lista dos 18 dias sai da consulta do Apêndice B, e o script do passo 3 roda **uma vez**.
   Saídas commitadas. Nova execução só para corrigir erro do script, com o diff na §8.
7. Qualquer análise além da §6 é **exploratória** e rotulada assim no texto da dissertação.

## 6. Métricas e testes estatísticos

Semente de toda reamostragem: `20261011`. Réplicas de *bootstrap*: 10 000. O IC é percentil de 95%.

**Bootstrap estratificado:** os positivos (P_principal) são reamostrados por episódio (13). Os negativos (N) são
reamostrados em blocos de semana ISO, preservando a dependência entre dias vizinhos.

### 6.1 Primárias (H1, H2; e H3 se promovida)

- AUC de Mann–Whitney com empates valendo 0,5, P_principal (14) × N (689).
- ΔAUC pareado (mesmas réplicas para os dois escores) com IC por *bootstrap*. Teste: o IC exclui 0?
- Referência de poder (calculada antes, Apêndice A): com 14 × 689, o menor AUC cujo IC exclui 0,5 é ≈ 0,66 (supondo
  positivos independentes). A diferença detectável fica em ≈ 0,07 a 0,15, dependendo da correlação entre os escores
  (0,9 a 0,5).

### 6.2 Secundárias pré-registradas

- **Binárias, nos limiares congelados:** recall k/14 e k/18 com IC exato de Clopper–Pearson; taxa de alerta em N com IC
  exato; McNemar exato entre M1a e B3 nos positivos (p bilateral = 2 · P(X ≤ mín) com X ~ Bin(m, 0,5), em que m é o número
  de discordantes) e, à parte, em N.
- **P_extra:** acertos em 4 dias e posto percentil do escore de cada dia extra na distribuição em N.
- **M2:** Brier e BSS contra B1, com IC por *bootstrap*; curva de confiabilidade só descritiva.
- **Todos os escores:** AUC contra 0,5.
- **Verificação de consistência declarada:** comparar o comportamento de B3-50 com o relatado por Fonseca Neto et al.
  (2026), sem peso inferencial.

### 6.3 Sensibilidade a parâmetros

- Um de cada vez: cada parâmetro da §4.2 nos extremos da faixa e, para `f`, em todos os 6 níveis.
- Combinado: os 2³ = 8 cantos de {`f` ∈ {0,2; 1,0}, curva ±30%, snap {5, 20} m}, todos parâmetros sem fonte ou instáveis.
  `f = 0` não entra nos cantos: com galeria totalmente obstruída, `S` é só armazenamento e entra no "um de cada vez".
- Para cada variante: ΔAUC de H1 e H2 com IC e a categoria resultante (§7). Reporta-se a família inteira. **Nenhuma
  variante é escolhida como "a melhor".**

### 6.4 Emulador (H4)

- **Fidelidade:** em forçantes de teste disjuntas das de treino, `|p_emul − p_Petri|` mediano ≤ erro de Monte Carlo
  mediano da Petri, `√(p(1 − p)/n_rep)`. O critério não usa valor sem fonte: pede que o erro do emulador seja
  indistinguível do ruído da própria simulação.
- **Também reportados:** MAE, fração dentro de ±2 erros de Monte Carlo, comparação com o emulador ingênuo (média
  climatológica por ponto).
- **Custo:** tempo de inferência para os 4 pontos, comparado com o da Petri e com o ciclo de previsão (o
  `DataCollectionTask` roda a cada 3 600 000 ms = 1 h, `integracao/fase-2`). Cabe se a inferência for menor que o ciclo.

## 7. O que conta como resultado

| Categoria | Critério (H1 ou H2) | O que se pode afirmar |
|---|---|---|
| **Positivo robusto** | IC de ΔAUC > 0 na versão principal **e** em todas as variantes da §6.3 com parâmetro sem fonte (`f`, curva, snap) | "Com os parâmetros externos e em toda a faixa dos parâmetros sem fonte, o modelo ordena os dias com registro melhor que B2/B3 em 2023–2024 (n = 14)." Com o IC e as ressalvas da MODELAGEM §7 |
| **Positivo condicional** | IC > 0 na principal, mas a categoria muda dentro da faixa de algum parâmetro sem fonte | Conta como **inconclusivo** para a pergunta. Afirma-se só: "positivo se a galeria/terreno estiver em X; o dado que decidiria isso (cadastro) não existe" |
| **Negativo** | IC de ΔAUC < 0 na principal | "O modelo físico parametrizado externamente ordena pior que B2/B3." Resultado válido e reportado |
| **Inconclusivo** | IC contém 0 | "Não distinguível de B2/B3 com 14 positivos." Não se afirma equivalência (sem margem com fonte e sem poder) |
| **Degenerado** | M1a alerta em 0% ou 100% dos dias de N, ou `S = 0` em todos os positivos | As métricas binárias não informam. O AUC usa o escore lexicográfico (cai em `R`, M1 ≈ B3). H2 fica inconclusivo por degeneração, lido à luz da §4.3 (escala da forçante) |

H4: **positivo** se o critério de fidelidade da §6.4 for atingido; senão, negativo. O custo é reportado à parte.

**Efeito dos parâmetros sem fonte nas conclusões, dito agora:** galeria (`f`), curva cota-volume e soleira não têm
fonte. Toda conclusão sobre M1 fica condicionada à hipótese de "galeria como projetada, sem obstrução", e o mecanismo de
maré fica fora. O resultado mais provável, dado o cancelamento de `C` e `A` (MODELAGEM §2.1) e a escala da forçante, é
**inconclusivo ou degenerado em H2**. Nesse caso, a dissertação lê o resultado no enquadramento da opção G (MEMO §6),
como foi declarado na MODELAGEM §0, sem trocar de desenho depois de ver o dado.

## 8. Desvios e emendas

| Data | Tipo (emenda antes do passo 5 / desvio / bug) | O que mudou | Motivo | Commit |
|---|---|---|---|---|
| — | — | — | — | — |

## 9. Conhecimento prévio do conjunto de teste (contaminação declarada)

1. **A chuva dos dias positivos já foi olhada** (MEMO, Apêndice B, grade do Derby, 2023–2024): a regra "≥ 10 mm/dia" cobre
   8/14; "≥ 20 mm" cobre 3/14; 2024-01-15 teve 0,2 mm; 2024-02-16 teve 3,5 mm (17,5 mm na véspera). Consequências: um
   modelo só de chuva vai perder 2024-01-15 quase com certeza, e isso já se sabe; o limiar de B3 veio de IDF, não desses
   números (decisão do Arquiteto; MEMO §5, item 15).
2. **Datas, ocorrências e bairros** de cada dia positivo são conhecidos (F2-BACK-16 §3).
3. **Fonseca Neto et al. (2026)** relatam o desempenho de regras de 50% a 100% de 55 mm/h contra ocorrências do Cemaden no
   Recife de 2016 a 2025 (PARAMETROS, "Limiar da regra de chuva"), período que se sobrepõe ao teste. Por isso o estudo só
   entra como verificação de consistência.
4. **O autor conhece eventos de alagamento do Recife** por vivência. Não dá para cegar; fica declarado.
5. **Até esta data, nenhum escore de M1, M2 ou dos baselines foi calculado nos dias de 2021–2024.** O modelo legado
   (`flood_classifier.joblib`, F1-IA-02) não tem relação com este protocolo.

## 10. Dependências e bloqueios

| # | Dependência | Responsável | Bloqueia | Estado em 2026-10-11 |
|---|---|---|---|---|
| 1 | Valores de `C`, `TR`/IDF, `h_crit`, `Ia`, limiar de B3 | F4-IA-11 | passo 1 | **levantados** (PARAMETROS); congelar no commit |
| 2 | Normal mensal de chuva do Recife, 1991–2020 (APAC ou INMET), com fonte | F4-IA-11 ou Arquiteto | H1 (passo 1) | `[a obter]` |
| 3 | Confirmação do autor da §2 (2021/2022/2025–2026) e da §4.2 | Lucas | passo 1 | pendente (RC-25) |
| 4 | Comparação reanálise × pluviômetro sem rótulos (§4.3); depende da cobertura histórica do Cemaden verificado | Dados | passo 2 (só a interpretação e as variantes -ef) | `[cobertura a medir]` |
| 5 | *Backfill* da chuva Open-Meteo de 2021-04-01 a 2021-09-30 (2021-05-13) e de 1991–2020 (só se a variante -ef usar a grade), com o modelo de reanálise fixado e o mesmo em todos os períodos | Backend/Dados | passo 5 (2021-05-13; variante) | pendente; modelo do archive no export `[a verificar]` |
| 6 | Implementação de M1, baselines e script de avaliação com teste de rótulos sintéticos; agregação diária | F4-IA-13 | passos 3–6 | pendente |
| 7 | Consulta fixa da lista de positivos (Apêndice B) conferida no banco | Backend | passo 6 | pendente |
| 8 | Export da Petri e tabela de parâmetros dela (MODELAGEM §2.2) | Lucas | só H3 e H4 | bloqueado (F4-PED-02) |
| 9 | Cadastro de microdrenagem com cota de fundo e diâmetro (LAI à EMLURB/SESAN) | Lucas | nada (sem ele, `f` e soleira ficam como faixa) | a enviar |
| 10 | Δ NR↔MDT (ADR 0009) | Geo / pedidos CHM, IBGE | nada (M1b fica fora) | aguardando |
| 11 | Polígonos oficiais de bairro | Geo | só Q3 | `[a obter]` |

**O que bloqueia o início da execução no teste (passo 5):** 1 a 3, 6 e, para 2021-05-13, o 5. O 4 bloqueia apenas a
leitura e as variantes -ef. Sem o 2, H1 não pode ser testada e a hierarquia trava H2. Nesse caso, ou se obtém a normal
antes do commit, ou se emenda a §1 antes do passo 5 para que H2 seja a primária única.

## Apêndice A — Contas deste documento

- IC exato de Clopper–Pearson (bisseção sobre a binomial exata, Python 3 com `math.comb`, mesmo método do MEMO, Apêndice A):
  7/14 → [0,230; 0,770]; 10/14 → [0,419; 0,916]; 14/14 → [0,768; 1]; 9/18 → [0,260; 0,740]; 2/4 → [0,068; 0,932];
  35/689 → [0,036; 0,070]; 0/689 → [0; 0,005].
- EP do AUC por Hanley e McNeil (1982): `EP² = [A(1−A) + (n₁−1)(Q₁−A²) + (n₂−1)(Q₂−A²)] / (n₁n₂)`, com `Q₁ = A/(2−A)` e
  `Q₂ = 2A²/(1+A)`. Para n₁ = 14 e n₂ = 689: EP = 0,081 (A = 0,6), 0,079 (0,7), 0,076 (0,75), 0,072 (0,8). Menor A com
  `A − 1,96·EP > 0,5`: 0,658. Supõe positivos independentes; com 13 episódios, o IC real é mais largo.
- ΔAUC: `EP_Δ = √(EP₁² + EP₂² − 2r·EP₁·EP₂)`. Com EP ≈ 0,076 e r = 0,5 / 0,8 / 0,9 → 0,076 / 0,048 / 0,034; o
  detectável (1,96·EP_Δ) fica ≈ 0,15 / 0,095 / 0,067.
- Taxa de base em P_principal ∪ N: 14 / 703 = 2,0%.
- Monte Carlo: `n_rep ≥ 0,25 / 0,01² = 2 500` para EP ≤ 0,01 em p = 0,5.
- 50% e 70% de 50,3 mm/h: 25,15 e 35,21 mm/h (arredondados para 25,2 e 35,2).

## Apêndice B — Consulta da lista de positivos

Ocorrências canônicas (filtro de F2-BACK-16 §1), tipo "alagamento" (`label_filter`), dia local:

```sql
WITH canon AS (
  SELECT o.* FROM flood_occurrence o
  WHERE NOT EXISTS (SELECT 1 FROM flood_occurrence d
                    WHERE d.ckan_record_id = o.ckan_record_id
                      AND d.occurred_from = o.occurred_from AND d.id < o.id))
SELECT (occurred_from AT TIME ZONE 'America/Recife')::date AS dia, count(*) AS ocorrencias
FROM canon
WHERE (occurred_from AT TIME ZONE 'America/Recife')::date BETWEEN DATE '2021-01-01' AND DATE '2024-12-31'
GROUP BY 1 ORDER BY 1;
```

A saída esperada é a tabela da §2.1 (18 linhas). Se a consulta devolver outra lista, a execução para e a diferença é
registrada na §8 antes de prosseguir. Isso é conferência do rótulo, não do desempenho. Conferir no banco se o filtro de
tipo já está aplicado em `flood_occurrence` ou precisa de junção com `label_filter` `[a verificar no passo 7 da §10]`.

## Emendas

| Data | Emenda | Motivo | Antes da junção com rótulos? |
|---|---|---|---|
| 2026-10-11 | Fonte e valores da normal mensal de chuva de B2 (§4.2, linha "Normal mensal", antes `[a obter]`): INMET, Normais Climatológicas 1991–2020, estação 82900 Recife (Curado), https://portal.inmet.gov.br/uploads/normais/Normal-Climatologica-PREC.xlsx, acessado em 2026-10-11 (SHA-256 do xlsx `903ba7d9…8c19`). mm/mês, jan a dez: 106,6; 127,0; 197,2; 268,9; 317,1; 390,5; 314,8; 186,7; 93,3; 52,8; 39,0; 61,6 (ano 2155,5). Escore de B2 no dia = normal do mês local do dia. Desbloqueia H1 | O protocolo não trazia fonte (§10, item 2). Série 1991–2020, sem sobreposição com o teste; escolha feita sem olhar rótulos nem escores (F4-IA-13, etapa 1 cega) | sim — antes da junção |
| 2026-10-11 | Convenções de implementação da etapa 1 (sem mudar §4.2): (a) a razão `R(p,d)` com `f = 0` usa `f = 1` no denominador, porque a razão é indefinida; (b) B3-24h = máximo no dia local da soma móvel das 24 h anteriores, depois máximo nos 4 pontos; (c) M1-ef, B3-ef, M1-pluv e B1 não são calculados na etapa 1: M1-ef/B3-ef exigem ≥ 1 ano de horas comuns com pluviômetro (não existe: o banco só tem Cemaden de 2026-10-09 em diante, sem horas comuns com o archive) ou o backfill 1991–2020; B1 depende da taxa de positivos (constante, não altera AUC); (d) a umidade do solo ERA5-Land não entra em M1a nem nas baselines | Fechar lacunas de especificação antes dos escores; nada disso usa rótulo | sim — antes da junção |
