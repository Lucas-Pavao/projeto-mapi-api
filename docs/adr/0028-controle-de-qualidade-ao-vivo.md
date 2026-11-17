# 0028 — Controle de qualidade ao vivo (emenda ao ADR 0022)

- Status: proposto (implementado)
- Data: 2026-11-17
- Decisores: Claude (autor), Lucas
- Tarefa: ALERTA-E2, item 2.4

## Contexto

A checagem mensal de estação e a retirada de pico isolado (ADRs 0021 e 0022) precisam do mês inteiro e não valiam
para o dado ao vivo. Num alerta, um pluviômetro com defeito vira alarme falso.

## Opções consideradas

### A — Filtro também nas entradas do rain_v1

Avaliado nos 5 anos da simulação do dado como chega (`projeto-mapi-ai/reports/rain_v1b/controle_qualidade_ao_vivo.json`):
validação, falsos 82 → 66 e nenhum evento perdido; teste, falsos 87 → 73, **1 evento perdido e antecedência 16 → 6 min**
(IC 0 a 16). A checagem de 30 dias reprova 8,5% dos estação-dias e tira 10% das leituras; o modelo perde estações que
usava no treino.

### B — Filtro só na série medida do alerta

O nível ALERTA (chuva medida acima da galeria) e o balde usam a série filtrada; a previsão recebe as leituras como no
treino.

## Decisão

Opção B.

- Pico isolado: mesma regra do ADR 0022 (10 mm ou mais na hora com pelo menos 2 vizinhas a até 6 km abaixo de 1 mm),
  aplicada no instante, no compositor da série de 10 min.
- Estação suspeita: critérios do `StationMonthChecker` (cobertura parcial, travada em zero, subcaptação, excesso), com
  os mesmos limiares, numa janela móvel de 30 dias terminando em t, recalculada no máximo uma vez por hora do relógio do
  ciclo. Hora vinda da coleta ao vivo tem cobertura desconhecida (bloco sem duração) e não reprova por cobertura.
- Leitura retirada fica em `live_qc_drop` com o motivo (`PICO_ISOLADO`, `QC_<motivos>`). Nada é apagado.

## Consequências

- Alarme falso por pluviômetro com defeito no nível ALERTA e no balde fica bloqueado; o ATENCAO não muda.
- Primeira rodada em produção (2026-11-17): 37 estações reprovadas, quase todas por cobertura parcial, causada pela
  coleta ao vivo sem duração de bloco; corrigido no mesmo dia.

## Condições que reabririam a decisão

- Retreino do rain_v1 com o filtro aplicado também no treino (o modelo aprenderia com as mesmas ausências).
