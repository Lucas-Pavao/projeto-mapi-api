# F2-BACK-08 — Contrato de predição no backend (ADR 0007, passo 2)

Branch `feat/contrato-predicao-backend`, a partir de `integracao/fase-2`. Sem push, sem migração, sem mexer em docker-compose, nenhum endpoint removido.

## O que mudou

| Item do ADR | Implementação |
|---|---|
| §1 enum | `dto/RiskLevel` (`LOW/MEDIUM/HIGH/UNKNOWN`) e `dto/DegradationReason`. A coluna `flood_predictions.risk_level` continua `String`; a conversão está em `RiskLevel.fromColumn` / `fromAi`. |
| §2 coerência | `service/flood/PredictionCoherence.violation(dto)`. O serviço confere o que monta antes de devolver; os testes aplicam a mesma regra a toda saída. |
| §3 payload da IA | `FloodPredictionServiceImpl.AiPredictionDTO` lê `model_version`, `horizon_hours`, `issued_at`, `top_contributors`, `imputed_features`. Valor de `risk_level` fora de LOW/MEDIUM/HIGH (inclusive `UNKNOWN`), probabilidade fora de [0,1] ou nula, `model_version` ausente, horizonte ≤ 0, data ilegível ou contribuinte inválido → `UNKNOWN/AI_ERROR`. |
| §4 falha | 422 → `INSUFFICIENT_DATA`; timeout de leitura → `AI_TIMEOUT`; conexão recusada, timeout de conexão ou `CallNotPermittedException` → `AI_UNAVAILABLE`; 5xx, corpo inválido/vazio ou incoerente → `AI_ERROR`. Exceção só vai ao log. |
| §5 DTO | `FloodPredictionResponseDTO` virou `record` (Lombok `@Builder`), com `Instant` serializado em ISO-8601 com `Z`, truncado em segundos. `estimatedTimeToEvent` fica `@Deprecated` e sempre `null`. `modelVersion` leva só a parte antes do `+`. |
| Catálogo | `resources/feature-catalog.properties` (UTF-8) + `FeatureCatalog`: 28 features do `feature_engineering.py` e 9 termos do ADR/glossário; id desconhecido → "Outro fator" + `WARN`. |
| Imputação | `MapiServiceImpl.predictAndStore` registra em `imputedFeatures` cada 0.0 que coloca no payload: `rainfall_{3,6,12,24}h_accumulated`, `tide_level`, `river_level`. O valor enviado à IA não mudou. |
| §7 efeitos colaterais | GET `/api/pontos`, `/api/pontos/{slug}` e `/api/environmental-data` não chamam a IA nem gravam. O GET devolve a última linha de `flood_predictions` do ponto, com `lastValidIssuedAt`. `PredictionCycleRunner` roda `refreshPredictionsForActivePoints()` no `taskExecutor` ao fim de cada ciclo do `SensorCollectionTask` (descarta o disparo se a rodada anterior ainda corre). |
| HTTP | `POST /api/flood-predictions` responde 200, inclusive em `UNKNOWN`. |

## Como a auditoria cabe sem migração

A tabela não tem colunas para versão completa do modelo, horizonte, motivo, imputações e contribuintes. Eles vão como JSON na coluna `message` (TEXT) de `flood_predictions` (`FloodPredictionHistoryServiceImpl`). A versão completa com hash git fica ali e no log. Em falha, `flood_probability` agora é gravada como `NULL` (antes, 0.0).

## Medidas

| Medida | Antes (`integracao/fase-2`) | Depois | Origem |
|---|---|---|---|
| Testes | 49 | 92, 0 falhas | soma de `target/surefire-reports/*.txt` após `./mvnw -q clean test` |
| Cobertura de linhas | 36,2 % | 42,3 % (1476 de 3487) | JaCoCo 0.8.12 via `jacoco.csv`, coluna LINE |
| Gravações em `flood_predictions` por GET | 1 por chamada | 0 | `PredictionSideEffectsIntegrationTest.getsNaoChamamAIANemGravamEmFloodPredictions` (Testcontainers) |
| Valores de risco emitidos fora do enum | `EXTREME` no comentário, texto livre da IA | 0 | `FloodPredictionJsonTest`, `FloodPredictionServiceImplTest` |
| Falha exibida como probabilidade 0.0 | sim | não (`null`) | `FloodPredictionServiceImplTest.assertUnknown` |

Testes novos: coerência §2 (6), mapeamento §4 e payload incoerente com IA simulada por `MockRestServiceServer` (20), catálogo (5), serialização com `Z` (3), runner do ciclo (4), integração Testcontainers (5).

## Riscos e pendências para decisão

1. **Ordem de implantação.** A IA atual (`app.py`) não envia `model_version`. Pelo §2 isso é incoerente, então com este backend no ar e a IA antiga toda previsão sai `UNKNOWN/AI_ERROR` até o passo 3 (IA). Se não puder esperar, a saída é aceitar `unversioned` quando faltar `model_version`; não fiz isso por contrariar o §3.
2. **`lastValidIssuedAt`** usa a consulta por latitude/longitude do ponto. O ADR pede índice por ponto + timestamp, mas só existe `idx_prediction_timestamp`, e migração está vetada. Funciona; escala mal com a tabela grande. Pede `V3` (coluna do ponto e índice) com aprovação.
3. **Auditoria em JSON na coluna `message`** é dívida: o certo são colunas próprias (mesma migração).
4. **Fuso.** A conversão `LocalDateTime` ↔ `Instant` usa o fuso da JVM, igual ao que as linhas existentes assumem; fica correta quando o ADR 0001 migrar para UTC.
5. **Imputação parcial.** `determinePreciseData` ainda grava 0.0 em chuva/rio quando há sensores próximos sem esse dado (`orElse(0.0)`); isso não aparece em `imputedFeatures`, porque o valor já chega sem marca ao passo de predição.
6. **Linhas legadas** (`SUCCESS` sem versão de modelo na auditoria) são lidas como `UNKNOWN/AI_ERROR` até o primeiro ciclo gravar uma nova.
7. **Ponto sem previsão gravada** devolve `floodPrediction: null` (não há motivo honesto no enum para esse caso). O front do passo 1 aceita `null`.
8. Não há circuit breaker configurado para a IA hoje; o mapeamento de `CallNotPermittedException` está pronto e testado para quando houver.
