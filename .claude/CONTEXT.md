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

## Acréscimo 2026-10-10 (primeira carga)
- **Rodar o compose sempre a partir da worktree `integracao/fase-2`** com `-p projeto-mapi-api`
  (volume `projeto-mapi-api_postgres_ha_data`). O checkout principal está na branch de documentação
  `revisao/fase-0`, cujo compose ainda aponta a imagem antiga e um volume antigo — usá-lo criou por
  engano um volume vazio (removido em 2026-10-10 após conferir 0 tabelas).
- Banco local agora tem V1–V10 e a primeira carga (snapshot pré-migração `35dccf45…`).
- CKAN é 100% precisão DAY → regra prévia troca a tarefa principal para a diária (corte 06:00).

## Acréscimo 2026-10-10 (fim das Fases 2 e 3)
- Ler só: `docs/HANDOFF-fase-2.md`, `docs/HANDOFF-fase-3.md`, `docs/handoff/fase-{2,3}.json`,
  `docs/MODELAGEM-IA.md` e `docs/MEMO-ESCASSEZ-DE-ROTULO.md`.
- Integração: `integracao/fase-2` @ e27755b (worktree no scratchpad `wt-integ`), V1–V13, 218 testes.
- Bloqueio central: pergunta 28 (F1 físico+emulador × F2 rótulo ampliado). Sem ela, a Fase 4 não começa.
- Autorização durável do Lucas para implementar/decidir no escopo; registrar tudo aqui e no PROGRESSO.
