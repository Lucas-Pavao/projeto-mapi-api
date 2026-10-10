# 0001 — Fuso horário: UTC na persistência, America/Recife só na exibição

- Status: aprovado
- Data: 2026-10-10
- Decisores: Arquiteto (autor), Lucas (aprovou em 2026-10-10)
- Tarefa: F1-ARQ-03

## Contexto
As convenções se contradiziam: `agents.md` §3.1 mandava converter para UTC−3 **antes de persistir**;
`GEMINI.md` mandava armazenar em UTC. O código não segue nenhuma das duas de forma explícita
(medido em 2026-10-10 com `grep` em `src/main/java`):

- 0 usos de `ZoneId`, `ZoneOffset`, `atZone` ou `America/Recife`;
- 24 usos de `LocalDateTime.now()` (dependem do fuso padrão da JVM, não declarado no Dockerfile/compose);
- colunas temporais `TIMESTAMP WITHOUT TIME ZONE` (`V1__baseline_schema.sql:18,51,80,101`);
- Open-Meteo chamado com `timezone=auto` (`WeatherServiceImpl.java:47`, `MarineServiceImpl.java:37`),
  que devolve hora local sem offset;
- `hibernate.jdbc.time_zone: UTC` (`application.yml:21`).

Resultado: o fuso real de cada série no banco **não está documentado** e pode variar por fonte.
Para a dissertação, isso compromete o alinhamento chuva × maré × rótulo, em que 3 h de erro é
do tamanho do horizonte de previsão.

## Opções consideradas
### A — Persistir hora local (UTC−3), como dizia o agents.md
A favor: leitura direta no banco. Contra: Open-Meteo, ANA e a tábua em UTC precisam de conversão
na entrada; ambíguo se o Brasil voltar a ter horário de verão; padrão menos comum para série temporal.
### B — Persistir em UTC (`timestamptz` / `Instant`), converter só na exibição
A favor: instante absoluto sem ambiguidade; padrão do TimescaleDB; facilita o join com Open-Meteo
(UTC nativo). Contra: exige migrar colunas e reinterpretar o histórico já carregado.

## Decisão
**B.** Todo instante é persistido em UTC. A conversão para `America/Recife` acontece só na
camada de exibição (DTO de resposta ou front). Cada parser declara o fuso de origem da sua fonte.

## Consequências
- `agents.md` §3.1 e o checklist §6 foram reescritos; o `GEMINI.md` já estava alinhado.
- Implementação (Fase 2, junto do pipeline de ingestão, F2-DADOS-05/F2-BACK-01):
  1. F1-BACK-03 levanta o fuso real de cada fonte/parser (APAC, ANA, Cemaden, TabuaMare, Open-Meteo, CKAN);
  2. migração Flyway `timestamp` → `timestamptz` reinterpretando o histórico com o fuso levantado
     por fonte — **migração de dado: precisa de aprovação própria antes de rodar**;
  3. `LocalDateTime.now()` → `Instant.now()` / `Clock` injetado; `TZ=UTC` explícito no container.
- Até a migração, nenhum dataset de treino deve ser tratado como alinhado no tempo sem conferência.

## Condições que reabririam a decisão
Nenhuma prevista; só uma fonte que não informe fuso de forma alguma exigiria regra específica.
