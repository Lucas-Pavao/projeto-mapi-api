# 0004 — Testcontainers (TimescaleDB) nos testes de integração

- Status: aprovado
- Data: 2026-10-10
- Decisores: Arquiteto (autor), Lucas (aprovou em 2026-10-10)
- Tarefa: F1-ARQ-03

## Contexto
`GEMINI.md` manda usar H2. `src/test/resources/application.yml` usa H2 com Flyway desligado,
porque as migrations são SQL de Postgres/TimescaleDB. Consequência: hypertables, `time_bucket`,
continuous aggregates, queries nativas e (na Fase 3) PostGIS **nunca são exercitados em teste**.
Inventário: 8 arquivos de teste, 668 LOC.

## Opções consideradas
### A — Continuar com H2
A favor: rápido, sem Docker. Contra: não testa o que de fato quebra (SQL nativo, migrations).
### B — Testcontainers com a mesma imagem do compose
A favor: testa migrations Flyway e queries reais. Contra: exige Docker no CI e na máquina; testes mais lentos.

## Decisão
**B** para testes de repositório, migração e ingestão. Testes unitários (parsing, cálculo)
continuam sem banco, com Mockito. H2 sai quando o primeiro teste Testcontainers estiver verde.

## Consequências
`GEMINI.md` atualizado. A imagem precisa ter versão fixada (hoje `latest-pg16`) para os testes
serem reproduzíveis; isso entra na decisão do banco (F2-DADOS-02), que também avalia PostGIS.

## Condições que reabririam a decisão
CI sem Docker disponível.
