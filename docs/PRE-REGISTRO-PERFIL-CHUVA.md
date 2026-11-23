# Pré-registro — perfil da chuva prevista no tempo (onda 16, Fase 4.2)

- Data: 2026-11-23 · Status: **aprovado** (ponto de parada E coberto pelo aval geral do Lucas de 22/11) · escrito antes de rodar
- Pergunta: como distribuir no horizonte a chuva que o rain_v1 prevê como acumulado em (t, t+h], h = 1, 3 e 6 h?
- Só chuva. Nenhum rótulo de alagamento entra aqui.

## 1. Problema

Hoje o acumulado previsto é espalhado por igual nos passos de 10 min (`mapi_ai/flood/config.py`, `DISTRIBUTION`). Isso apaga o
pico: 30 mm em 3 h viram 1,7 mm a cada 10 min, e o balde, que reage ao pico, não enche.

## 2. Alvo medido

Pico de 10 min do ponto em (t, t+h]: o maior valor, entre os passos de 10 min do horizonte, da chuva do ponto (maior valor
entre as estações válidas a até 5 km no passo; ADR 0022), com a mesma regra de validade do alvo `y_h` do rain_v1 (estação
válida no início e no fim). Nome: `peak10_{h}h`. NaN quando não há estação válida.

Avaliação: **só no teste** do rain_v1 (depois de 2025-07-31, com a guarda de 6 h), **só nas linhas com chuva medida no
horizonte** (`y_h` ≥ 1 mm), por horizonte. Mesmo conjunto de pontos do rain_v1.

## 3. Opções

| Id | Opção | Pico de 10 min previsto | Série que vai para o balde |
|---|---|---|---|
| u | Uniforme (hoje; referência) | acumulado q50 / n | acumulado / n em cada passo |
| a | **Prever o pico**: regressor de quantis novo no rain_v1 (XGBoost, mesmas entradas, mesmos cortes de treino, validação e teste, mesma parada antecipada, um instante a cada 30 min no treino), alvo `peak10_{h}h`, quantis 0,5 e 0,95 | q50 do regressor do pico, limitado ao acumulado q50 | pico no passo do meio do horizonte; o resto do acumulado por igual nos outros passos |
| b | **Perfil de projeto**: bloco alternado da IDF de Silva e Araújo (2013, Eq. 16), blocos de 10 min | fração do maior bloco × acumulado q50 | blocos da IDF normalizados para somar 1, escalados pelo acumulado, maior no meio e os outros alternando dos dois lados |

Bloco alternado (b): para d = 10, 20, …, 60·h min, P(d) = i(TR, d) · d / 60 com a IDF; bloco k = P(10k) − P(10(k−1)). Na Eq.
16 o fator do TR multiplica todos os blocos e some na normalização: o perfil não depende do TR. Curvas de Huff não entram: não
achei curvas do Recife com fonte.

A coluna `rain_max_10min_mm` do banco é nula em parte das horas com chuva (prompt): o alvo `peak10` é recalculado a partir das
leituras de 10 min (`data/rain/ped_10min.csv.gz`), não daquela coluna.

## 4. Critério (fixado agora)

1. Métrica: erro absoluto médio (MAE, mm) do pico de 10 min previsto (o q50 de cada opção) contra o medido, por horizonte.
2. Escolha: a opção com o **menor MAE médio nos 3 horizontes** entre (a) e (b).
3. Empate: se a diferença entre a melhor e a outra tiver IC95 (bootstrap por dia local, 1.000 reamostragens, semente 42) que
   inclui zero, fica a **(b)**, que não precisa de modelo novo.
4. A uniforme entra só como referência ("quanto o pico melhora").
5. Também relatado, sem decidir nada: cobertura do q95 do pico na opção (a) e o viés médio de cada opção.

## 5. Depois da escolha

A opção escolhida passa a construir a série do balde no `/v1/flood/forecast` (`config.DISTRIBUTION` muda de texto e de versão)
e na reconstrução de 5 anos (Fase 4.4). O q95 do cenário pessimista usa a mesma forma: em (a), o pico q95 do regressor do pico,
limitado ao acumulado q95; em (b), o mesmo perfil escalado pelo acumulado q95.

## 6. Saídas

`projeto-mapi-ai/reports/perfil_chuva/RELATORIO.md` e `perfil_chuva.json`; se (a) ganhar, os regressores em `models/v4/rain/`
com versão nova (`rain_v1p`), sem mexer nos do acumulado.
