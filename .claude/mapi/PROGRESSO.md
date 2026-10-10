# Progresso da revisão multiagente do MAPi

Última execução: 2026-10-10 · Fase atual: 1 (Fase 0 concluída) · Próxima tarefa: F1-BACK-02

> Local: `projeto-mapi-api/.claude/mapi/PROGRESSO.md`. A pasta-mãe `MAPI/` não é repositório git;
> o estado oficial fica no repositório orquestrador para ser versionado. Docs transversais
> (glossário, ADRs, handoffs) também ficam em `projeto-mapi-api/docs/`.

## Decisões tomadas (não reabrir sem motivo novo)
| Data | Decisão | ADR | Quem decidiu |
|---|---|---|---|
| 2026-10-10 | Estado da revisão, glossário, ADRs e handoffs ficam no projeto-mapi-api (pasta-mãe não é git) | — (operacional) | Arquiteto (F0) — Lucas pode vetar |
| 2026-10-10 | As 5 skills de projeto ficam em `projeto-mapi-api/.claude/skills/`; cópia para os outros repos só depois de aprovadas | — (operacional) | Arquiteto (F0) |
| 2026-10-10 | Skills descrevem a arquitetura **como está** (camadas do agents.md) e marcam o alvo ports-and-adapters como "não aprovado" até ADR | pendente (ver pergunta 2) | Arquiteto (F0) |

## Tarefas
| ID | Fase | Agente | Nível | Tarefa | Status | PR/commit | Resultado em uma linha |
|---|---|---|---|---|---|---|---|
| F0-ARQ-01 | 0 | Arquiteto | Equilibrado* | Criar as 5 skills de projeto com skill-creator | concluido | branch `revisao/fase-0` | 2026-10-10 · Opus (sessão única) · `.claude/skills/mapi-*/SKILL.md` ×5 · derivadas de agents.md/GEMINI.md, com tabela de divergências convenção×código; sem evals (ver F0-ARQ-07) |
| F0-ARQ-02 | 0 | Arquiteto | Rápido* | Glossário único | concluido | branch `revisao/fase-0` | 2026-10-10 · `docs/GLOSSARIO.md` v0 · 4 termos marcados ⚠ (ficha, soleira, lâmina, nível lá fora) aguardam confirmação contra o modelo Mercury |
| F0-ARQ-03 | 0 | Arquiteto | Rápido* | PROGRESSO.md inicial e `.claude/CONTEXT.md` | concluido | branch `revisao/fase-0` | 2026-10-10 · este arquivo + CONTEXT.md |
| F0-ARQ-04 | 0 | Arquiteto | Rápido* | Inventário barato dos 3 repositórios (árvore, LOC, deps, grep) | concluido | branch `revisao/fase-0` | 2026-10-10 · números em `docs/handoff/fase-0.json` · cobre F1-BACK-01, F1-FRONT-01, F1-IA-01 |
| F0-ARQ-05 | 0 | Arquiteto | Rápido* | Carregar MCPs e registrar o que ficou sem verificação | concluido | — | 2026-10-10 · nenhum MCP essencial disponível; substitutos CLI listados em CONTEXT.md §MCPs |
| F0-ARQ-06 | 0 | Arquiteto | Rápido* | Handoff da Fase 0 | concluido | branch `revisao/fase-0` | 2026-10-10 · `docs/HANDOFF-fase-0.md` + `docs/handoff/fase-0.json` |
| F0-ARQ-07 | 0 | Arquiteto | Equilibrado | Evals das skills (2–3 prompts por skill, com e sem skill) | pendente | | adiado: custo alto, baixo risco agora; fazer antes da Fase 2 |
| F1-BACK-01 | 1 | Backend | Rápido | Inventário barato do backend | concluido | — | coberto por F0-ARQ-04: 138 arquivos / ~8,9 mil LOC main; 8 testes / 668 LOC |
| F1-BACK-02 | 1 | Backend | Equilibrado | Leitura dirigida: aderência à arquitetura, jobs/resiliência (APAC 6 h fora), @Transactional, taskExecutor/Hikari, segurança (JWT, CORS, Actuator) | pendente | | |
| F1-BACK-03 | 1 | Backend | Equilibrado | Revisão a fundo da ingestão histórica (backfill vs. pipeline, proveniência, lacunas, reamostragem, volume, raio do repair-stations) | pendente | | |
| F1-BACK-04 | 1 | Backend | Rápido | Snapshot do OpenAPI (`/v3/api-docs`) versionado em `docs/openapi/` | pendente | | exige subir a API (docker) |
| F1-BACK-05 | 1 | Backend | Equilibrado | Medidas "antes": cobertura (JaCoCo), p95 por endpoint (k6), N+1 (log Hibernate) | pendente | | sem `mvn` local: usar `./mvnw` |
| F1-FRONT-01 | 1 | Frontend | Rápido | Inventário barato do front | concluido | — | coberto por F0-ARQ-04: bundle MapPage 1,22 MB, 0 `any`, MapView 1 106 linhas |
| F1-FRONT-02 | 1 | Frontend | Equilibrado | Leitura dirigida: estado servidor×UI, re-render do mapa, a11y, 390 px, estado UNKNOWN/erro | pendente | | só leitura até o WIP não commitado do front ser resolvido (pergunta 5) |
| F1-IA-01 | 1 | IA | Rápido | Inventário barato da IA | concluido | — | coberto por F0-ARQ-04: 847 LOC Python, 3 artefatos sem metadata |
| F1-IA-02 | 1 | IA | Equilibrado | Leitura dirigida do módulo IA e do contrato real de degradação UNKNOWN (backend↔IA↔front) | pendente | | |
| F1-QA-01 | 1 | QA | Rápido | Corrigir READMEs do AI/Front (MAPI Edge, MQTT, "quatro componentes") e varrer o resto | pendente | | achado já confirmado: AI README L10, L30–33, L104; Front README L95 |
| F1-QA-02 | 1 | QA | Rápido | Risco Grafana (credencial padrão) e Prometheus sem auth; documentar "só uso local" | pendente | | |
| F1-QA-03 | 1 | QA | Equilibrado | Blindar armadilha do k6 no compose (entrypoint em vez de command) | pendente | | |
| F1-ARQ-01 | 1 | Arquiteto | Equilibrado | Relatório de consistência entre repos (campos, unidades, fuso, enum de risco, nulos) | pendente | | |
| F1-ARQ-02 | 1 | Arquiteto | Equilibrado + checkpoint Máximo | Diagnóstico e matriz de prioridade | pendente | | |
| F1-ARQ-03 | 1 | Arquiteto | Equilibrado | ADRs das divergências convenção×código (fuso, schema Flyway×TimescaleSetup, H2×Testcontainers, records/MapStruct, domínio sem framework) | pendente | | dependem das perguntas 1–4 |
| F1-ARQ-04 | 1 | Arquiteto | Rápido | Handoff da Fase 1 | pendente | | |
| F2-DADOS-01 | 2 | Dados | Equilibrado | Benchmark e evidência sobre o banco (tamanho das hypertables, compressão, EXPLAIN ANALYZE, volume projetado) | pendente | | |
| F2-DADOS-02 | 2 | Dados | Equilibrado + checkpoint | ADR PostgreSQL+TimescaleDB+PostGIS × MySQL | pendente | | |
| F2-DADOS-03 | 2 | Dados | Rápido | Planilha comparativa do banco (xlsx) | pendente | | |
| F2-DADOS-04 | 2 | Dados | Equilibrado | `docs/MODELO-DE-DADOS.md` com diagrama | pendente | | |
| F2-DADOS-05 | 2 | Dados+Backend | Equilibrado + checkpoint | ADR do pipeline de ingestão (staging → validação → consolidada, idempotência, proveniência) | pendente | | |
| F2-DADOS-06 | 2 | Dados | Equilibrado | Políticas de retenção e compressão | pendente | | |
| F2-BACK-01 | 2 | Backend | Equilibrado | Implementar ingestão aprovada (migração incremental) | pendente | | exige aprovação de migração |
| F2-BACK-02 | 2 | Backend | Equilibrado | Identificador de proveniência nas respostas de dado | pendente | | muda contrato: aprovação |
| F2-BACK-03 | 2 | Backend | Equilibrado | `docs/INGESTAO.md` e `docs/API.md` | pendente | | |
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
| F1-FRONT-02 (parcial) | 14 arquivos modificados e não commitados no projeto-mapi-front | decisão do Lucas (pergunta 5) | 2026-10-10 |
| F3-GEO-01 | GDAL/rasterio ausentes no host | rodar em container (ex.: `ghcr.io/osgeo/gdal`) — sem decisão necessária | 2026-10-10 |
| GitHub (todas as tarefas de PR) | `gh` ausente e sem MCP GitHub | instalar `gh` e autenticar, ou o Lucas abre os PRs a partir das branches | 2026-10-10 |

## Perguntas pendentes para o Lucas
| # | Pergunta | Desde | Impacto se não responder |
|---|---|---|---|
| 1 | Fuso: o agents.md manda converter para UTC−3 **antes de persistir**; o GEMINI.md e o código (`hibernate.jdbc.time_zone: UTC`) armazenam em UTC. Qual vale? (recomendação: UTC no banco, UTC−3 só na exibição) | 2026-10-10 | ADR de fuso e todo o pipeline de ingestão/features ficam sem base |
| 2 | Arquitetura alvo do backend: manter camadas atuais (model = entidade JPA) ou migrar gradualmente para domínio sem anotação de framework? | 2026-10-10 | ArchUnit só congela a regra atual; skill java fica em "como está" |
| 3 | Schema: Flyway (`V1__baseline_schema.sql`) e `TimescaleSetup.sql` criam as mesmas hypertables. Posso tornar o Flyway a fonte única e o .sql um documento derivado? | 2026-10-10 | risco de divergência de schema; agents.md §2.4 fica desatualizado |
| 4 | Testes de integração: GEMINI.md manda H2, mas H2 não executa TimescaleDB/PostGIS. Aceita Testcontainers? | 2026-10-10 | ingestão e queries nativas continuam sem teste real |
| 5 | Front tem 14 arquivos modificados não commitados (flood.service, schemas, MapView…). É trabalho seu em andamento? Devo esperar o commit antes de qualquer PR no front? | 2026-10-10 | Fase 1 do front fica só leitura |
| 6 | Confirmar as definições ⚠ do glossário (ficha = 5 mm, soleira, lâmina, nível lá fora) contra a formulação da rede de Petri/Mercury | 2026-10-10 | ponte IA↔modelo estocástico (Agente 8) fica sem base |
| 7 | O repositório `projeto-mapi` (projeto-mapi-sensores, o antigo MAPI Edge) deve ser arquivado no GitHub ou receber README de "descontinuado"? | 2026-10-10 | READMEs continuam apontando para ele |

## Medidas antes e depois
| Métrica | Antes | Depois | Quando | Onde foi medida |
|---|---|---|---|---|
| Arquivos de teste backend / LOC de teste | 8 / 668 | | 2026-10-10 | `find src/test -name '*.java'` |
| LOC Java main (sem testes) | 8 908 | | 2026-10-10 | soma por pacote, `wc -l` |
| DTOs que não são `record` | 25 de 36 | | 2026-10-10 | `grep -c record dto/*.java` |
| Bundle front MapPage JS (não gzip) | 1 215 244 B | | 2026-10-10 | `dist/assets` do build existente (data do build não verificada) |
| Ocorrências de `any` no front | 0 | | 2026-10-10 | `grep -rn ': any\|as any' src` |
| Cobertura de teste backend | não medida | | | F1-BACK-05 |
| p95 por endpoint | não medido | | | F1-BACK-05 (k6) |
