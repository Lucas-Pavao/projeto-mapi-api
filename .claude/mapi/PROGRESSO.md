# Progresso da revisão multiagente do MAPi

Última execução: 2026-10-10 (2ª) · Fase atual: 1 · Próxima tarefa: F1-BACK-02

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
| F1-BACK-02 | 1 | Backend | Equilibrado | Leitura dirigida: aderência à arquitetura, jobs/resiliência (APAC 6 h fora), @Transactional, taskExecutor/Hikari, segurança (JWT, CORS, Actuator) | em_andamento |  | 2026-10-10 · subagente Sonnet · interrompido pelo limite semanal de uso; retomado com o contexto preservado após o reset |
| F1-BACK-03 | 1 | Backend | Equilibrado | Revisão a fundo da ingestão histórica (backfill vs. pipeline, proveniência, lacunas, reamostragem, volume, raio do repair-stations) | em_andamento |  | 2026-10-10 · subagente Sonnet · interrompido pelo limite semanal de uso; retomado com o contexto preservado após o reset |
| F1-BACK-04 | 1 | Backend | Rápido | Snapshot do OpenAPI (`/v3/api-docs`) versionado em `docs/openapi/` | pendente | | exige subir a API (docker) |
| F1-BACK-05 | 1 | Backend | Equilibrado | Medidas "antes": cobertura (JaCoCo), p95 por endpoint (k6), N+1 (log Hibernate) | pendente | | sem `mvn` local: usar `./mvnw` |
| F1-FRONT-01 | 1 | Frontend | Rápido | Inventário barato do front | concluido | — | coberto por F0-ARQ-04: bundle MapPage 1,22 MB, 0 `any`, MapView 1 106 linhas |
| F1-FRONT-02 | 1 | Frontend | Equilibrado | Leitura dirigida: estado servidor×UI, re-render do mapa, a11y, 390 px, estado UNKNOWN/erro | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet · `docs/revisao/fase-1/F1-FRONT-02.md` · 21 achados, 2 críticos: UNKNOWN exibido vermelho/cinza/verde em 3 componentes; cada clique dispara POST de predição (grava auditoria) + N chamadas por sensor |
| F1-IA-01 | 1 | IA | Rápido | Inventário barato da IA | concluido | — | coberto por F0-ARQ-04: 847 LOC Python, 3 artefatos sem metadata |
| F1-IA-02 | 1 | IA | Equilibrado | Leitura dirigida do módulo IA e do contrato real de degradação UNKNOWN (backend↔IA↔front) | concluido | branch `revisao/fase-0` (relatório) | 2026-10-10 · subagente Sonnet (parou no limite de uso depois de gravar o relatório completo) · `docs/revisao/fase-1/F1-IA-02.md` · 17 achados, 6 críticos [DISSERTACAO]: 9/28 features zeradas na inferência, H = 0 (nowcast), scaler antes do split, 0/4 baselines, artefato sem metadata, fallback silencioso p/ dataset sintético |
| F1-QA-01 | 1 | QA | Rápido | Corrigir READMEs do AI/Front (MAPI Edge, MQTT, "quatro componentes") e varrer o resto | concluido | AI bd15c50 (branch `docs/remove-mapi-edge`); front: edição não commitada no README (WIP do Lucas) | 2026-10-10 · Opus · AI: diagrama sem Edge, "três componentes", link do compose → main; front: o WIP do Lucas já corrigia quase tudo, faltava L95 + link (feito). Achado novo: README do front cita enum `CRITICAL`, que o backend não tem → F1-ARQ-01 |
| F1-QA-02 | 1 | QA | Rápido | Risco Grafana (credencial padrão) e Prometheus sem auth; documentar "só uso local" | em_andamento |  | 2026-10-10 · Opus · Grafana e Prometheus já ligados só em 127.0.0.1 (compose L103, L129); senha do Grafana sobrescrevível por env. Achado maior: Postgres em 0.0.0.0:5433 com senha fixa `mapi123` (compose L8–10). Aguarda F1-BACK-02 (JWT/Actuator) para um PR único de segurança |
| F1-QA-03 | 1 | QA | Equilibrado | Blindar armadilha do k6 no compose (entrypoint em vez de command) | concluido | c21a38c (branch `fix/k6-remote-write-env`) | 2026-10-10 · Opus · `K6_OUT=experimental-prometheus-rw` no environment, `--out` removido do command; testado em grafana/k6:0.54.0 com argumento arbitrário → `output: Prometheus remote write`; `docker compose config` ok |
| F1-ARQ-01 | 1 | Arquiteto | Equilibrado | Relatório de consistência entre repos (campos, unidades, fuso, enum de risco, nulos) | pendente | | |
| F1-ARQ-02 | 1 | Arquiteto | Equilibrado + checkpoint Máximo | Diagnóstico e matriz de prioridade | pendente | | |
| F1-ARQ-03 | 1 | Arquiteto | Equilibrado | ADRs das divergências convenção×código (fuso, schema Flyway×TimescaleSetup, H2×Testcontainers, records/MapStruct, domínio sem framework) | em_andamento | ver linha F1-ARQ-03 no histórico | 2026-10-10 · Opus · ADRs 0001–0004 aprovados; agents.md §2.4/§3.1/§6, GEMINI.md, README e skills ajustados · falta: records/MapStruct (desejável, sem ADR ainda) |
| F1-QA-04 | 1 | QA | Rápido | Aviso de descontinuado no projeto-mapi (MAPI Edge) | concluido | projeto-mapi d86a996 (branch `docs/descontinuado`) | 2026-10-10 · Opus · bloco DESCONTINUADO no topo do README |
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

## Divergências PROGRESSO × código (o código vence)
| Data | O que se afirmava | O que o código mostra | Correção |
|---|---|---|---|
| 2026-10-10 | Achado F0-A03: schema duplicado entre Flyway e `TimescaleSetup.sql` (e o prompt da revisão dizia que as hypertables vinham do `TimescaleSetup.sql`) | `TimescaleSetup.sql` já tem cabeçalho "SUPERADO" e não é montado pelo compose; o Flyway já era a fonte real | achado rebaixado a "documentação desatualizada"; resolvido por ADR 0003 + README |
| 2026-10-10 | GEMINI.md e `application.yml` sugeriam persistência em UTC | colunas `TIMESTAMP WITHOUT TIME ZONE`, 0 usos de `ZoneId`, Open-Meteo com `timezone=auto` → histórico gravado em hora local sem fuso | registrado no ADR 0001; migração na Fase 2 |
