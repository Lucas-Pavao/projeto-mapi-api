# Handoff — Fase 0 (2026-10-10)

**Feito:** 5 skills de projeto (`.claude/skills/mapi-{clean-arch-java,clean-arch-react,ml-pipeline,geo-pe3d,docs-style}`),
`docs/GLOSSARIO.md` v0, `.claude/mapi/PROGRESSO.md`, `.claude/CONTEXT.md`, inventário barato dos
três repositórios. Sem evals das skills (F0-ARQ-07). Nível real: Opus em toda a fase (registrado).

**Inventário (números em `handoff/fase-0.json`):** backend 138 arquivos / 8 908 LOC, sendo 5 335 em
`service/`; 8 arquivos de teste / 668 LOC. IA 847 LOC Python, 3 artefatos de modelo sem metadata.
Front: MapPage 1,22 MB de JS, `MapView.tsx` 1 106 linhas, 0 `any`.

**Achados da varredura (a confirmar na F1):**
- Crítico p/ dissertação: artefatos de IA (`flood_classifier.joblib`, `scaler.joblib`, `water_level_lstm.h5`) sem hash de dataset, lista de features nem versão — não rastreáveis.
- Crítico p/ dissertação: conflito de fuso — agents.md (UTC−3 antes de persistir) × GEMINI.md e `application.yml:21` (UTC).
- Importante: schema duplicado em Flyway `V1__baseline_schema.sql` e `TimescaleSetup.sql`.
- Importante: imagem `timescale/timescaledb:latest-pg16` (tag flutuante, sem PostGIS) — reprodutibilidade e Fase 3.
- Importante: testes de integração em H2 não exercitam TimescaleDB; sem ArchUnit, sem Testcontainers.
- Importante: raio de vínculo usado em vários pontos (3 km em `MapiController.java:28`, `FloodEventServiceImpl.java:66`; 10 km de log em `:57`) sem registro como metadado da série.
- Desejável: 25/36 DTOs não são record; sem MapStruct apesar do agents.md.
- Docs: READMEs do AI (L10, L30–33, L104) e do Front (L95) ainda citam MAPI Edge/MQTT/"quatro componentes".
- Front com 14 arquivos não commitados (WIP do Lucas?).

**Pendências:** perguntas 1–7 do PROGRESSO.md. Nada foi alterado em código.

**Próxima tarefa:** F1-BACK-02 (leitura dirigida do backend), em paralelo F1-IA-02 e F1-QA-01.
