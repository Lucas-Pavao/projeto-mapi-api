# Progresso da revisão multiagente do MAPi

Última execução: 2026-10-10 (2ª) · Fase atual: 2 (Fase 1 concluída) · Próxima tarefa: F2-DADOS-01 / F2-DADOS-05 / F2-BACK-04 (paralelo)

> Local: `projeto-mapi-api/.claude/mapi/PROGRESSO.md`. A pasta-mãe `MAPI/` não é repositório git;
> o estado oficial fica no repositório orquestrador para ser versionado. Docs transversais
> (glossário, ADRs, handoffs) também ficam em `projeto-mapi-api/docs/`.

## Decisões tomadas (não reabrir sem motivo novo)
| Data | Decisão | ADR | Quem decidiu |
|---|---|---|---|
| 2026-10-10 | Estado da revisão, glossário, ADRs e handoffs ficam no projeto-mapi-api (pasta-mãe não é git) | — (operacional) | Arquiteto (F0) — Lucas pode vetar |
| 2026-10-10 | As 5 skills de projeto ficam em `projeto-mapi-api/.claude/skills/`; cópia para os outros repos só depois de aprovadas | — (operacional) | Arquiteto (F0) |
| 2026-10-10 | ~~Skills marcam ports-and-adapters como alvo não aprovado~~ — substituída pelo ADR 0002 | 0002 | Arquiteto (F0) |
| 2026-10-10 | UTC na persistência; America/Recife só na exibição; migração do histórico na Fase 2 com aprovação própria | 0001 | Lucas |
| 2026-10-10 | Manter arquitetura em camadas atual; congelar com ArchUnit | 0002 | Lucas |
| 2026-10-10 | Flyway como fonte única do schema | 0003 | Lucas |
| 2026-10-10 | Testcontainers nos testes de integração; H2 sai | 0004 | Lucas |
| 2026-10-10 | Front: pode-se trabalhar sobre as edições não commitadas existentes | — | Lucas |
| 2026-10-10 | Glossário: ficha = 5 mm, soleira, lâmina e nível lá fora confirmados | — | Lucas |
| 2026-10-10 | projeto-mapi (MAPI Edge) declarado descontinuado | — | Lucas |
| 2026-10-10 | Contrato de predição: enum fechado `LOW/MEDIUM/HIGH/UNKNOWN`, probabilidade `null` em falha, `modelVersion` | — (vira ADR junto com o OpenAPI do contrato) | Lucas |
| 2026-10-10 | Rótulo: desativar o realinhamento ao pico de chuva e reimportar o CKAN preservando a data original | — | Lucas |
| 2026-10-10 | `wipe-database` restrito ao profile `dev` | — | Lucas |
| 2026-10-10 | `flood_predictions` **não** entra nos resultados da dissertação (matriz item 7 fica P1) | — | Lucas |
| 2026-10-10 | Não existe histórico de 5 anos em lugar nenhum: a carga histórica só depois dos itens 1–4 da matriz | — | Lucas |
| 2026-10-10 | ADR 0007 aprovado integralmente (contrato de predição, ordem front → backend → IA) | 0007 | Lucas |
| 2026-10-10 | ADR 0006 aprovado: pipeline staging → validação → consolidada; migrações V3–V6 aprovadas | 0006 | Lucas |
| 2026-10-10 | Dado de desenvolvimento (~4 semanas) descartado no novo pipeline; snapshot `3ace3cde…` é o registro | 0006 | Lucas |
| 2026-10-10 | Contrato de proveniência aprovado: `provenanceId` + `revision`, timestamps ISO com `Z`, manifesto do export, endpoint admin de reprocessamento | 0006 | Lucas |
| 2026-10-10 | Rótulo positivo: só "alagamento" (Cobrade); "inundação" fica de fora | 0006 | Lucas |
| 2026-10-10 | Rótulo diário × horizonte 3/6 h decidido na Fase 4 | — | Lucas |
| 2026-10-10 | Fuso de ANA, APAC 24h e Cemaden verificado por correlação cruzada com Open-Meteo após a primeira carga (F2-DADOS-07); até lá, quarentena | 0006 | Lucas |

## Tarefas
| ID | Fase | Agente | Nível | Tarefa | Status | PR/commit | Resultado em uma linha |
|---|---|---|---|---|---|---|---|
| F0-ARQ-01 | 0 | Arquiteto | Equilibrado* | Criar as 5 skills de projeto com skill-creator | concluido | ca67b07 (branch `revisao/fase-0`) | 2026-10-10 · Opus (sessão única) · `.claude/skills/mapi-*/SKILL.md` ×5 · derivadas de agents.md/GEMINI.md, com tabela de divergências convenção×código; sem evals (ver F0-ARQ-07) |
| F0-ARQ-02 | 0 | Arquiteto | Rápido* | Glossário único | concluido | ca67b07 (branch `revisao/fase-0`) | 2026-10-10 · `docs/GLOSSARIO.md` v0 · 4 termos marcados ⚠ (ficha, soleira, lâmina, nível lá fora) aguardam confirmação contra o modelo Mercury |
| F0-ARQ-03 | 0 | Arquiteto | Rápido* | PROGRESSO.md inicial e `.claude/CONTEXT.md` | concluido | ca67b07 (branch `revisao/fase-0`) | 2026-10-10 · este arquivo + CONTEXT.md |
| F0-ARQ-04 | 0 | Arquiteto | Rápido* | Inventário barato dos 3 repositórios (árvore, LOC, deps, grep) | concluido | ca67b07 (branch `revisao/fase-0`) | 2026-10-10 · números em `docs/handoff/fase-0.json` · cobre F1-BACK-01, F1-FRONT-01, F1-IA-01 |
| F0-ARQ-05 | 0 | Arquiteto | Rápido* | Carregar MCPs e registrar o que ficou sem verificação | concluido | — | 2026-10-10 · nenhum MCP essencial disponível; substitutos CLI listados em CONTEXT.md §MCPs |
| F0-ARQ-06 | 0 | Arquiteto | Rápido* | Handoff da Fase 0 | concluido | ca67b07 (branch `revisao/fase-0`) | 2026-10-10 · `docs/HANDOFF-fase-0.md` + `docs/handoff/fase-0.json` |
| F0-ARQ-07 | 0 | Arquiteto | Equilibrado | Evals das skills (2–3 prompts por skill, com e sem skill) | pendente | | adiado: custo alto, baixo risco agora; fazer antes da Fase 2 |
| F1-BACK-01 | 1 | Backend | Rápido | Inventário barato do backend | concluido | — | coberto por F0-ARQ-04: 138 arquivos / ~8,9 mil LOC main; 8 testes / 668 LOC |
| F1-BACK-02 | 1 | Backend | Equilibrado | Leitura dirigida: aderência à arquitetura, jobs/resiliência (APAC 6 h fora), @Transactional, taskExecutor/Hikari, segurança (JWT, CORS, Actuator) | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet (retomado após o limite de uso) · `docs/revisao/fase-1/F1-BACK-02.md` · 26 achados, 6 críticos; conferidos pelo Arquiteto: 0 timeouts HTTP (o único é a espera do rate limiter), fallback APAC `List.of()` faz falha virar sucesso 0/0, JWT_SECRET padrão público no compose |
| F1-BACK-03 | 1 | Backend | Equilibrado | Revisão a fundo da ingestão histórica (backfill vs. pipeline, proveniência, lacunas, reamostragem, volume, raio do repair-stations) | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet · `docs/revisao/fase-1/F1-BACK-03.md` · 26 achados, 6 críticos; conferidos pelo Arquiteto: rótulo circular (`alignFloodEventsToRainPeaks` sobrescreve startTime 00:00 com o pico de chuva) e ANA sem lat/lon fora do export; 5 tratamentos de fuso distintos; weather_data sem unique (reprocessar duplica) |
| F1-BACK-04 | 1 | Backend | Rápido | Snapshot do OpenAPI (`/v3/api-docs`) versionado em `docs/openapi/` | concluido | 06b4398 | 2026-10-10 · Opus · `docs/openapi/openapi-2026-10-10.json` (45 caminhos, 46 operações, 42 schemas); imagem da API reconstruída porque a de 2026-09-06 não tinha `0554073` |
| F1-BACK-05 | 1 | Backend | Equilibrado | Medidas "antes": cobertura (JaCoCo), p95 por endpoint (k6), N+1 (log Hibernate) | concluido | 06b4398 + k6 fix 8ec2f5f/54275ea | 2026-10-10 · Opus · `docs/medidas/LINHA-DE-BASE-2026-10-10.md`: cobertura 26,1% linhas / 17,6% ramos (export 0,6%); p95 por endpoint (cache: 0,8–4 ms; export 143 ms com banco quase vazio, não representativo). N+1 não medido (fica para o item 12). Achado: heavy_export do k6 fazia 0 requisições (campo `id_ponto`→`slug`) e o limite passava vazio — corrigido |
| F1-FRONT-01 | 1 | Frontend | Rápido | Inventário barato do front | concluido | — | coberto por F0-ARQ-04: bundle MapPage 1,22 MB, 0 `any`, MapView 1 106 linhas |
| F1-FRONT-02 | 1 | Frontend | Equilibrado | Leitura dirigida: estado servidor×UI, re-render do mapa, a11y, 390 px, estado UNKNOWN/erro | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet · `docs/revisao/fase-1/F1-FRONT-02.md` · 21 achados, 2 críticos: UNKNOWN exibido vermelho/cinza/verde em 3 componentes; cada clique dispara POST de predição (grava auditoria) + N chamadas por sensor |
| F1-IA-01 | 1 | IA | Rápido | Inventário barato da IA | concluido | — | coberto por F0-ARQ-04: 847 LOC Python, 3 artefatos sem metadata |
| F1-IA-02 | 1 | IA | Equilibrado | Leitura dirigida do módulo IA e do contrato real de degradação UNKNOWN (backend↔IA↔front) | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet (parou no limite de uso depois de gravar o relatório completo) · `docs/revisao/fase-1/F1-IA-02.md` · 17 achados, 6 críticos [DISSERTACAO]: 9/28 features zeradas na inferência, H = 0 (nowcast), scaler antes do split, 0/4 baselines, artefato sem metadata, fallback silencioso p/ dataset sintético |
| F1-QA-01 | 1 | QA | Rápido | Corrigir READMEs do AI/Front (MAPI Edge, MQTT, "quatro componentes") e varrer o resto | concluido | AI bd15c50 (branch `docs/remove-mapi-edge`); front: edição não commitada no README (WIP do Lucas) | 2026-10-10 · Opus · AI: diagrama sem Edge, "três componentes", link do compose → main; front: o WIP do Lucas já corrigia quase tudo, faltava L95 + link (feito). Achado novo: README do front cita enum `CRITICAL`, que o backend não tem → F1-ARQ-01 |
| F1-QA-02 | 1 | QA | Rápido | Risco Grafana (credencial padrão) e Prometheus sem auth; documentar "só uso local" | concluido | 2fe06c6 (branch `fix/seguranca-local`) | 2026-10-10 · Opus · Postgres 5433 e IA 8000 agora só em 127.0.0.1; `POSTGRES_PASSWORD` por variável; `docs/SEGURANCA-LOCAL.md` com portas, credenciais padrão e riscos que dependem de código; `docker compose config` ok |
| F1-QA-03 | 1 | QA | Equilibrado | Blindar armadilha do k6 no compose (entrypoint em vez de command) | concluido | c21a38c, 8ec2f5f, 54275ea (branch `fix/k6-remote-write-env`) | 2026-10-10 · Opus · `K6_OUT=experimental-prometheus-rw` no environment, `--out` removido do command; testado em grafana/k6:0.54.0 com argumento arbitrário → `output: Prometheus remote write`; `docker compose config` ok |
| F1-ARQ-01 | 1 | Arquiteto | Equilibrado | Relatório de consistência entre repos (campos, unidades, fuso, enum de risco, nulos) | concluido | branch `revisao/fase-0` | 2026-10-10 · Opus · `docs/CONSISTENCIA-ENTRE-REPOS.md` · enum (CRITICAL/EXTREME inexistentes; UNKNOWN em 3 cores), contrato de predição, 5 tratamentos de fuso, unidades e nulos, nomes de campo |
| F1-ARQ-02 | 1 | Arquiteto | Equilibrado + checkpoint Máximo | Diagnóstico e matriz de prioridade | concluido | branch `revisao/fase-0` | 2026-10-10 · Opus (autor) + checkpoint Opus independente · `docs/MATRIZ-PRIORIDADE-FASE-1.md` · 90 achados → 16 itens (7 P0); checkpoint APROVADO com 6 ressalvas, todas aplicadas (novo item 0: snapshot auditável) |
| F1-ARQ-03 | 1 | Arquiteto | Equilibrado | ADRs das divergências convenção×código (fuso, schema Flyway×TimescaleSetup, H2×Testcontainers, records/MapStruct, domínio sem framework) | concluido | d455ae9 | 2026-10-10 · Opus · ADRs 0001–0004 aprovados e convenções ajustadas. Resíduo (DTOs record/MapStruct, desejável) movido para a matriz, item 14, sem ADR por ora |
| F1-QA-04 | 1 | QA | Rápido | Aviso de descontinuado no projeto-mapi (MAPI Edge) | concluido | projeto-mapi d86a996 (branch `docs/descontinuado`) | 2026-10-10 · Opus · bloco DESCONTINUADO no topo do README |
| F1-ARQ-04 | 1 | Arquiteto | Rápido | Handoff da Fase 1 | concluido | branch `revisao/fase-0` | 2026-10-10 · Opus · `docs/HANDOFF-fase-1.md` + `docs/handoff/fase-1.json` (90 achados: 21 críticos, 49 importantes, 20 desejáveis) |
| F2-DADOS-00 | 2 | Dados | Rápido | Snapshot auditável do banco atual (pg_dump + SHA-256 + contagem por tabela) antes de qualquer migração | concluido | 06b4398 | 2026-10-10 · Opus · dump fora do git em `MAPI/snapshots/`, SHA-256 `3ace3cde…4dedd`, manifesto `docs/snapshots/2026-10-10-pre-revisao.md`. Achado: o banco local não tem o histórico de 5 anos (555 leituras de sensor, 40 de clima, 0 eventos); flood_predictions 25 102 linhas |
| F2-DADOS-01 | 2 | Dados | Equilibrado | Benchmark e evidência sobre o banco (tamanho das hypertables, compressão, EXPLAIN ANALYZE, volume projetado) | concluido | ver commit "ADR 0005 v2" | 2026-10-10 · subagente Sonnet · benchmark 50 pts × 5 anos sintético: compressão Timescale 688→126 MB (5,46×), MySQL 493→240 MB; 1 ponto × 5 anos: TS 50–59 ms, MySQL 82,6–90,4 ms (rerodado com buffer pool 2 GB); PostGIS 3.6.4 raster ok em EPSG:31985 |
| F2-DADOS-02 | 2 | Dados | Equilibrado + checkpoint | ADR PostgreSQL+TimescaleDB+PostGIS × MySQL | concluido | idem | 2026-10-10 · subagente Sonnet + checkpoint Opus · ADR 0005: manter PG16 + TimescaleDB + PostGIS (V7); decisivo = raster + custo de reescrita, não velocidade; hipótese do autor só parcialmente confirmada · checkpoint APROVADO com 4 ressalvas aplicadas · status proposto até a pergunta 20 |
| F2-DADOS-03 | 2 | Dados | Rápido | Planilha comparativa do banco (xlsx) | concluido | idem | 2026-10-10 · `docs/medidas/comparacao-banco.xlsx` (abas Critérios e Benchmark) |
| F2-DADOS-04 | 2 | Dados | Equilibrado | `docs/MODELO-DE-DADOS.md` com diagrama | concluido | idem | 2026-10-10 · `docs/MODELO-DE-DADOS.md` com erDiagram do schema atual |
| F2-DADOS-05 | 2 | Dados+Backend | Equilibrado + checkpoint | ADR do pipeline de ingestão (staging → validação → consolidada, idempotência, proveniência) | concluido | 800356c | 2026-10-10 · subagente Sonnet + checkpoint Opus · `docs/adr/0006-pipeline-de-ingestao.md` + `docs/INGESTAO.md` · v1 REPROVADA (borda da hora deslocava leitura hh:00; REVALIDATE perdia linha deslocada pelo fuso; A→B→A não voltava ao A; V7/caggs prematuros), v2 APROVADA com 2 ressalvas aplicadas pelo Arquiteto · status proposto até as perguntas 14–19 |
| F2-DADOS-06 | 2 | Dados | Equilibrado | Políticas de retenção e compressão | concluido | 800356c | 2026-10-10 · ADR 0006 §9: compressão e continuous aggregates **adiados** (5 pontos ≈ 1,5 mi linhas em 5 anos); gatilhos `[sem fonte]`: > 5 GB, p95 export 5 anos > 5 s, ≥ 50 pontos; sem `drop_chunks` |
| F2-BACK-01 | 2 | Backend | Equilibrado | Implementar ingestão aprovada (migração incremental) | pendente | | exige aprovação de migração |
| F2-BACK-02 | 2 | Backend | Equilibrado | Identificador de proveniência nas respostas de dado | pendente | | muda contrato: aprovação |
| F2-BACK-03 | 2 | Backend | Equilibrado | `docs/INGESTAO.md` e `docs/API.md` | pendente | | |
| F2-BACK-04 | 2 | Backend | Equilibrado | Testcontainers com TimescaleDB (ADR 0004): base de teste de repositório, migração Flyway e export; remover H2 quando verde | concluido | 6b8c0d6 (`feat/testcontainers`); relatório 6f05775 | 2026-10-10 · subagente Sonnet · Testcontainers 1.21.4 + TimescaleDB real, Flyway + validate; 7 testes novos (migrations, hypertables, time_bucket/raio/DISTINCT ON, export CSV); Docker 29 exigiu `api.version=1.44` · H2 segue nos testes antigos (pendência) |
| F2-BACK-05 | 2 | Backend | Equilibrado | `wipe-database` só no profile `dev` (decisão 2026-10-10) | concluido | 95364bc (`fix/wipe-database-dev`) | 2026-10-10 · subagente Sonnet · `DevAdminController` com `@Profile("dev")`: sem dev → 404; compose não ativa dev |
| F2-BACK-06 | 2 | Backend | Equilibrado | Desativar `alignFloodEventsToRainPeaks` e preservar a data original do CKAN, com precisão declarada (decisão 2026-10-10) | concluido | 2c15fa4 (`fix/desativa-realinhamento-rotulo`) | 2026-10-10 · subagente Sonnet · realinhamento removido do código e do full-sync; `align-events` responde 410 citando ADR 0006; evento 00:00 continua 00:00 · pendência: export ainda dá janela fixa de 3 h a evento só com data (00:00–03:59) → resolvido pela precisão DAY do ADR 0006 + decisão da Fase 4 (pergunta 17) |
| F2-BACK-07 | 2 | Backend | Equilibrado | Timeouts em todos os clientes HTTP; fallback que não mascara falha (Retry efetivo); métrica de idade do dado | concluido | 03967a6, db38e0f (`fix/timeouts-e-fallback-honesto`) | 2026-10-10 · subagente Sonnet · timeouts por fonte em `app.http`; fallbacks `List.of()` removidos (Retry volta a disparar, ciclo distingue `sourceUnavailable`); gauge `mapi_collector_last_data_age_seconds{source}` · faltam gauge de Open-Meteo/maré e regra de alerta |
| F2-ARQ-02 | 2 | Arquiteto | Equilibrado | Contrato de predição (OpenAPI + ADR) antes de mudar backend, IA e front (decisão 2026-10-10) | em_andamento | | 2026-10-10 · Opus · `docs/adr/0007-contrato-de-predicao.md` (Arquiteto) |
| F2-FRONT-01 | 2 | Frontend | Equilibrado | Contrato de predição no front, passo 1 do ADR 0007 (zod tolerante, enum, UNKNOWN neutro único, campos novos opcionais) | concluido | sem commit (WIP do Lucas); patch `scratchpad/F2-FRONT-01.patch` copiado para `docs/revisao/fase-2/F2-FRONT-01.patch` | 2026-10-10 · subagente Sonnet · `docs/revisao/fase-2/F2-FRONT-01.md` · UNKNOWN 3→1 tratamento; mapeamento de risco 4→1 cópia; testes front 5→20; lint 0 erros, tsc limpo · fora: 6 `toLocale*` (F1-F2-19), UI de contributors, contraste da cor neutra |
| F2-BACK-08 | 2 | Backend | Equilibrado | Contrato de predição no backend, passo 2 do ADR 0007 (enum, null em falha, degradationReason, previsão no ciclo de coleta, GET sem gravar) | pendente | | espera F2-BACK-07 (mesmo arquivo) |
| F2-DADOS-07 | 2 | Dados | Equilibrado | Verificar fuso de ANA, APAC 24h e Cemaden por correlação cruzada com Open-Meteo (UTC) após a primeira carga | pendente | | depende de F2-BACK-01 + carga |
| F2-BACK-09 | 2 | Backend | Equilibrado | Primeira carga histórica (5 anos) pelo pipeline novo + medidas de volume e p95 do export | pendente | | depende de F2-BACK-01, F2-BACK-06 |
| F2-ARQ-03 | 2 | Arquiteto | Equilibrado | Integrar F2-BACK-04..07 e rodar a suíte completa | concluido | 7ccc0e8 (`integracao/fase-2`) | 2026-10-10 · Opus · 1 conflito trivial resolvido em `HistoricalDataServiceImpl` (serviceProvider removido + cliente com timeout); 49 testes, 0 falhas; cobertura 36,2% linhas / 23,9% ramos; export 74,6% |
| F2-ARQ-01 | 2 | Arquiteto | Rápido | Handoff da Fase 2 | pendente | | |
| F3-GEO-01 | 3 | Geo | Equilibrado | Processar rasters: unzip, atribuir CRS/nodata, VRT, recorte, COG; medir tamanhos | pendente | | precisa GDAL/rasterio (ausentes localmente → docker) |
| F3-GEO-02 | 3 | Geo+Dados+Backend | Equilibrado + checkpoint | ADR de onde armazenar o terreno | pendente | | |
| F3-GEO-03 | 3 | Geo | Equilibrado + checkpoint | ADR referência vertical MDT × NR da DHN | pendente | | |
| F3-GEO-04 | 3 | Geo | Equilibrado | Atributos por ponto e tabela `ponto_terreno` | pendente | | migração: aprovação |
| F3-GEO-05 | 3 | Geo | Equilibrado | Comparar cota MDT × altitude Open-Meteo | pendente | | |
| F3-GEO-06 | 3 | Geo | Equilibrado | `docs/TERRENO-PE3D.md` | pendente | | |
| F3-BACK-01 | 3 | Backend | Equilibrado | Endpoint de terreno e colunas no CSV de treino | pendente | | |
| F3-FRONT-01 | 3 | Frontend | Equilibrado | Camada de terreno no mapa (hipsometria, abaixo da cota de maré) | pendente | | |
| F3-ARQ-01 | 3 | Arquiteto | Rápido | Handoff da Fase 3 | pendente | | |
| F4-IA-01 | 4 | IA | Máximo | Formulação da tarefa, features e baselines (A.1–A.3) | pendente | | |
| F4-IA-02 | 4 | IA | Equilibrado | Pipeline de treino e experimentos (A.4–A.5) | pendente | | |
| F4-IA-03 | 4 | IA | Máximo | Interpretação, métricas, explicabilidade, limitações (A.6) | pendente | | |
| F4-IA-04 | 4 | IA | Equilibrado | Contrato de produção e degradação (A.7) | pendente | | |
| F4-IA-05 | 4 | IA | Equilibrado | `docs/MODELAGEM-IA.md` e exemplo real de dataset | pendente | | |
| F4-IA-06 | 4 | IA | Rápido | Tabela comparativa de algoritmos (xlsx) | pendente | | |
| F4-IA-07 | 4 | IA | Rápido | Deck pptx da disciplina | pendente | | |
| F4-FRONT-01 | 4 | Frontend | Equilibrado | Painel por ponto, legenda para Defesa Civil, estado UNKNOWN; screenshots antes/depois | pendente | | |
| F4-ARQ-01 | 4 | Arquiteto | Rápido | Handoff da Fase 4 | pendente | | |
| F5-QA-01 | 5 | QA | Rápido | `docs/FONTES-E-LICENCAS.md` | pendente | | |
| F5-QA-02 | 5 | QA | Rápido | Checklist de release e guia de contribuição; conferir origem de todo número | pendente | | |
| F5-REV-01 | 5 | Revisor | Máximo | `docs/REVISAO-CRITICA.md` | pendente | | |
| F5-ACAD-01 | 5 | Acadêmico | Máximo | `docs/PRONTIDAO-ACADEMICA.md` | pendente | | |
| F5-ARQ-01 | 5 | Arquiteto | Máximo | `docs/ARQUITETURA.md` ×3 e `docs/ROADMAP-REVISAO.md` | pendente | | |
| F5-ARQ-02 | 5 | Arquiteto | Máximo | `docs/RELATORIO-REVISAO.md` e PRs finais | pendente | | |

\* Nível: toda a Fase 0 rodou numa única sessão Opus (Máximo), acima do nível previsto. Motivo: o
prompt foi executado diretamente pelo orquestrador, sem subagentes. Registrado conforme a regra
"ninguém muda o próprio nível sem registrar". A partir da Fase 1, usar subagentes no nível da tabela.

## Bloqueios abertos
| ID | O que trava | O que destrava | Desde |
|---|---|---|---|
| (resolvido) | Limite semanal de uso derrubou 3 subagentes da F1 | reset do limite; agentes retomados | 2026-10-10 |
| F3-GEO-01 | GDAL/rasterio ausentes no host | rodar em container (ex.: `ghcr.io/osgeo/gdal`) — sem decisão necessária | 2026-10-10 |
| GitHub (todas as tarefas de PR) | `gh` ausente e sem MCP GitHub | instalar `gh` e autenticar, ou o Lucas abre os PRs a partir das branches | 2026-10-10 |

## Perguntas pendentes para o Lucas
| # | Pergunta | Desde | Impacto se não responder |
|---|---|---|---|
| — | Perguntas 1–7 respondidas em 2026-10-10 (ver Decisões). Mantidas abaixo, riscadas, para auditoria. | | |
| 1 | ~~Fuso: o agents.md manda converter para UTC−3 **antes de persistir**; o GEMINI.md e o código (`hibernate.jdbc.time_zone: UTC`) armazenam em UTC. Qual vale? (recomendação: UTC no banco, UTC−3 só na exibição)~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 2 | ~~Arquitetura alvo do backend: manter camadas atuais (model = entidade JPA) ou migrar gradualmente para domínio sem anotação de framework?~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 3 | ~~Schema: Flyway (`V1__baseline_schema.sql`) e `TimescaleSetup.sql` criam as mesmas hypertables. Posso tornar o Flyway a fonte única e o .sql um documento derivado?~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 4 | ~~Testes de integração: GEMINI.md manda H2, mas H2 não executa TimescaleDB/PostGIS. Aceita Testcontainers?~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 5 | ~~Front tem 14 arquivos modificados não commitados (flood.service, schemas, MapView…). É trabalho seu em andamento? Devo esperar o commit antes de qualquer PR no front?~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 6 | ~~Confirmar as definições ⚠ do glossário (ficha = 5 mm, soleira, lâmina, nível lá fora) contra a formulação da rede de Petri/Mercury~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 7 | ~~O repositório `projeto-mapi` (projeto-mapi-sensores, o antigo MAPI Edge) deve ser arquivado no GitHub ou receber README de "descontinuado"?~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 8 | ~~Contrato de predição: posso fechar o enum em `LOW/MEDIUM/HIGH/UNKNOWN` (remover `CRITICAL`/`EXTREME` do front), devolver probabilidade `null` em falha (hoje 0.0 aparece como "0%") e adicionar `modelVersion`? (recomendação: sim)~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 9 | ~~Rótulo: o CKAN só tem data e o código sobrescreveu a hora dos eventos 00:00 com o pico de chuva (rótulo circular). Posso (a) desativar o realinhamento e (b) reimportar o CKAN preservando a data original? A escolha entre rótulo diário e horizonte 3/6 h fica para a formulação da Fase 4~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 10 | ~~`DELETE /api/admin/ingestion/wipe-database` apaga tudo, inclusive usuários. Remover, ou restringir a um profile `dev` com confirmação? (recomendação: restringir ao `dev`)~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 11 | ~~A tabela `flood_predictions` (taxa de alerta, auditoria) entra nos resultados da dissertação? Se sim, o item 7 da matriz sobe para P0~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 12 | ~~O banco local não tem o histórico de 5 anos (só ~4 semanas de setembro, 0 eventos de alagamento). A carga histórica existe em outro banco/máquina? Se não, recomendo **não** rodar `historical-full-sync` agora: o pipeline atual grava fusos misturados e reescreve o rótulo. Carregar só depois dos itens 1–4 da matriz~~ **respondida 2026-10-10** | 2026-10-10 | — |
| 13 | ~~ADR 0007 (contrato de predição), itens marcados [aprovar]: `issuedAt`/`validUntil`/`horizonHours`; `degradationReason`; `lastValidIssuedAt`; catálogo de rótulos (`contributors`/`imputedFeatures`); `model_version="unversioned"` até a Fase 4; 422 da IA para dado insuficiente; hash fora da resposta; 200 em vez de 201 na degradação; regra de exibição de UNKNOWN; previsão feita pelo ciclo de coleta e GET sem gravar~~ **respondida 2026-10-10: aprovado** | 2026-10-10 | — |
| 14 | ~~ADR 0006: o que fazer com as ~4 semanas de dado de desenvolvimento? (a) descartar após o snapshot já tirado (recomendação), (b) reinterpretar por fonte, (c) manter como legado fora do dataset~~ **respondida 2026-10-10: (a) descartar após o snapshot** | 2026-10-10 | — |
| 15 | ~~ADR 0006: aprova `provenanceId` + `revision` em `SensorResponseDTO`/`WeatherResponseDTO`, timestamps ISO com `Z`, manifesto do export (`/manifest`, cabeçalho `X-Dataset-Id`) e endpoint admin de reprocessamento?~~ **respondida 2026-10-10: sim** | 2026-10-10 | — |
| 16 | ~~Rótulo: "inundação" (Cobrade) entra junto com "alagamento", ou só alagamento?~~ **respondida 2026-10-10: não — rótulo só alagamento** | 2026-10-10 | — |
| 17 | ~~Confirma que a escolha entre rótulo diário e horizonte 3/6 h fica para a formulação da Fase 4 (Máximo)?~~ **respondida 2026-10-10: sim** | 2026-10-10 | — |
| 18 | ~~Fuso de ANA, APAC meteorologia24h e Cemaden: verificar por correlação cruzada com Open-Meteo (UTC) após a primeira carga (nova tarefa F2-DADOS-07); até lá essas fontes ficam em quarentena. De acordo?~~ **respondida 2026-10-10: sim** | 2026-10-10 | — |
| 19 | ~~Aprova as migrações aditivas V3–V6 do ADR 0006 (nenhuma apaga dado; V8, que aposentaria `sensor_data`/`weather_data`, terá aprovação própria)?~~ **respondida 2026-10-10: sim** | 2026-10-10 | — |
| 20 | ADR 0005: aprova manter PostgreSQL + TimescaleDB, adicionar PostGIS (migração V7) e trocar a imagem para `timescale/timescaledb-ha:pg16.15-ts2.30.2` pelo procedimento a–f do ADR (dump com hash, volume novo, restauração, conferência contra o snapshot; o volume antigo fica até aprovação separada)? | 2026-10-10 | Fase 3 (PostGIS) e imagem dos Testcontainers |

## Medidas antes e depois
| Métrica | Antes | Depois | Quando | Onde foi medida |
|---|---|---|---|---|
| Arquivos de teste backend / LOC de teste | 8 / 668 | | 2026-10-10 | `find src/test -name '*.java'` |
| LOC Java main (sem testes) | 8 908 | | 2026-10-10 | soma por pacote, `wc -l` |
| DTOs que não são `record` | 25 de 36 | | 2026-10-10 | `grep -c record dto/*.java` |
| Bundle front MapPage JS (não gzip) | 1 215 244 B | | 2026-10-10 | `dist/assets` do build existente (data do build não verificada) |
| Ocorrências de `any` no front | 0 | | 2026-10-10 | `grep -rn ': any\|as any' src` |
| Pontos críticos cadastrados | 5 | | 2026-10-10 | `MapiServiceImpl.java:573-620` (F1-BACK-03) |
| Volume clima 5 anos × 5 pontos | ~219 mil linhas (~55 MB) | | 2026-10-10 | estimativa F1-BACK-03 §6, premissas a conferir no banco |
| Timeouts HTTP configurados nos clientes externos | 0 | todos os clientes (F2-BACK-07) | 2026-10-10 | grep `timeout` em src/main (F1-B2-01, conferido) |
| Testes backend (métodos) / em coleta, export, IA | 20 / 0 (o subagente contou 18 por leitura; o surefire executou 20 — vale o surefire) | | 2026-10-10 | F1-BACK-02 §7 |
| Tratamentos de fuso distintos nas mesmas colunas | 5 | | 2026-10-10 | F1-BACK-03 tabela de fuso |
| Features constantes (zeradas) na inferência da IA | 9 de 28 | | 2026-10-10 | F1-IA-02 (F1-I2-01) |
| Baselines implementados | 0 de 4 | | 2026-10-10 | F1-IA-02 (F1-I2-04) |
| Tratamentos visuais distintos de UNKNOWN no front | 3 | 1 (F2-FRONT-01, sem commit) | 2026-10-10 | F1-FRONT-02 (F1-F2-04) |
| `aria-label` / botões no front | 0 / 31 | | 2026-10-10 | F1-FRONT-02 (F1-F2-09) |
| Portas de banco/IA em 0.0.0.0 | 2 | 0 | 2026-10-10 | docker-compose.yml (F1-QA-02, 2fe06c6) |
| Cobertura de teste backend (linhas / ramos) | 26,1% / 17,6% | 36,2% / 23,9% (integracao/fase-2) | 2026-10-10 | JaCoCo 0.8.12, `docs/medidas/jacoco-2026-10-10.csv` |
| Cobertura do export (`service.export.impl`) | 0,6% | 74,6% (integracao/fase-2, 7ccc0e8) | 2026-10-10 | idem |
| Testes backend executados | 20 (0 falhas) | 49 (0 falhas), integracao/fase-2 | 2026-10-10 | surefire |
| Linhas no banco local (sensor / clima / eventos / predições) | 555 / 40 / 0 / 25 102 | | 2026-10-10 | snapshot `3ace3cde…` |
| p95 por endpoint (k6, ramping_load, cache) | 0,8–4,0 ms | | 2026-10-10 | `docs/medidas/LINHA-DE-BASE-2026-10-10.md` |
| p95 export CSV 30 d (banco quase vazio) | 143,1 ms (n=44) | | 2026-10-10 | idem, rodada 2 — não representativo |
| Requisições do heavy_export no k6 | 0 (bug de campo) | 44 | 2026-10-10 | idem, rodadas 1 e 2 |

## Divergências PROGRESSO × código (o código vence)
| Data | O que se afirmava | O que o código mostra | Correção |
|---|---|---|---|
| 2026-10-10 | Achado F0-A03: schema duplicado entre Flyway e `TimescaleSetup.sql` (e o prompt da revisão dizia que as hypertables vinham do `TimescaleSetup.sql`) | `TimescaleSetup.sql` já tem cabeçalho "SUPERADO" e não é montado pelo compose; o Flyway já era a fonte real | achado rebaixado a "documentação desatualizada"; resolvido por ADR 0003 + README |
| 2026-10-10 | GEMINI.md e `application.yml` sugeriam persistência em UTC | colunas `TIMESTAMP WITHOUT TIME ZONE`, 0 usos de `ZoneId`, Open-Meteo com `timezone=auto` → histórico gravado em hora local sem fuso | registrado no ADR 0001; migração na Fase 2 |
