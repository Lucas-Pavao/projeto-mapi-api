# 0007 — Contrato de predição entre IA, backend e front

- Status: proposto, v2. Os três pontos da decisão de 2026-10-10 já estão aprovados pelo Lucas (enum fechado, probabilidade `null` em falha, `modelVersion`); os itens marcados **[aprovar]** ainda não.
- Data: 2026-10-10
- Decisores: Arquiteto (autor), checkpoint Máximo, Lucas
- Tarefa: F2-ARQ-02 · matriz item 7
- Checkpoint: v2 **APROVADA** em 2026-10-10 com 3 ressalvas, todas aplicadas (fonte de `lastValidIssuedAt` = ciclo de coleta; prazo e log do `.catch`; log de `featureId` desconhecido).
- Histórico: a v1 foi reprovada no checkpoint Máximo (7 motivos: ordem de implantação quebrava o front, campo de tempo ambíguo, faltava regra de coerência, nomes internos de feature iam para a tela, faltavam marcações [aprovar], a opção A foi tratada de forma superficial, faltava a fonte da última previsão válida). Esta v2 responde a todos.

## Contexto
Medido na Fase 1 (`docs/CONSISTENCIA-ENTRE-REPOS.md` §1–2):
- O enum de risco diverge. A IA emite `LOW/MEDIUM/HIGH`; o backend repassa `String` com o comentário `EXTREME`
  (`FloodPredictionResponseDTO.java`); o front trata `CRITICAL` (`MapView.tsx:390`), que ninguém emite.
- Em falha, o backend devolve `floodProbability = 0.0` (`FloodPredictionServiceImpl.java:54-60`). O front mostra "0%", e `UNKNOWN` aparece com 3 cores diferentes.
- O zod do front exige `floodProbability: z.number()` e `riskLevel: z.string()` (`floodPointResponse.schema.ts:17-18`).
- Não há `modelVersion`. O front exibe "Neural Engine v2.4" fixo no código. `message` leva o texto da exceção interna até o cliente.
- O backend imputa 0.0 em chuva, maré e rio sem declarar. `estimatedTimeToEvent` nunca é preenchido.

## Opções consideradas
### A — Manter `String` livre e o 0.0, acrescentando um flag `degraded: true`
A favor: não quebra nenhum consumidor. Contra: o consumidor que ignora o flag continua mostrando "0%",
que é exatamente o defeito de hoje. Três componentes do front já tratam o mesmo campo de três jeitos
(F1-F2-04), então confiar que todos leiam o flag repete a causa do problema.
### B — Enum fechado + `null` em falha + campos de degradação explícitos, com implantação compatível
A favor: zod e compilador barram valor inválido; falha deixa de ter forma de previsão. Contra: exige
ordem de implantação cuidadosa nos três repositórios.
### C — Versionar a rota (`/v2/...`) e manter a v1
Contra: o único consumidor é o próprio front. Manter duas versões não paga o custo.

## Decisão
**B**, sem nova rota. O contrato abaixo é o canônico. Mudança futura passa por este ADR e pelo `docs/openapi/`.

### 1. Enum (aprovado)
`RiskLevel = LOW | MEDIUM | HIGH | UNKNOWN`. `UNKNOWN` = não há previsão válida, e **não** é risco baixo.
`CRITICAL` e `EXTREME` saem. **A IA nunca emite `UNKNOWN`**: quem atribui `UNKNOWN` é o backend.

### 2. Regras de coerência (valem para todo payload)
| Se `riskLevel` é… | então |
|---|---|
| `LOW`/`MEDIUM`/`HIGH` | `floodProbability` ∈ [0, 1], não nula; `modelVersion` não nula; `issuedAt` não nula; `degradationReason = null` |
| `UNKNOWN` | `floodProbability = null`; `degradationReason` não nula; `horizonHours`, `validUntil` e `modelVersion` podem ser `null` |

A classe vem dos limiares do modelo. A **IA** aplica os limiares e grava esses limiares no
`metadata.json` do artefato (skill ml-pipeline §7). O backend não recalcula a classe; valida só o
enum, a faixa e a coerência acima. Payload incoerente da IA vira `UNKNOWN` com `AI_ERROR`.

### 3. IA → backend (`POST /v1/predict/flood`, snake_case)
```json
{
  "flood_probability": 0.42,
  "risk_level": "MEDIUM",
  "model_version": "xgb-h3-2026.11.0+a1b2c3d",
  "horizon_hours": 3,
  "issued_at": "2026-10-10T12:00:00Z",
  "top_contributors": [{"feature_id": "tide_height_t1", "contribution": 0.18}],
  "imputed_features": ["river_level"]
}
```
- `model_version`: obrigatório (aprovado). Até haver artefato versionado (Fase 4), a IA envia
  `"unversioned"` **[aprovar]**.
- `horizon_hours`, `top_contributors` e `imputed_features` são opcionais até a Fase 4 **[aprovar]**.
- Dado insuficiente: a IA responde **422** `{"detail": "...", "missing_features": ["..."]}` em vez
  de um 0.0 **[aprovar]**.

### 4. Mapeamento de falha no backend
| Situação | `riskLevel` | `degradationReason` |
|---|---|---|
| IA responde 422 | `UNKNOWN` | `INSUFFICIENT_DATA` |
| Timeout de leitura | `UNKNOWN` | `AI_TIMEOUT` |
| Conexão recusada ou circuit breaker aberto | `UNKNOWN` | `AI_UNAVAILABLE` |
| 5xx, corpo inválido ou payload incoerente | `UNKNOWN` | `AI_ERROR` |

O texto da exceção vai só para o log. `message` é um texto fixo por motivo, em português.

### 5. Backend → front (`FloodPredictionResponseDTO`, camelCase)
| Campo | Tipo | Regra |
|---|---|---|
| `riskLevel` | `RiskLevel` | aprovado |
| `floodProbability` | `number \| null` | aprovado; ver §2 |
| `modelVersion` | `string \| null` | aprovado. Ao cliente vai só a parte semântica (`xgb-h3-2026.11.0`); o hash git fica no log e na auditoria **[aprovar]** |
| `issuedAt` | ISO-8601 UTC com `Z` | instante em que a previsão foi emitida, ou em que a tentativa falhou, se `UNKNOWN` **[aprovar]** |
| `horizonHours` | `number \| null` | **[aprovar]** |
| `validUntil` | ISO-8601 UTC \| `null` | `issuedAt + horizonHours`: fim da janela prevista; `null` se `UNKNOWN` **[aprovar]** |
| `degradationReason` | `INSUFFICIENT_DATA \| AI_TIMEOUT \| AI_UNAVAILABLE \| AI_ERROR \| null` | **[aprovar]** |
| `lastValidIssuedAt` | ISO-8601 UTC \| `null` | `issuedAt` da última previsão `SUCCESS` do ponto em `flood_predictions` (consulta indexada por ponto + timestamp); `null` se nunca houve **[aprovar]** |
| `contributors` | `{featureId, label, contribution}[]` | `label` vem de um **catálogo fixo** em português no backend (ex.: `tide_height_t1` → "Maré prevista na próxima hora"). `featureId` fora do catálogo vira "Outro fator" e o backend registra `WARN` com o id desconhecido (catálogo desatualizado). Vazio até a Fase 4 **[aprovar]** |
| `imputedFeatures` | `{featureId, label}[]` | mesmo catálogo; vazio se nada foi imputado **[aprovar]** |
| `estimatedTimeToEvent` | `string \| null` | obsoleto; segue `null`; remoção exige aprovação |
| `message` | `string \| null` | texto fixo por motivo; nunca o texto da exceção |

HTTP: `POST /api/flood-predictions` responde 201 também em falha. Proposta: 200, inclusive para
`UNKNOWN`, porque é resposta válida e não recurso criado **[aprovar]**.

### 6. Exibição no front **[aprovar — decisão de UX para a Defesa Civil]**
`UNKNOWN` → estado neutro "Previsão indisponível", com o motivo legível e "Última previsão válida:
<`lastValidIssuedAt` em America/Recife>". Nunca usa cor de risco nem porcentagem. Todo instante é exibido
em America/Recife (ADR 0001), com a janela "previsão para até <`validUntil`>".

### 7. Efeitos colaterais **[aprovar — muda o que é persistido]**
`GET /api/pontos/{slug}` e os cliques no mapa deixam de gravar em `flood_predictions` (F1-B2-25,
F1-F2-06): ler não escreve. **Quem prevê passa a ser o ciclo de coleta**: ao fim de cada ciclo do
`SensorCollectionTask`, um passo agendado (no pool `taskExecutor`, sem bloquear o agendador) pede uma
previsão por ponto ativo e grava em `flood_predictions`, o que está alinhado ao `agents.md` §4.1. O GET devolve a
última previsão gravada do ponto, sem chamar a IA. Assim `lastValidIssuedAt` fica tão atual quanto o
ciclo de coleta. Se a IA estiver fora por muitos ciclos, o campo envelhece de propósito: é isso que o
operador precisa ver. O `POST` explícito continua existindo para cenários. Ela não entra nos resultados da dissertação (decisão de 2026-10-10).

## Ordem de implantação (compatível, sem janela de quebra)
1. **Front primeiro**: zod tolerante, com `floodProbability: z.number().nullable()`; `riskLevel` como
   `z.enum([...])` com `.catch('UNKNOWN')`, para que um valor antigo não derrube o painel; campos novos
   `optional()`; estado `UNKNOWN` próprio. Funciona com o backend atual e com o novo.
2. **Backend**: enum, `null` em falha, campos novos e fim da gravação no GET. O front do passo 1 já aceita.
3. **IA (Fase 4)**: `model_version` real, 422, `contributors`. O backend do passo 2 já aceita a ausência.
4. Depois que os três estiverem no ar, **até o fim da Fase 4**: o zod do front aperta (sai o
   `.catch`) e `estimatedTimeToEvent` é removido, com aprovação. Durante a transição, cada vez que o
   `.catch('UNKNOWN')` disparar, o front registra `console.warn` com o valor recebido, e o backend
   tem teste de contrato que impede emitir valor fora do enum. Assim a coerção não esconde um bug.

## Consequências
- Medidas: tratamentos visuais de `UNKNOWN` (3 → 1); valores de enum fora do glossário (2 → 0);
  falha exibida como "0%" (sim → não); gravações em `flood_predictions` por GET (1 por chamada → 0).
- Novo artefato: catálogo `featureId → label` versionado com o glossário (`docs/GLOSSARIO.md`).
- Testes de contrato: backend (coerência §2 e mapeamento §4) e front (zod aceita o payload antigo e o novo).

## Condições que reabririam a decisão
Necessidade de um quarto nível de risco com protocolo próprio da Defesa Civil: ele entra no enum por
este ADR, não por código.
