---
name: mapi-ml-pipeline
description: Regras obrigatórias do pipeline de ML do MAPI (projeto-mapi-ai — XGBoost, LSTM, scikit-learn, FastAPI) para previsão de alagamento na RMR. Use sempre que for montar dataset a partir de /api/export/ia-dataset, criar features, treinar, avaliar, calibrar, comparar algoritmos com baselines, versionar modelo ou mexer no contrato /v1/predict/flood — mesmo que o pedido só diga "treina o modelo" ou "melhora a acurácia".
---

# MAPI — pipeline de ML

Os resultados entram numa dissertação de mestrado: cada número precisa ser reproduzível
e defensável em banca. As regras abaixo existem para impedir os erros que uma banca
encontra primeiro.

## 1. Tarefa

- Principal: classificação binária — haverá alagamento no ponto `p` em `[t, t+H]`?
  `H ∈ {3 h, 6 h}`, um modelo por horizonte.
- `LOW/MEDIUM/HIGH` é **saída**, obtida por limiares sobre a probabilidade calibrada.
  Limiar vem de critério escrito (custo FN vs. falso alarme), com a curva que o sustenta.
  Número redondo sem curva não é aceito.

## 2. Vazamento temporal (o erro mais grave)

- Divisão **temporal**: treino até data D, validação/teste depois. Nunca `shuffle=True`,
  nunca `train_test_split` aleatório, nunca `KFold` comum. CV = blocos por evento
  (`GroupKFold` por id de evento, ou `TimeSeriesSplit` com intervalo de guarda ≥ H).
- Feature só usa informação disponível em `t`. Acumulados são janelas **para trás**.
- Exceção legítima e declarada: maré prevista (tábua DHN/TabuaMare) em `t+1..t+6`, pois é
  previsão determinística publicada antes do evento. Escreva isso no relatório.
- Scaler/imputador/encoder é ajustado **só no treino** (use `Pipeline`).
- Rótulo de alagamento não pode aparecer, nem defasado, como feature.

## 3. Features

Toda feature declarada numa tabela: nome, unidade, fonte, janela, tratamento de nulo,
justificativa física. Terreno (PE3D) entra por **junção com a tabela de pontos**
(`ponto_terreno`), não por cópia manual. Nomes e unidades seguem `docs/GLOSSARIO.md` e
precisam casar com os do modelo estocástico (rede de Petri/Mercury: ficha = 5 mm, lâmina,
nível lá fora). Cota MDT só entra em comparação absoluta com maré depois que o ADR de
referência vertical estiver aprovado; antes disso, use cota relativa.

## 4. Rótulo

CKAN Recife, filtrado por tipo: **só "alagamento"** (decisão do autor; "inundação", enxurrada e deslizamento ficam fora). Precisão temporal HOUR/DAY gravada por ocorrência (ADR 0006); a formulação que trata eventos só com data está em `docs/MODELAGEM-IA.md`. Critério
espacial (raio) e temporal de casamento ocorrência↔ponto↔hora documentado. Declarar o
viés de notificação (recall medido é recall sobre o que foi comunicado) e a
subnotificação de alagamento de maré em dia sem chuva.

## 5. Desbalanceamento e calibração

Compare ≥ 2 estratégias (`scale_pos_weight`, undersampling, focal loss). Calibre (Platt
ou isotônica) **em dado de validação separado** e mostre curva de confiabilidade antes e
depois.

## 6. Métricas obrigatórias

PR-AUC, recall a precisão fixa, Brier score e Brier skill score, curva de calibração,
matriz de confusão nos limiares escolhidos (com custo em linguagem de Defesa Civil).
Acurácia e ROC-AUC isoladas não valem com evento raro. Reporte também **por ponto** e
**por mecanismo** (chuva vs. refluxo de maré), além do global.

Baselines obrigatórios, no mesmo split: "nunca alaga", "alaga se chuva > X mm/h", modelo
do balde e rede de Petri estocástica. Modelo que não vence o baseline físico não é
resultado positivo — reporte assim.

## 7. Artefato versionado

Cada modelo salvo junto de um `metadata.json` com: hash SHA-256 do CSV de treino, lista
ordenada de features, versão do código (commit), seed, versões de bibliotecas, período de
treino/validação, limiares e métricas. Sem metadata, o artefato não é carregado em produção.
Os `.joblib`/`.h5` atuais em `models/` não têm isso — tratar como não rastreáveis.

## 8. Contrato de inferência

Contrato canônico no **ADR 0007 (aprovado)**. A IA responde `flood_probability`, `risk_level`
(`LOW|MEDIUM|HIGH`, **nunca** `UNKNOWN`), `model_version` (obrigatório; `"unversioned"` até haver
artefato com metadata), `horizon_hours`, `issued_at` (UTC, `Z`), `top_contributors`
`[{feature_id, contribution}]` (SHAP ou permutação) e `imputed_features`. Dado insuficiente = **422**
com `missing_features`, nunca 0.0. O backend converte falhas em `UNKNOWN` + `degradationReason` e
traduz `feature_id` por um catálogo fixo em português — feature nova precisa entrar no catálogo.

## 9. Reprodutibilidade

Um único comando regenera dataset → treino → métricas, com seed fixa e dependências
travadas. Eventos reais conhecidos viram teste de regressão do modelo.
