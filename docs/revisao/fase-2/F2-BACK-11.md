# F2-BACK-11 — Migração V8: colunas de auditoria em `flood_predictions`

Branch `feat/flood-predictions-v8`, a partir de `integracao/fase-2`. Sem push, banco real (mapi-db) intocado, `docker-compose.yml` intocado, nenhum endpoint removido. Quita as pendências 2 e 3 do F2-BACK-08 (consulta por latitude/longitude e auditoria em JSON na `message`).

## Migração `V8__flood_predictions_audit_columns.sql`

Aditiva: nenhum tipo muda, nenhuma coluna é removida.

| Coluna | Tipo | Observação |
|---|---|---|
| `model_version` | text | parte semântica da versão |
| `model_version_full` | text | com hash git |
| `horizon_hours` | integer | |
| `degradation_reason` | text | CHECK nos 4 valores de `DegradationReason`; NULL aceito |
| `imputed_features` | jsonb | default `'[]'` |
| `top_contributors` | jsonb | **acréscimo fora do pedido**: sem ela os contribuintes, que estavam no JSON da `message`, se perderiam |
| `flood_point_id` | bigint | FK para `flood_points(id)`; NULL em linhas antigas e no POST de cenário |
| `issued_at` | timestamptz | NULL nas linhas antigas |

- **`issued_at` nas linhas antigas fica NULL**, de propósito: `timestamp` é `TIMESTAMP` sem fuso e o fuso em que foi escrito é incerto (`LocalDateTime.now()` da JVM). Converter inventaria um instante. A leitura usa `issued_at` quando existe e, senão, `timestamp` no fuso da JVM (comportamento anterior).
- **FK em hypertable: suportada** (TimescaleDB 2.30.2, imagem `timescale/timescaledb-ha:pg16.15-ts2.30.2`). A restrição do Timescale é para FK *apontando para* hypertable; aqui a hypertable aponta para tabela comum. Verificado no teste: a FK existe na tabela-mãe (propaga para os chunks) e rejeita `flood_point_id = 999999`. Não foi preciso trocar por validação na aplicação.
- **Índice** `idx_prediction_point_timestamp (flood_point_id, timestamp DESC)`, com a coluna de tempo.

### EXPLAIN (30 000 linhas, 50 pontos, `ANALYZE`; Testcontainers)

Consulta equivalente à de `lastValidIssuedAt` (`WHERE flood_point_id = ? AND status = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1`), com `EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)`:

```
Limit (actual rows=1 loops=1)
  ->  Custom Scan (DeferredChunkAppend) on flood_predictions (actual rows=1 loops=1)
        Order: "timestamp" DESC
        Filter: ((flood_point_id = 3)) AND (((status)::text = 'SUCCESS'::text))
        Chunks Visited: 1
        ->  Limit (actual rows=1 loops=1)
              ->  Index Scan using _hyper_4_2_chunk_idx_prediction_point_timestamp on _hyper_4_2_chunk (actual rows=1 loops=1)
                    Index Cond: (flood_point_id = 3)
                    Filter: ((status)::text = 'SUCCESS'::text)
```

Sem `ANALYZE` o plano mostra só o `DeferredChunkAppend` (chunks são escolhidos na execução); por isso o teste usa `ANALYZE`. Visita 1 chunk de vários.

## Código

- `FloodPrediction`: campos novos (`jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`, `issuedAt` como `Instant`).
- `FloodPredictionHistoryService`: `record(request, result, floodPointId)`, `findLatest(floodPointId, lat, lon)`, `findLastValidIssuedAt(floodPointId, lat, lon)`. Grava as colunas; `message` recebe só o texto fixo (`DegradationReason.message()` em falha, "Previsão gerada pelo modelo." em sucesso).
- `lastValidIssuedAt`: última linha SUCCESS por `flood_point_id`; se não houver, a busca antiga por latitude/longitude. `findLatest` segue a mesma regra (sem linha por id, cai na coordenada).
- Linhas anteriores à V8 (auditoria em JSON na `message`) continuam legíveis por `parseLegacyAudit`.
- `MapiServiceImpl`: o ciclo (`refreshPredictionsForActivePoints`) e o GET por ponto passam `point.getId()`; o POST de cenário e o `getPreciseData(lat, lon)` público passam `null`.

## Testes (Testcontainers, `ddl-auto=validate`)

Novo `FloodPredictionAuditColumnsIntegrationTest` (9): migração e colunas; CHECK rejeita valor fora do enum e aceita NULL; FK rejeita ponto inexistente; linha antiga com NULLs e `imputed_features = []`; gravação preenche as colunas e `message` sem JSON; leitura reconstrói o contrato das colunas; `lastValidIssuedAt` por ponto (dois pontos na mesma coordenada não se misturam; falha posterior não o altera); fallback por coordenada; EXPLAIN com Index Scan. `MigrationsIntegrationTest` agora espera a versão 8.

## Medidas

| Medida | Linha de base `integracao/fase-2` | Depois | Origem |
|---|---|---|---|
| Testes | 92 | 101, 0 falhas | soma de `target/surefire-reports/*.txt` após `./mvnw -q clean test` |
| Cobertura de linhas | 42,3 % (1476/3487) | 42,5 % (1490/3505) | JaCoCo 0.8.12 via linha de comando (`jacoco.csv`, LINE) |

## Pendências para a integração

1. **Numeração.** Nesta branch só existem V1, V2 e V8 (o Flyway aceita a lacuna em banco novo). Na integração, V3–V7 entram antes; V8 só depende de `flood_points` (V1).
2. **Banco existente.** Quem já rodou V1/V2 aplica V8 sem perda; linhas antigas ficam com as colunas novas NULL.
3. **`top_contributors`** foi acrescentada além do pedido; se o autor preferir não ter a coluna, os contribuintes voltam a se perder na releitura.
4. **Fallback por coordenada** continua existindo para linhas antigas e para o POST de cenário; pode sair quando as linhas antigas saírem da janela de interesse.
5. O índice antigo `idx_prediction_timestamp` foi mantido (nada destrutivo).
