# F2-BACK-13 — Rótulo do export vem do pipeline novo

- Branch: `feat/export-rotulo-pipeline` (de `integracao/fase-2`). Sem push. Nenhuma migração; `docker-compose.yml` intocado; nenhum endpoint removido.
- Decisão do autor (pergunta 25, 2026-10-10): raio principal 500 m e G = H (6 h para `label_h6`, 3 h para `label_h3`).

## O que mudou

- `OccurrenceLabeler` (puro, `service/export/label`): regras de MODELAGEM-IA A.1.4 e A.3.1. `t` = fim da hora, UTC.
  - `label_h<H>` = 1 se há ocorrência HOUR casada com o ponto em (t, t+H]; 0 se não; **vazio** se excluída.
  - Exclusão `DAY_EVENT_SAME_DAY`: a janela (t, t+H] toca o intervalo de uma ocorrência DAY do ponto (nunca vira 0). Exclusão `POST_EVENT_WINDOW`: ocorrência em (t−G, t] (para DAY, intervalo que toca a janela). DAY tem precedência.
  - `label_day` = 1 se há ocorrência HOUR ou DAY no dia civil America/Recife do instante `t` (hora 03:00Z já é o dia local seguinte). Nunca excluído.
  - `label_excluded_reason` refere-se ao maior horizonte (nos demais, a exclusão é subconjunto). `occurrence_ids` = ids de `flood_occurrence` que justificam, separados por `;`.
- CSV: colunas novas ao fim das `_missing` e antes das `terrain_*`; colunas existentes inalteradas. `isFlooded`/`severity` (de `flood_events`) mantidas e listadas em `deprecatedColumns`.
- Propriedades (`mapi.export.label.*`, `ExportLabelProperties`): `horizons-hours` (6, 3), `post-event-window-hours` (vazio = G = H), `linkage-radius-m` (500), `linkage-config-id` (opcional; sobrepõe o raio). `mapi.ingestion.occurrence-radius-m` também passou de 3000 para 500.
- linkage_config vigente: o de id configurado ou o de menor id com raio 500 e `kind = OCCURRENCE_POINT`. A V6 só traz o de 3 km; o config de 500 m é criado pelo pipeline (`rematch(500)` / ingestão CKAN), sem apagar o antigo. **Se ele ainda não existe, o export sai com `label_*` vazios e `linkageConfigId` nulo no manifesto**: é preciso rodar a ingestão/rematch antes de exportar.
- Manifesto: `deprecatedColumns` e `label` com política, horizontes, G por horizonte, `linkageConfigId`/`linkageRadiusM`/`linkageMethod`, contagens (positivos, negativos, excluídos) por `label_h<H>`, excluídas por motivo, positivos de `label_day` e ocorrências HOUR × DAY casadas que tocam o período.

## Testes (Testcontainers)

`ExportLabelIntegrationTest` (5): HOUR em (t, t+6h] rotula 1 e fora 0; dia DAY exclui horas com motivo e nunca produz 1/negativo; janela pós-evento; ocorrência de outro `linkage_config` (outro raio) não rotula e trocar o id muda o manifesto; ingestão real com geocodificação (300 m casa, 1 km não casa em 500 m); cabeçalho sem rótulo em coluna de feature (as `terrain_*` vêm depois); manifesto com contagens (6 positivos, 42 excluídos em h6, 30 DAY + 12 pós-evento, 1 HOUR e 1 DAY). `OccurrenceLabelerTest` (3, unidade). `DataExportIntegrationTest` com o novo cabeçalho. `OccurrenceIngestionIntegrationTest` fixou o raio em 3000 por propriedade, porque suas fixtures estão a ~1 km.

## Medidas

| | Base `integracao/fase-2` | Depois |
|---|---|---|
| Testes | 151 | 159 (0 falhas) |
| Linhas (JaCoCo 0.8.12 CLI) | 59,0% (informado) | 57,3% (2826/4932), medido com `surefire:test` + `jacoco:report`; a diferença de método não foi reconciliada |

## Pendências e riscos

1. Eventos (agrupamento por G, A.3.2, `eventId`) não são exportados; só contagem de horas. Para `GroupKFold` a IA precisa do `eventId`.
2. Perto do fim do período, ocorrências futuras ainda não registradas dão rótulo 0 (alvo olha para frente); o corte do treino deve deixar folga de H.
3. Viés de notificação (A.3.5) e geocodificação por centroide (A.3.1 item 3) não tratados aqui; `geocode_method` não entra no CSV.
4. `label_excluded_reason` único por linha; se G for configurado diferente de H, a razão por horizonte pode divergir.
5. O leitor da IA ainda não consome `label_*`; nenhuma mudança em `projeto-mapi-ai`.
