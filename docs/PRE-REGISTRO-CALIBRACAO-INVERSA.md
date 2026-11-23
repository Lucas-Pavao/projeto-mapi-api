# Pré-registro — calibração inversa da capacidade (onda 16, Fase 4.3)

- Data: 2026-11-23 · Status: **aprovado** pelo aval geral do Lucas de 22/11 (o prompt pede também o aval do orientador: fica
  registrado como **pendente**; o resultado só passa a valer no alerta se o orientador não vetar até o congelamento da Fase 9)
- Escrito antes de rodar. Usa **só** os dados de calibração da regra 1 do prompt.

## 1. Dados

- **Positivos (calibração, nunca validação):** os 18 dias da Defesa Civil (`projeto-mapi-ai/data/in1165/flood_occurrences.csv.gz`,
  225 registros, 13/05/2021 a 25/06/2024, precisão de dia). Unidade = ponto-dia local; positivo = registro a até 2 km do ponto
  naquele dia (a mesma regra da validação de 20/11, `mapi_ai/flood/validate.py`, `RADIUS_KM = 2.0`). As posições são as da
  Fase 3. 2023-02-06 entra (é dia da Defesa Civil; sai só da validação de Ibura, decisão do ponto A).
- **Controles negativos, por regra escrita:** todo dia local de 01/09/2021 (início da série de 10 min) a 25/06/2024 com chuva
  medida no ponto, no dia, **de 15 a 40 mm**, sem lacuna na série do dia, e **sem** registro da Defesa Civil a até 2 km. Nenhum
  dia é escolhido a dedo.
- **Limitação declarada:** a lista da Defesa Civil tem 18 dias em 3 anos. Dia sem registro não prova que não alagou. Os
  negativos são "sem registro", não "seco na rua".
- Fora: datas de notícia do alinhamento (06/02/2023, 15/05/2025, 04/08/2026) só entram se forem dia da Defesa Civil (só
  2023-02-06 é); eventos de 2026 da imprensa não entram; armazenamento lacrado não é aberto.

## 2. Modelo observado

Balde a 10 min sobre a chuva **medida** do ponto (a série de `data/bucket10/serie10min_<ponto>.csv.gz`, por trecho contínuo),
com o terreno da Fase 3, o refluxo no modo do ponto (Mascarenhas `NIVEL`; Ibura `INFO`, isto é, sem refluxo na conta) e a
limpeza fora (`INFO`). Dia previsto positivo = lâmina máxima do dia local ≥ 15 cm.

## 3. Parâmetro (um por ponto)

κ ∈ [0, 1], posição na faixa da capacidade efetiva em escala log: Q(κ) = Q_min^(1−κ) · Q_max^κ
(`docs/PRE-REGISTRO-CAPACIDADE-EFETIVA.md`). Grade de 0 a 1 com passo 0,05.

## 4. Ajuste

- Objetivo: **acurácia balanceada** = (sensibilidade + especificidade) / 2 nos dias do ponto (positivos e negativos de controle).
- Empate: o κ mais perto de 0,5.
- Incerteza: bootstrap por dia (1.000 reamostragens, semente 42), estratificado em positivos e negativos; IC95 de κ̂ pelos
  percentis 2,5 e 97,5.
- Relatado também: acurácia balanceada em κ = 0, 0,5 e 1, e o número de positivos e negativos por ponto.

## 5. Quando o κ̂ é adotado (fixado agora)

Num ponto, κ̂ substitui a faixa no alerta **só se**: (i) o ponto tem pelo menos 3 ponto-dias positivos calculáveis; (ii) o IC95
de κ̂ tem largura ≤ 0,5; (iii) a acurácia balanceada em κ̂ passa a de κ = 0,5 por pelo menos 0,05. Senão o ponto fica com a
regra da faixa (ALERTA com Q_max, ATENÇÃO com Q_min). Com 41 ponto-dias no total, o esperado é que poucos pontos passem: o
resultado é relatado do mesmo jeito.

## 6. O que não muda

Nenhum outro parâmetro é ajustado (C, IDF, h_crit, perfil da chuva, refluxo, limpeza). A frequência de alertas de 5 anos
(Fase 4.4) é relatada depois, sem comparar com dia de alagamento.

## 7. Saídas

`projeto-mapi-ai/reports/calibracao/RELATORIO.md` e `calibracao.json`.
