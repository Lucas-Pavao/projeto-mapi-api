# 0025 — Rótulo híbrido estratificado por precisão espacial e proveniência da chuva do ponto

- Status: proposto (infraestrutura configurável implementada em `feat/rotulo-estratificado`, desligada por padrão; aguardando validação quando chegarem dados com hora e endereço)
- Data: 2026-11-16
- Decisores: Lucas (especificação), agente de implementação
- Tarefa: P16-ROTULO-B

## Contexto

As 225 ocorrências atualmente em `flood_occurrence` (`mapi_db` em 2026-11-16) vêm exclusivamente de `CKAN_RECIFE`, todas com `temporal_precision = DAY` (sem hora), endereços anonimizados sem número (`172 STREET`, `53 NEIGHBORHOOD`), distância mínima a algum ponto crítico de **619 m** e **0** ocorrências dentro de 250 m, 400 m ou 600 m.

Para preparar o sistema para a chegada de dados de melhor qualidade (pedido por LAI à Defesa Civil e relatos validados `FloodReport`), foi especificada a **Opção B (Híbrida Estratificada)**, mantendo o export atual (500 m, pergunta 25) intacto até aprovação na validação.

## Especificação registrada (limiares do Lucas — a verificar na validação)

### 1. Matriz de vínculo espacial e confiança (`mapi.export.label.*`, `stratified.enabled=false` por padrão)

| Estrato | Critério espacial | Raio máximo | Peso (`sample_weight`) | Tier | Uso |
|---|---|---|---|---|---|
| **Ouro (Estrito)** | Coordenada de origem (`SOURCE_COORDINATE`), `GeocodeLevel.ADDRESS` (com `house_number`) ou `FloodReport` validado | 250 m | 1,00 | `HIGH` | Treino + teste oficial primário |
| **Prata (Rua)** | `GeocodeLevel.STREET` | 400 m | 0,70 | `MEDIUM` | Treino ponderado + sensibilidade |
| **Bronze (Cluster)** | `GeocodeLevel.NEIGHBORHOOD` com `OccurrenceCluster` de `>= 3` ocorrências | 600 m | 0,40 | `LOW` | Apenas exploratório (fora do treino/teste principal) |
| **Descarte** | `NEIGHBORHOOD` isolado, `CITY`, `NONE` ou fora do raio do estrato | — | 0,00 | `DISCARD` | Excluído |

- **Janela temporal em torno do pico do `RainEventDetector` (ADR 0017, separação 6 h):** `[-2h, +6h]` ao redor do pico do evento de chuva para ocorrências com precisão `HOUR`/`MINUTE`.
- **Ocorrência só com `DAY` (sem hora):** nunca é casada pela janela do pico; recebe marcação explícita `TEMPORAL_DAY_ONLY` e segue a regra existente de `DAY` (`OccurrenceLabeler.DAY_EVENT_SAME_DAY` exclui `(t, t+H]`, `label_day = 1` no dia civil local).

### 2. Proveniência estratificada da chuva do ponto (`mapi.analise.point-rain.*`, ADR 0022)

Sem alterar o cálculo do valor da série (`PointRainComposer` continua escolhendo o maior valor entre as estações válidas até 5 km):

1. `MEASURED_PRIMARY`: estação escolhida a até `3,0 km` (`confidence = 1.0`).
2. `MEASURED_EXTENDED`: estação escolhida entre `3,0 km` e `5,0 km` (`confidence = 0.85`, flag `DISTANT_STATION`).
3. `REANALYSIS_FALLBACK`: propriedade `allow-reanalysis-fallback` com **padrão `false`** (`max-gap-hours-before-fallback = 2`). Se ligada explicitamente em experimento, as linhas recebem flag `SYNTHETIC_RAIN_FALLBACK`, confiança `0.60`, e **nunca** entram na métrica estrita. Motivo do padrão `false`: treinar com reanálise e servir com chuva medida produziu a divergência apontada no relatório de 16/11 (`RELATORIO-GERAL-2026-11-16.md`).

## Estado atual nos dados reais (`mapi_db`, 2026-11-16)

- **Ocorrências por estrato (Ouro / Prata / Bronze):** **0 / 0 / 0** (todas as 225 estão a ≥ 619 m e todas são `TEMPORAL_DAY_ONLY`). Nenhum raio foi afrouxado para fabricar positivos.
- **Horas-ponto em `point_rain_hourly` (899 010 linhas):** `MEASURED_PRIMARY` = **555 495**, `MEASURED_EXTENDED` = **327 600** (inclui `CIN_UFPE`, com 0 primárias e 179 088 estendidas), lacunas (`GAP`) = **15 915**.

## Consequências

- **Melhor**: infraestrutura pronta, configurável e auditável para novos dados com hora e endereço; proveniência de distância (3 km vs 5 km) visível por linha.
- **Pior**: com os dados de 2026-11-16, o pipeline estratificado produz zero rótulos positivos; a avaliação em `avaliacao/opcao_b/` reporta explicitamente estratos vazios sem quebrar.

## Condições que reabririam a decisão

- Chegada de ocorrências com hora e endereço/coordenada (Defesa Civil via LAI ou `FloodReport` validado), permitindo verificar na validação se 250 m / 400 m / 600 m e os pesos 1,0 / 0,7 / 0,4 melhoram a calibração e a generalização.
