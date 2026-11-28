# Pré-registro — conferência semanal do rain_v1 (onda 16, Fase 1.1)

Escrito em 2026-11-22, antes da primeira rodada. A conferência não muda o modelo. Ela diz se a previsão de chuva servida
continua tão boa quanto no teste, e separa o antes e o depois de cada quebra de série que afeta o rain_v1.

## O que é medido

- Previsões gravadas em `rain_forecast` e conferidas pela regra do treino: chuva medida no ponto em (t, t+H], o maior
  acumulado entre as estações a até 5 km com leitura no início e no fim da janela. Previsão sem medida (`NO_MEASURE`) fica
  fora e é contada à parte; nunca vira zero.
- Erro de quantil (pinball, média de 50%, 80% e 95%) do rain_v1 e da **persistência do treino**: a faixa da chuva do
  ponto na hora antes da emissão (seco, 0–1, 1–5, ≥ 5 mm) dá q50, q80 e q95 pela tabela ajustada no treino
  (`projeto-mapi-ai/reports/v4/rain/referencia_conferencia_semanal.json`). Sem chuva medida na hora antes, a faixa é
  "sem dado" (distribuição global do treino), nunca "seco".
- Ganho = 1 − pinball(rain_v1) / pinball(persistência), com IC 95% por bootstrap de dia local (2.000 reamostras, semente
  20261122).
- Referência do teste (mesma conta, previsões fora da amostra do rain_v1): **0,165 [0,148; 0,185]** em 1 h,
  **0,148 [0,126; 0,173]** em 3 h e **0,137 [0,115; 0,160]** em 6 h.

## Quando roda e o que grava

- Toda segunda às 6h (hora de Recife), sobre a semana anterior (segunda 00h a segunda 00h). Também por
  `POST /api/admin/rain-forecast/weekly-check?semana=AAAA-MM-DD`.
- Duas linhas por prazo em `rain_forecast_check`: `SEMANA` (só a semana) e `TRECHO` (acumulado desde o início do trecho
  até o fim da semana). Os trechos são cortados em cada quebra de `source_series_break` que afeta o rain_v1. Hoje a
  única quebra é **2026-11-30** (`best_match` do Open-Meteo, ICON na grade nativa).

## Regra do status (os números a aprovar)

| Status | Quando |
|---|---|
| `POUCO_DADO` | menos de **200** previsões, ou menos de **3** dias com pelo menos **1 mm** medido no prazo. Semana seca não informa nada sobre a previsão de chuva |
| `PIORA` | o IC 95% inteiro do ganho fica **abaixo** do limite inferior do IC do teste |
| `OK` | nos outros casos |

- **Pendência:** só o `TRECHO` com `PIORA` abre pendência (alerta de sistema `MODEL_CHECK`, um por prazo e trecho a cada
  7 dias). A linha `SEMANA` é informativa: uma semana sozinha é curta e oscila muito.
- O que a pendência pede: olhar a semana, e, se o `PIORA` for só depois de 30/11, tratar a quebra do `best_match`
  (por exemplo, rodar o rain_v1 sem as entradas do Open-Meteo, como o modelo de 1 h já faz) **com um pré-registro
  próprio**. A conferência nunca retreina nem troca o modelo sozinha.

## O que fica de fora

- Evento FORTE: a conferência mede o erro de quantil, não o aviso de chuva forte (raro demais para uma semana).
- As 22 estações só ao vivo (Fase 7.2) entram como estão hoje no serviço.
