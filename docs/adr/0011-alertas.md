# 0011 — Alertas: regras, avaliação e canais

- Status: **aprovado** (2026-10-21, Lucas, pergunta 32)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F0-REDESIGN-02

## Contexto

A tela "Central de alertas" do canvas lista transições de risco por ponto, cotas de rio (pré-alerta, alerta, inundação), maré acima de um limiar, chuva forte por pluviômetro e pluviômetro sem resposta. O backend não guarda nada disso hoje.

As previsões são feitas pelo ciclo de coleta: `SensorCollectionTask` chama `PredictionCycleRunner.triggerAfterCycle(source)` ao fim de cada ciclo (`SensorCollectionTask.java:105-106`). As cotas de rio já existem (`V2__add_river_alert_levels.sql`).

Restrições: a previsão é experimental (`modelValidationStatus = NOT_VALIDATED`, ADR 0007 emenda 1); `UNKNOWN` nunca é risco baixo; os limiares MEDIUM/HIGH ainda dependem da Defesa Civil (bloqueio F4-PED-01).

## Opções consideradas

### A — Calcular alertas no front a partir do SSE
A favor: sem tabela. Contra: sem histórico, sem reconhecimento compartilhado, cada aba calcula diferente.

### B — Regras no banco, avaliação no fim do ciclo de previsão, alerta persistido
A favor: histórico auditável, `ack` compartilhado, um único avaliador testável. Contra: duas tabelas novas e um serviço.

### C — Motor de regras externo (ex.: Prometheus Alertmanager)
A favor: pronto. Contra: mistura alerta operacional de alagamento com alerta de infraestrutura; regras fora do domínio.

## Decisão

Opção B.

- `alert_rule`: `id`, `kind` (`RISK_LEVEL`, `RIVER_STAGE`, `TIDE_ABOVE`, `RAIN_RATE`, `SENSOR_STALE`, `PREDICTION_UNAVAILABLE`), `flood_point_id` ou `station_id` (nulos = todos), `threshold` (numérico, conforme o tipo), `min_risk` (`MEDIUM`/`HIGH`), `enabled`, `channels` (`BROWSER` por enquanto), `created_by`, `created_at`, `updated_at`.
- `alert`: `id`, `rule_id`, `kind`, `severity` (`INFO`/`WARNING`/`CRITICAL` — severidade do alerta, distinta da classe de risco), `flood_point_id`/`station_id`, `title`, `detail`, `observed_value`, `prediction_id` (quando vier de previsão), `model_validation_status`, `triggered_at`, `acknowledged_at`, `acknowledged_by`.
- Avaliação: `AlertEvaluator` roda depois de `triggerAfterCycle`, compara o estado atual com o anterior e só cria alerta na **transição** (entrada em nível), nunca a cada ciclo. Supressão: mesma regra e mesmo alvo não dispara de novo em 30 min.
- `UNKNOWN` gera alerta do tipo `PREDICTION_UNAVAILABLE` com `degradationReason`; nunca é tratado como queda de risco.
- Todo alerta vindo de previsão leva `model_validation_status`; o front mostra `ExperimentalBadge`.
- Canais: `BROWSER` via SSE (ADR 0012). E-mail e WhatsApp entram só como interface `AlertChannel` sem implementação.
- Padrões iniciais (V18, regras desativáveis): risco HIGH em qualquer ponto; rio na cota de alerta; pluviômetro sem resposta por 30 min; chuva ≥ 55 mm em 1 h por pluviômetro (`RAIN_RATE`, limiar pré-registrado da IDF TR 2 anos, `docs/PARAMETROS-MODELO-FISICO.md:47`; pergunta 36). Maré fica sem regra padrão: o limiar precisa de fonte (ADR 0009 e MEMO §5 vetam limiar escolhido olhando eventos).

## Consequências

- Melhor: histórico auditável; um avaliador único com teste unitário.
- Pior: duas tabelas; a regra de risco herda a incerteza dos limiares 0,5/0,8 até a resposta da Defesa Civil.
- Muda: V18; `PredictionCycleRunner` passa a publicar um evento interno de fim de ciclo.
- Medida: teste unitário de transição (sem alerta repetido em 2 ciclos iguais); IT de `ack`.
- `flood_predictions` não entra na dissertação (decisão de 2026-10-10); `alert` também não.

## Condições que reabririam a decisão

- Defesa Civil exigir integração com sistema próprio de alerta.
- Volume de alertas > 100 por dia por operador (fadiga), medido na tabela `alert`.

## Emenda 1 — estado por alvo e rastreio (2026-10-21, F2)

- `alert_state (rule_id, target_key, in_condition, updated_at)` entra na V18. Guarda se cada par (regra, alvo) estava em condição no ciclo anterior. Em memória, um reinício reemitiria todo alerta ainda ativo; no banco, não. Um par sem linha conta como "fora de condição".
- `UNKNOWN` não altera o estado das regras `RISK_LEVEL` (não é queda de risco); só a regra `PREDICTION_UNAVAILABLE` o avalia.
- `alert.prediction_id` fica nulo por enquanto: o resumo de previsão usado pelo avaliador não carrega o id da linha de `flood_predictions`.
- `RAIN_RATE` (decisão 45, 2026-10-21) lê a soma horária de `observation.precipitation` (variable_catalog: PT1H, SUM), da grade `observation_hourly_v` sobre `observation_current` (só ACTIVE; hora com completude abaixo do mínimo tem valor nulo e não entra). Vale a última hora fechada de cada estação, com fim nas últimas 3 h. O mesmo vale para `/api/public/risk` e para a chuva da linha do tempo, que serve a série horária como vem (um ponto por hora, `t` = fim da hora). O código da estação na observação casa com o `sensorId` do legado sem o prefixo (`APAC-PLUVIO-`, `APAC-METEO-`, `APAC-RIO-`, `ANA-`).
- Escalada (decisão 46, 2026-10-21): dentro da condição, subir de severidade (ex.: MEDIUM para HIGH numa regra com `min_risk = MEDIUM`) gera alerta novo com a severidade maior, mesmo dentro dos 30 min. A supressão vale para o mesmo nível: um alerta recente de severidade igual ou maior segura o novo. Para isso a V20 acrescenta `alert_state.severity`.
