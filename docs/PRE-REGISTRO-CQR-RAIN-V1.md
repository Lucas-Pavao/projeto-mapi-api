# Pré-registro — intervalo conforme (CQR) do rain_v1 (onda 16, Fase 9.3)

- Data: 2026-11-28 · Status: **fixado antes de rodar** (aval geral do Lucas de 22/11 para os pré-registros da onda).
- Tarefa: O16-F9.

## 1. Pergunta

O q95 do rain_v1 cobre 96,4% a 97,1% das horas do teste (fonte: `projeto-mapi-ai/reports/v4/rain/evaluation.json`), mas a
garantia é **na média**. Quanto ele cobre quando chove forte? E o ajuste conforme (CQR) muda isso?

## 2. Método

Conformalized Quantile Regression (Romano, Patterson e Candès, 2019, "Conformalized Quantile Regression", NeurIPS;
https://arxiv.org/abs/1905.03222, acesso em 2026-11-28), na forma de um lado só: a chuva não é negativa, então o intervalo é
[0, limite de cima].

- Escore de cada linha de calibração: E = y − q (y = chuva medida em (t, t + h]; q = quantil previsto).
- Q̂ = o ⌈(n + 1)(1 − α)⌉-ésimo menor E.
- Limite ajustado = max(0, q + Q̂).
- Feito para o q95 (α = 0,05) e o q80 (α = 0,20), por prazo (1, 3 e 6 h), com o modelo servido em cada prazo
  (`reports/v4/rain/selection.json`).

## 3. Dados

- **Calibração:** a parte de validação do rain_v1 (2024-07-03 14h10 a 2025-07-30 18h, UTC). Ela não entrou no ajuste dos
  modelos; serviu só para escolher entre eles.
- **Teste:** a parte de teste (2025-07-31 a 2026-10-31), lida uma vez.
- Previsões já gravadas em `projeto-mapi-ai/data/rain/predictions.pkl.gz`; nada é retreinado.

## 4. O que se relata (fixado agora)

Cobertura do q95 e do q80, original e ajustado, no teste, com IC 95% por bootstrap de bloco de dia (1.000 reamostras), em:

1. todas as linhas;
2. "já chove forte no ponto": chuva medida nos últimos 60 min ≥ 25,15 mm (limiar FORTE do ADR 0017), observável na hora
   da previsão;
3. "já chove no ponto": chuva medida nos últimos 60 min ≥ 5,03 mm (limiar MÉDIA);
4. diagnóstico, pelo resultado: linhas em que a chuva medida em (t, t + h] chegou a 25,15 mm. Esta é condicionada no que
   aconteceu depois e serve só para mostrar o tamanho do problema; a cobertura ali é baixa por construção.

Também: largura média (mm) do limite de cima, original e ajustado, nos mesmos recortes.

## 5. O que muda no sistema

Nada nesta onda. O q95 servido continua o original, porque o modelo fica congelado para a validação de 2027 e a cobertura
média já passa de 95%. Se o recorte 2 ficar muito abaixo de 95%, isso entra no pré-registro da validação como limitação
declarada e como proposta para depois dela (CQR condicionado ao regime, por exemplo Mondrian por "já chove forte").

## 6. Limitações conhecidas antes de rodar

- As linhas de 10 min dos 5 pontos são muito dependentes entre si; a garantia de amostra finita do CQR supõe linhas
  trocáveis. A cobertura média continua válida como estimativa, mas o IC usa bloco de dia.
- Calibração (2024–2025) e teste (2025–2026) são períodos diferentes; se a chuva mudar de um para o outro, a garantia vale
  menos.
