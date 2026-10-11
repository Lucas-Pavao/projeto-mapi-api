# Consistência entre projeto-mapi-api, projeto-mapi-ai e projeto-mapi-front

- Tarefa: F1-ARQ-01 · Data: 2026-10-10 · Autor: Arquiteto
- Fontes: `docs/revisao/fase-1/F1-IA-02.md` §5.7, `F1-BACK-02.md`, `F1-BACK-03.md` (tabela de fuso),
  `F1-FRONT-02.md`. Os números citados vêm desses relatórios. Os dois achados críticos de ingestão
  e o "nenhum timeout" foram conferidos no código pelo Arquiteto.

## 1. Enum de risco

| Valor | Glossário | IA | Backend | Front |
|---|---|---|---|---|
| `LOW`/`MEDIUM`/`HIGH` | sim | emite (`app.py:164-168`, limiares sem curva) | repassa como `String` | cores próprias, contraste 2,15–2,80:1 |
| `UNKNOWN` | sim (degradação) | — | em qualquer falha (`FloodPredictionServiceImpl.java:54-60`) | **3 tratamentos**: vermelho no marcador (`MapView.tsx:677`), cinza no popup, verde "Baixo" no card (`FloodPointDetailCard.tsx:85`) |
| `CRITICAL` | não | — | — | tratado (`MapView.tsx:390`), ninguém emite |
| `EXTREME` | não | — | comentário em `FloodPredictionResponseDTO.java:172` | — |

**Alvo:** enum fechado `LOW | MEDIUM | HIGH | UNKNOWN` nos três lados. Backend vira `enum` Java
e o front vira `z.enum` com `switch` exaustivo. `CRITICAL`/`EXTREME` saem. Isso muda contrato,
então precisa de aprovação (pergunta 8).

## 2. Contrato de predição

| Item | IA | Backend | Front | Problema |
|---|---|---|---|---|
| Probabilidade | `flood_probability` | converte para `floodProbability` | `floodProbability` | ok |
| Probabilidade em falha | — | `0.0` | "0%" | falha parece "0% de chance"; deve ser `null` |
| `modelVersion`, `topContributors` | ausentes | ausentes | "Neural Engine v2.4" fixo no código | contrato mínimo da skill ml-pipeline inexistente |
| `estimatedTimeToEvent`, `message` | nunca envia | mapeia, sempre `null` | opcional | campos sem uso |
| Entrada imputada | recebe 0.0 sem saber | imputa 0.0 em chuva, maré e rio (`MapiServiceImpl.java:129-148`) | — | a imputação não é declarada, contra a skill ml-pipeline §8 |
| Status da auditoria | — | `SUCCESS`/`FAILED`; `UNKNOWN` documentado mas não usado | — | — |
| Reprocessamento | — | inexistente (agents.md prometia `PENDING`) | — | — |
| Efeito colateral | — | `GET /api/pontos/{slug}` grava em `flood_predictions` | cada clique dispara POST de predição | auditoria inflada por navegação; a taxa de alerta fica sem significado |

## 3. Fuso horário (ADR 0001: UTC na persistência)

| Fonte / camada | Situação | Fonte do achado |
|---|---|---|
| Open-Meteo archive | UTC (sem `timezone`), gravado como veio | F1-BACK-03 |
| Open-Meteo current e marine | `timezone=auto` → hora de Recife, **na mesma coluna** do archive | F1-BACK-03 |
| ANA histórico | `minusHours(3)` incondicional, sem citação | F1-BACK-03 |
| ANA ao vivo | sem deslocamento (diferente do histórico) | F1-BACK-03 |
| APAC histórico | dia civil gravado como 00:00 | F1-BACK-03 |
| APAC/Cemaden ao vivo | sem fuso; se o parse falha, usa `now()` | F1-BACK-02/03 |
| TabuaMare/DHN | `tide_tables.timezone` existe, 0 usos | F1-BACK-03 |
| CKAN | só data → 00:00, depois realinhado ao pico de chuva | F1-BACK-03 |
| Payload para a IA | `LocalDateTime.now()` sem fuso | F1-IA-02 |
| Front | hora local do navegador, sem `America/Recife` (6 pontos de formatação) | F1-FRONT-02 |

São 5 tratamentos distintos gravados nas mesmas colunas. **Para a dissertação, nenhum alinhamento
chuva × maré × rótulo do banco atual é confiável sem antes corrigir isso.**

## 4. Unidades e nulos

| Grandeza | Situação | Alvo |
|---|---|---|
| Nível do rio | unidade da fonte repassada sem declarar (m ou cm) | m, convertido no parser |
| Chuva | `current_rainfall` mistura Open-Meteo (mm/h) e sensor; acumulados só de reanálise | mm e mm/h, com a fonte na coluna |
| Maré | limiar de 2,0 m sem fonte; referência (NR da DHN) não declarada | m (NR), documentado |
| Nulos no export | 6 constantes inventadas (25 °C, 1013 hPa, solo 30, chuva 0…) sem flag | `null` + coluna `*_imputed` |
| CSV | sem unidade, sem fuso, com `"null"` literal | cabeçalho com unidade e manifesto (hash, período, versão) |

## 5. Nomes de campo

Backend e front usam camelCase em inglês. A IA usa snake_case e aceita 4 aliases de lat/lon.
Alvo: camelCase no JSON entre API e front e snake_case só dentro do Python. A conversão fica
num único lugar do backend (já é assim em `FloodPredictionServiceImpl.java:62-64`). Os termos
do glossário ainda não usados em código (`waterDepth`, `outsideLevel`, `streetElevation`…)
entram quando as features existirem.
