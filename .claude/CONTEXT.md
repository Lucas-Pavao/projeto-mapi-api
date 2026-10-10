# Contexto compartilhado da revisão MAPi

Atualizado por acréscimo. Estado oficial de tarefas: `.claude/mapi/PROGRESSO.md`.

## Parte estável (ler sempre)
- Glossário: `docs/GLOSSARIO.md`. Skills: `.claude/skills/mapi-*`.
- Convenções-fonte: `agents.md`, `GEMINI.md` (api); `AGENTS.md`, `GEMINI.md` (front). AI não tem.
- Repositórios irmãos: `../projeto-mapi-ai`, `../projeto-mapi-front`, `../projeto-mapi`
  (= projeto-mapi-sensores, antigo MAPI Edge, descontinuado).
- Rasters PE3D: `../Modelo digital de terreno (RASTER)/` — 48 zips MDT, ~1,1 GB.

## MCPs (2026-10-10)
Nenhum MCP essencial carregado (Filesystem, Git, GitHub, PostgreSQL, Context7 ausentes).
Substitutos: Bash cobre Filesystem e Git; `psql` e `docker` presentes; `gh` ausente (PRs
não podem ser abertos daqui); WebFetch disponível para Fetch. Ausentes sem substituto:
Context7 (documentação consultada via web), Prometheus/Grafana MCP (consultar via HTTP
quando a stack estiver de pé), Playwright MCP (há claude-in-chrome como alternativa para
screenshots). Ferramentas ausentes no host: `mvn` (usar `./mvnw`), `k6` (usar container),
GDAL/rasterio (usar container).
O que deixou de ser verificado na F0 por isso: schema real do banco, tamanho das
hypertables, EXPLAIN — nenhum container do banco estava rodando.

## Decidido
- 2026-10-10: estado e docs transversais no projeto-mapi-api (ver PROGRESSO.md).

## Em aberto
- Perguntas 1–7 do PROGRESSO.md (fuso, arquitetura alvo, fonte única do schema,
  Testcontainers, WIP do front, glossário ⚠, repo Edge).

## Acréscimo 2026-10-10 (fim da Fase 1)
- Decidido: ADRs 0001 (UTC), 0002 (camadas), 0003 (Flyway), 0004 (Testcontainers); front pode ser
  editado sobre o WIP; glossário v1 confirmado; MAPI Edge descontinuado.
- Estado: Fase 1 concluída. Leia só `docs/HANDOFF-fase-1.md`, `docs/handoff/fase-1.json` e
  `docs/MATRIZ-PRIORIDADE-FASE-1.md`. Os relatórios brutos em `docs/revisao/fase-1/` não atravessam
  para a Fase 2 (consultar só um trecho, se um item exigir).
- Banco local sem histórico (snapshot `3ace3cde…`). Imagem local `timescale/timescaledb:latest-pg16`
  = PG 16.14 + TimescaleDB 2.27.2, sem PostGIS.
- Em aberto: perguntas 8–12 do PROGRESSO.md.
- Limite de uso: 3 subagentes caíram por limite semanal; retomar via SendMessage preserva o contexto.
