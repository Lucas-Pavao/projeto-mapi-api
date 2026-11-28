# 0031 — Índice de risco caixa branca com rótulo real, só para comparação

- Status: aprovado pelo Lucas (aval geral de 22/11 para todas as fases da onda 16); **aval do orientador pendente** (o prompt
  pede esse aval antes de treinar; o treino foi feito e fica marcado como "sem aval do orientador" até ele confirmar ou vetar)
- Data: 2026-11-23
- Decisores: Claude (autor), Lucas; orientador (pendente)
- Tarefa: O16-F5 (onda 16, Fase 5)

## Contexto

Em 20/11 o logreg_v3 e o xgb_v2 saíram porque o rótulo era sintético (ADR 0007, emendas). Desde então nenhum modelo do MAPi é
treinado com rótulo de alagamento. O prompt da onda 16 pede um modelo que se explique sozinho, ao lado do balde, usando os
**rótulos reais**: os ponto-dias da Defesa Civil (18 dias, 225 registros, 2021–2024; `projeto-mapi-ai/data/in1165/flood_occurrences.csv.gz`).
Com 41 positivos, qualquer modelo com muitas entradas decora.

## Opções consideradas

### A — Não treinar com rótulo real

A favor: mantém a decisão de 20/11. Contra: a dissertação fica sem a comparação "física × estatística simples" com o mesmo
rótulo, e o prompt pede.

### B — Índice caixa branca, poucas entradas, só comparação

Regressão logística regularizada e EBM com no máximo 4 entradas físicas, validação cruzada agrupada por dia, sem ir para a
tela. A favor: coeficientes e curvas legíveis ("cada 10 mm a mais em 6 h multiplica a chance por X"); mede quanto um modelo
estatístico simples separa os dias com registro. Contra: 41 positivos dão intervalos largos; o rótulo é "registro da Defesa
Civil a até 2 km", não lâmina medida.

### C — Modelo flexível (XGBoost) com rótulo real

Contra: com 41 positivos é sobreajuste certo, e não se explica sozinho.

## Decisão

Opção B, com o pré-registro `docs/PRE-REGISTRO-INDICE-CAIXA-BRANCA.md`. Regras:

- Rótulo real só da Defesa Civil (calibração na regra 1 do prompt). Os mesmos 18 dias já foram olhados; por isso o índice
  **não tem validação externa** nesta onda: a validação dele é a de 2027 (Fase 9), com os mesmos critérios do balde.
- No máximo 4 entradas, regularização fixada antes (sem busca de hiperparâmetro), no máximo uma interação em par no EBM.
- Nenhum índice vira o nível da tela nesta onda. Os resultados entram na página Modelo (Detalhes) e na dissertação como
  comparação.
- Artefatos marcados `training_data = REAL_LABEL_DEFESA_CIVIL`, `on_screen = false`, `orientador = pendente`.

## Consequências

- Melhor: a dissertação ganha a comparação com rótulo real e uma explicação em razão de chances.
- Pior: reabre o uso de rótulo de alagamento em treino, com amostra pequena. Mitigação: poucas entradas, validação agrupada,
  intervalos por bootstrap, nada na tela.
- Medir: AUC, PR-AUC, Brier e calibração fora da dobra; razão de chances com IC; e, na Fase 9, POD/FAR contra os rótulos
  lacrados de 2027.

## Condições que reabririam a decisão

- O orientador vetar o uso do rótulo real em treino.
- Rótulo com hora e lâmina (pedidos 05 e 07, armazenamento lacrado) em quantidade suficiente para treinar e validar de novo.
