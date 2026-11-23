# Pré-registro — índice caixa branca (onda 16, Fase 5; ADR 0031)

- Data: 2026-11-23 · Status: aprovado pelo aval geral do Lucas; aval do orientador pendente · escrito antes de montar o dataset

## 1. Unidade, período e rótulo

- Unidade: ponto-dia local (America/Recife), nos 5 pontos com a posição da Fase 3.
- Período: 01/09/2021 (início da série de 10 min) a 25/06/2024 (último dia da lista da Defesa Civil).
- Só dias com a série de 10 min do ponto completa (144 passos sem lacuna).
- Positivo: registro da Defesa Civil a até 2 km do ponto no dia (a mesma regra da calibração e da validação de 20/11).
- Negativo: todo outro ponto-dia calculável do período. Limitação: "sem registro" não é "sem alagamento".

## 2. Entradas (3, todas medidas ou previstas sem rótulo)

| Entrada | Definição |
|---|---|
| `chuva_6h_mm` | maior chuva medida em 6 h móveis no dia, na série do ponto (10 min) |
| `max_30min_mm` | maior chuva medida em 30 min móveis no dia |
| `mare_nr_m` | maré prevista (tábua DHN, cosseno a cada 10 min) no passo do fim da janela de 6 h de maior chuva |

A chance de entupimento (exemplo do prompt) **não entra**: no regime permanente ela é um número fixo por ponto, então
funcionaria como identificador do ponto (e a densidade de registro da Defesa Civil muda de bairro para bairro); CIn e Jaboatão
não têm valor. "Maré menos soleira" também não: CIn e Jaboatão não têm soleira; a maré do porto vale para todos.

## 3. Modelos (mesmos dados, mesma validação)

1. Regra "chuva medida em 6 h ≥ 25 mm" (linha de base que já existe).
2. Regressão logística com penalidade L2, C = 1,0, entradas padronizadas (média e desvio do treino de cada dobra), pesos de
   classe balanceados. Sem busca de hiperparâmetro.
3. EBM (`interpret-core` 0.7.3): `interactions=1`, `outer_bags=8`, semente 42, demais parâmetros padrão.

## 4. Validação e métricas

- Validação cruzada agrupada por dia local: 5 dobras (`GroupKFold`), o mesmo dia nunca está no treino e no teste.
- Fora da dobra: AUC, PR-AUC, Brier e diagrama de confiabilidade (10 faixas de probabilidade prevista).
- Ponto de operação para comparar com a regra: o limiar que marca o **mesmo número de ponto-dias** que a regra de 25 mm marca;
  aí, POD e falsos por ponto-mês.
- Intervalos: bootstrap por dia (1.000, semente 42).
- Interpretação: coeficientes da logística ajustada no conjunto todo, em razão de chances por 10 mm (chuva) e por 0,5 m (maré),
  com IC por bootstrap; curvas de forma do EBM ajustado no conjunto todo.

## 5. O que não se faz

Sem trocar entrada, regularização ou modelo depois de ver as métricas. Nada vai para a tela como nível.

## 6. Saídas

`projeto-mapi-ai/reports/indice/RELATORIO.md`, `indice.json` e figuras (`curvas_ebm.png`, `confiabilidade.png`).
