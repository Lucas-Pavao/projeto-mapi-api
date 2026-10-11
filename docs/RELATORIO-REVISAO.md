# Relatório final da revisão multiagente do MAPi

- Data: 2026-10-11 · Tarefa: F5-ARQ-02 (parte documental; os PRs finais ficam para o item 9 do `docs/ROADMAP-REVISAO.md`)
- Autor: Arquiteto (nível Máximo). Só documentação: nenhum código, banco ou commit foi alterado nesta tarefa.
- Fonte de verdade: `.claude/mapi/PROGRESSO.md` (abaixo, "PROGRESSO"), handoffs das Fases 0–3, ADRs 0001–0009 com emendas,
  `docs/REVISAO-CRITICA.md` (RC-xx), `docs/PRONTIDAO-ACADEMICA.md` (L-xx, T-xx), `docs/MATRIZ-PRIORIDADE-FASE-1.md`,
  `docs/RESULTADOS-AVALIACAO-F1.md`, `docs/INTERPRETACAO-F1.md`, `docs/PRE-REGISTRO-AVALIACAO.md`,
  `docs/PRE-REGISTRO-ANALISE-2.md`.
- Documentos irmãos: `docs/arquitetura/{api,ai,front}.md`, `docs/ROADMAP-REVISAO.md`, `docs/PENDENCIAS-DE-INFORMACAO.md`.
- A rede de Petri está fora do escopo atual (decisão do autor, 2026-10-11).

## 1. Resumo executivo

1. A revisão rodou de 2026-10-10 a 2026-10-11 nos 3 repositórios, em 5 fases, com 114 tarefas no PROGRESSO.
2. Fases 0–1: 98 achados (21 críticos na Fase 1); a revisão crítica fez 25 contestações (5 críticas).
3. Corrigidos os achados que invalidariam um resultado: rótulo circular, imputação no export, 5 fusos misturados (ADRs 0001, 0006).
4. Dados de 5 anos por pipeline auditável: 1,78 M linhas, maré 2021–2026, terreno em 4 de 5 pontos (F2-BACK-09/21, F3-GEO-01).
5. Fato central: **18 dias com alagamento no Recife em 5 anos**, todos sem hora (F2-BACK-18). Daí o desenho F1.
6. 1ª análise: H1 **inconclusiva e degenerada** (ΔAUC +0,113 [−0,010; +0,252]); H2 não testada (RESULTADOS §4–§7).
7. Causa: a reanálise chega a 16,7 mm/h contra 50,3 mm/h da IDF; o balde nunca transbordou (RESULTADOS §5).
8. 2ª análise (pluviômetro ANA, `2b9352c`): **não testada**, portão C2d reprovado; desvio pós-hoc recusado (emenda).
9. `avaliacao/reproduzir.sh` refaz escores e métricas byte a byte (F5-ACAD-02).
10. Veredito: sustenta a dissertação como dados auditáveis + resultado inconclusivo com causa medida (opção G), não como modelo validado (PRONTIDAO).
11. Abertos: dumps com cópia única, Δ do ADR 0009 sem fonte, 6 pedidos não enviados, terreno no limite de 3 s.

## 2. Achados por severidade

"Diss." = afeta a dissertação. Os achados de origem estão em `docs/revisao/fase-1/`, `docs/handoff/fase-{0,1}.json`,
`docs/MATRIZ-PRIORIDADE-FASE-1.md` (itens 0–15) e `docs/REVISAO-CRITICA.md`.

### 2.1 Críticos

| Achado | Origem | O que foi feito | O que ficou | Diss. |
|---|---|---|---|---|
| Rótulo circular: o realinhamento sobrescrevia a hora do evento com o pico de chuva | MATRIZ item 1; F1-BACK-03 | Realinhamento removido; `align-events` responde 410; CKAN reimportado com a data original e precisão declarada | — | sim |
| Fuso: 5 tratamentos diferentes nas mesmas colunas; conflito agents.md × código | F0-A02; MATRIZ item 2 | UTC na persistência (ADR 0001), `source_tz` por fonte, quarentena para fonte não verificada; Cemaden e ANA verificadas UTC−3 (V13, V14) | APAC 24h em quarentena; possível deslocamento de ~1 h do lado do Open-Meteo (RC-16; F2-DADOS-10) | sim |
| ANA fora do export (sem lat/lon) | MATRIZ item 3 | ANA no pipeline, fuso verificado, nível em m; chuva horária em `observation_hourly_v` e coluna `ana_precipitation` no export (V15; F2-DADOS-11) | Plausibilidade: critério C2d reprovado (781 mm contra piso `[sem fonte]` de 800 mm) | sim |
| Ingestão sem idempotência nem proveniência | MATRIZ item 4 | Pipeline staging → validação → consolidada com `provenanceId`, `observation_revision`, raio gravado (ADR 0006, V3–V6) | Legado ainda alimenta o painel (RC-14) | sim |
| Export imputava constantes e não tinha manifesto | MATRIZ item 5 | `<var>_missing`, manifesto com `X-Dataset-Id`, runs, hashes e `codeVersion` do build | — | sim |
| ML inválido: H = 0, 9/28 features zeradas, 0/4 baselines, scaler antes do split | MATRIZ item 6; F1-IA-02 | Formulação supervisionada abandonada (F1); avaliação pré-registrada com B2 e B3; inferência sem imputação (422) | Classificador legado continua no painel, como "não validado" | sim |
| Artefatos de modelo sem metadata | F0-A01 | `model_version = "unversioned"`, `modelValidationStatus = NOT_VALIDATED`, selo no front; `flood_predictions` fora dos resultados | Os 3 artefatos seguem sem metadata | sim |
| 0 timeouts HTTP; falha da APAC virava "sucesso 0/0" | F1-BACK-02 | Timeouts em todos os clientes; fallbacks removidos; gauge de idade do dado | Gauge de Open-Meteo e maré; regra de alerta | não |
| `JWT_SECRET` padrão público no compose | F1-BACK-02 | Documentado em `SEGURANCA-LOCAL.md`; portas só em 127.0.0.1 | Valor padrão continua no compose (linha 61) | não |
| `UNKNOWN` em 3 cores; POST de predição a cada clique | F1-FRONT-02 | 1 tratamento neutro; previsão no ciclo de coleta, GET sem gravar | — | não |
| RC-01: única cópia dos dados num volume Docker | REVISAO-CRITICA | Dump pós-carga `24aa31ce…` com SHA-256 | Cópia fora da máquina bloqueada (pergunta 31); backfill 2021-02→09 em nenhum dump | sim |
| RC-02: nenhuma branch no remoto | REVISAO-CRITICA | Todas as branches no GitHub (2026-10-11); fusão em `main` por PRs em curso (orquestrador, 2026-10-11) | Remedir as medidas no commit de `main` | sim |
| RC-03: de onde vem o `riskLevel`? | REVISAO-CRITICA | Emenda 1 do ADR 0007 (selo), `codeVersion` real, ArchUnit; painel rastreável (F4-FRONT-01c) | Screenshot com a IA real (F4-FRONT-01b §3) | sim |
| RC-04: escassez de rótulo medida depois de ~80 commits | REVISAO-CRITICA | Texto do handoff corrigido; lição registrada (§7) | — | sim |
| RC-05: recomendação F1 com motivo não epistêmico e sem a opção G | REVISAO-CRITICA | Opção G incluída no MEMO; autor escolheu F1; a leitura final usa G | Confirmação das decisões de método (RC-25) | sim |

### 2.2 Importantes

| Achado | Origem | O que foi feito | O que ficou | Diss. |
|---|---|---|---|---|
| Contrato de degradação ambíguo | MATRIZ item 7 | ADR 0007 implementado nos 3 repositórios; V8 de auditoria | — | não |
| Testes em H2, sem Testcontainers nem ArchUnit | F0-A05; MATRIZ item 9; RC-13 | Testcontainers com TimescaleDB; 240 testes; ArchUnit com 4 regras e 0 violações | H2 no `pom.xml` | não |
| `wipe-database` apaga tudo; registro aberto ao export | MATRIZ item 10 | `wipe-database` só no perfil `dev` | Registro aberto; admins órfãos (RC-20) | não |
| Transações abertas durante HTTP; pools | MATRIZ item 11 | Só o `LazyInitializationException` do ciclo de predição (F2-BACK-19) | Restante não tratado | não |
| Desempenho do export | MATRIZ item 12 | Maré do pipeline e índice por export: 2,47 s em 5 anos | Remedir em base comparável (RC-07) | não |
| Front para a Defesa Civil | MATRIZ item 13 | Erro/vazio real, legenda, a11y, 390 px, selo, painel rastreável | Contraste < 4,5:1 (RC-19); celular real | não |
| Imagem do banco com tag flutuante | F0-A04 | Imagem fixa por tag e digest (ADR 0005) | — | sim |
| Raio de vínculo sem metadado | F0-A06 | `linkage_config` gravado; raio 500 m; sensibilidade 500 m–3 km medida | — | sim |
| RC-06: MEMO com 14, 15 e 18 dias em lugares diferentes | REVISAO-CRITICA | Pré-registro e resultados usam 18 (14 + 4) | Adendo ao MEMO §1 não registrado no PROGRESSO `[a verificar]` | sim |
| RC-07, RC-08, RC-09: antes e depois que comparam coisas diferentes (export, cobertura, volume) | REVISAO-CRITICA | Ressalvas escritas nas tabelas (§5) | Remedir (ROADMAP item 10) | não |
| RC-10: PostGIS sem nenhuma chamada `ST_*` | REVISAO-CRITICA | — | Adendo ao ADR 0005 | não |
| RC-11: checkpoints de uma só família de modelo, pareceres não guardados | REVISAO-CRITICA | Declarado nos próprios documentos de revisão | Pareceres ou declaração na metodologia; leitura do orientador | sim |
| RC-14: engenharia grande para 18 dias; legado convive | REVISAO-CRITICA | Ingestão parou no que o desenho F1 exige | Plano de aposentadoria do legado | sim |
| RC-15: liberação do Cemaden além do texto do critério | REVISAO-CRITICA | — | Esclarecer ou reverter por REVALIDATE | sim |
| RC-16: ANA com argmax −4 h | REVISAO-CRITICA | Novo teste pré-fixado com dados novos: UTC−3 (F2-DADOS-10) | Efeito do viés de ~1 h do Open-Meteo no máximo diário | sim |
| RC-17: Nominatim não guardado; "STREET" é rua sem número | REVISAO-CRITICA | — | Guardar respostas; renomear o nível | sim |
| RC-18: área contribuinte como número pontual | REVISAO-CRITICA | API e painel mostram a faixa do snap (F4-FRONT-01c) | Conciliar 573 046 × 573 223 m² | sim |
| RC-20: ≥ 5 admins criados por agentes | REVISAO-CRITICA | Admin local com senha fora do git (F2-OPS-01) | Remover os órfãos | não |
| RC-25: decisões de método tomadas sob a autorização durável | REVISAO-CRITICA | — | Confirmação do Lucas e do orientador | sim |

### 2.3 Desejáveis

| Achado | Origem | O que foi feito | O que ficou | Diss. |
|---|---|---|---|---|
| DTOs fora do padrão `record`; sem MapStruct | F0-A07; MATRIZ item 14 | DTOs novos como `record` | Os antigos (25 de 36 no início) | não |
| `MapiServiceImpl` com 5 responsabilidades | MATRIZ item 14 | — | 775 → 798 linhas (medido em F5-ARQ-01) | não |
| Limpeza do front (`alert()`, tipos, bundle, polling) | MATRIZ item 15 | `alert()` 9 → 0 | Tipos do OpenAPI, bundle de 1,2 MB, polling sem abort | não |
| READMEs com MAPI Edge e MQTT | F0-A08 | Corrigidos; `projeto-mapi` marcado como descontinuado | — | não |
| RC-12: agregação do critério de 3 s não fixada | REVISAO-CRITICA | Declarado no ADR 0008 | Fixar antes da medição em celular | não |
| RC-19: contraste 3,19:1 abaixo do AA para texto | REVISAO-CRITICA | — | Meta 4,5:1 | não |
| RC-21: termo da DHN pendente; Open-Meteo só não comercial | REVISAO-CRITICA | Pedido 01 pergunta sobre reutilização | Resposta da DHN | sim |
| RC-22: método de contagem de LOC não registrado | REVISAO-CRITICA | Comando registrado nos documentos de arquitetura | — | não |
| RC-23: evals das skills vencidas | REVISAO-CRITICA | — | F0-ARQ-07 | não |
| RC-24: contagem de rótulo tratada como risco, não ação | REVISAO-CRITICA | MODELAGEM-IA reescrita para F1 (F4-IA-10) | — | sim |

### 2.4 Os que afetam a dissertação, por tema

**Validação.**
- Rótulo circular corrigido. O rótulo agora mede "dia com registro público de alagamento", e o negativo é "dia sem
  registro", não "dia sem alagamento" (PRE-REGISTRO-AVALIACAO §2).
- São 18 dias positivos (14 em 2023–2024, 13 episódios). Eles já foram gastos pela 1ª análise: qualquer avaliação nova
  sobre eles é exploratória (INTERPRETACAO §6.2).
- Parâmetros sem fonte (`f`, soleira, curva cota-volume) entram só como faixa. A faixa inteira deu a mesma categoria
  (RESULTADOS §6).
- Pendentes: confirmação das escolhas do pré-registro (RC-25) e leitura do orientador (RC-11).

**Métricas.**
- Errata: eram 24 variantes de M1, não 30 (PROGRESSO, "Divergências"). A conclusão não mudou.
- A cobertura de 53,5% registrada em `a472922` não se reproduziu; vale 51,0% (PROGRESSO, "Divergências").
- O export, a cobertura e o volume precisam de nova medida comparável antes de entrar no texto (RC-07/08/09).

**Rastreabilidade.**
- Manifesto com `codeVersion` real, `datasetId` e runs (F5-REV-02a). Painel com grandeza e fonte de cada número e maré
  sobre o NR (F4-FRONT-01c). Selo "não validado" (ADR 0007, emenda 1).
- Pendentes: respostas do Nominatim (RC-17), pareceres dos checkpoints (RC-11) e o classificador do painel sem dataset
  de treino registrado (RC-03).

**Reprodutibilidade.**
- `reproduzir.sh congelado` refaz escores (`4615d416…`) e resultado (`dcf020cb…`) byte a byte. A extração versionada
  reproduz a chuva (`ef6772b7…`). O terreno se reproduz em duas execuções com o mesmo SHA (`114ddd32…`).
- Pendentes: cópia fora da máquina, dump pós-backfill, restauração em banco vazio não testada, ambiente Python sem
  hashes e termos de uso que impedem publicar maré derivada e terreno (PRONTIDAO L2, L3, L15; FONTES, "Pendências").

## 3. Decisões tomadas

Lista completa e datada em PROGRESSO, "Decisões tomadas". Aqui, as que mudaram a arquitetura ou a pesquisa.

| Data | Decisão | ADR / documento | Quem decidiu |
|---|---|---|---|
| 2026-10-10 | UTC na persistência; America/Recife só na exibição | 0001 | Lucas |
| 2026-10-10 | Manter as camadas atuais e congelar com ArchUnit | 0002 | Lucas |
| 2026-10-10 | Flyway como fonte única do schema | 0003 | Lucas |
| 2026-10-10 | Testcontainers nos testes de integração; H2 sai | 0004 | Lucas |
| 2026-10-10 | PostgreSQL 16 + TimescaleDB + PostGIS, imagem fixa | 0005 | Lucas |
| 2026-10-10 | Pipeline staging → validação → consolidada; dado de desenvolvimento descartado após snapshot; rótulo só "alagamento" | 0006 | Lucas |
| 2026-10-10 | Contrato de predição (enum fechado, `null` em falha, GET sem gravar), ordem front → backend → IA | 0007 | Lucas |
| 2026-10-10 | Terreno: atributos no banco, raster fora; C3 (tiles) depois que C1 reprovou no critério pré-fixado | 0008 | Lucas (C3 pelo plano de retorno do ADR) |
| 2026-10-10 | Só cotas relativas até um Δ de fonte primária; Δ nunca calibrado com eventos | 0009 | Lucas |
| 2026-10-10 | `flood_predictions` fora dos resultados da dissertação | — | Lucas |
| 2026-10-10 | Autorização durável: o Arquiteto decide dentro do escopo e registra; ações destrutivas são sinalizadas antes | — | Lucas |
| 2026-10-10 | Tarefa principal passa a ser diária (N_HOUR = 0 < 20) | MODELAGEM-IA | regra pré-registrada |
| 2026-10-10 | ANA mantida em quarentena: aplicar −3 h depois de ver o dado seria pós-hoc | 0006 | Arquiteto (autorizado) |
| 2026-10-11 | Desenho F1: modelo físico como principal, dia × cidade, regra de chuva com limiar externo como baseline | MEMO-ESCASSEZ-DE-ROTULO | Lucas |
| 2026-10-11 | Protocolo e parâmetros commitados antes de qualquer execução nos dias rotulados | PRE-REGISTRO-AVALIACAO | Arquiteto (autorizado) |
| 2026-10-11 | Limiar da regra de chuva pela IDF publicada (TR 2 anos, 60 min), não por estudo que sobrepõe os rótulos | PRE-REGISTRO-AVALIACAO | Arquiteto (autorizado) |
| 2026-10-11 | Rede de Petri fora do escopo: H3, H4, M2 e emulador retirados por emenda antes da junção | PRE-REGISTRO-AVALIACAO, emenda | Lucas |
| 2026-10-11 | Selo `modelValidationStatus` (NOT_VALIDATED por padrão) | 0007, emenda 1 | Arquiteto (autorizado) |
| 2026-10-11 | ANA sai da quarentena (UTC−3 por critério pré-fixado com dados novos); 2ª análise como análise posterior pré-registrada | 0006; PRE-REGISTRO-ANALISE-2 | Arquiteto (autorizado) / Lucas |
| 2026-10-11 | Pedidos institucionais não são enviados em nome do autor | — | Arquiteto, aceito pelo Lucas |
| 2026-10-11 | 2ª análise **não testada**: portão §2.1 reprovado (C2d); desvio pós-hoc não aceito | PRE-REGISTRO-ANALISE-2, emenda | Arquiteto (autorizado) |

## 4. Resultados científicos

### 4.1 1ª análise (confirmatória, pré-registrada em `77e41de`)

- **Conjunto:** 14 dias positivos de 2023–2024 (13 episódios), 689 negativos ("sem registro", menos ±1 dia em torno dos
  positivos); 4 positivos extras de 2021–2022 só em análise secundária (RESULTADOS §2).
- **Execução cega em duas etapas:** escores gerados sem rótulos e congelados (`4615d416…`) antes da junção; a junção
  rodou uma vez (RESULTADOS, cabeçalho e §1).
- **H1 (balde M1a × climatologia B2):** AUC 0,891 × 0,777; ΔAUC +0,113, IC 95% [−0,010; +0,252] → **inconclusiva**.
  Também **degenerada**: `S = 0` em todos os 14 positivos e alerta em 0 de 689 negativos. O AUC vem do desempate pela
  chuva horária máxima (RESULTADOS §4).
- **H2 (M1a × regra IDF B3):** **não testada** pela regra de parada. Descritivamente ΔAUC = 0 por construção (RESULTADOS §5).
- **Robustez:** as 24 variantes caem na mesma categoria (RESULTADOS §6).
- **Causa medida:** a reanálise Open-Meteo chega a 16,7 mm/h, 33% do limiar IDF de 50,3 mm/h (RESULTADOS §5; F4-IA-13).
  O teste não exercitou o modelo físico (INTERPRETACAO, "Resumo", item 4).
- **Para um teste com poder** faltam, ao mesmo tempo, chuva pontual com hora confiável, ≥ 43 episódios e rótulo com hora
  e endereço (INTERPRETACAO §4).

### 4.2 2ª análise (pré-registrada em `2b9352c`): não testada

- **Pergunta:** a classificação muda quando a chuva de entrada passa da reanálise para o pluviômetro da ANA, com todo o
  resto igual? Era análise de sensibilidade à forçante, posterior à junção da 1ª, e não confirmação independente
  (PRE-REGISTRO-ANALISE-2 §1). Congelada com SHA-256 `21beddab…`, registrado em `818bf50`.
- **Portão:** só rodaria se a F2-DADOS-11 verificasse a janela das leituras de 15 min com critério commitado antes (§2.1).
- **O que aconteceu:** a F2-DADOS-11 confirmou a semântica da janela (incremento de 15 min com carimbo no fim; norma ANA
  2012 §4.2b), mas o critério pré-fixado C2d reprovou na leitura literal: estação 39167000, 2024, com 781 mm contra o piso
  de 800 mm. Havia ainda uma incompatibilidade: a série horária exige 4 de 4 leituras e o protocolo dizia 3 de 4.
  O agente da F2-DADOS-11 prosseguiu; o Arquiteto manteve a reprovação para fins científicos (PROGRESSO, F2-DADOS-11).
- **Classificação:** "não testada (portão reprovado)", registrada como emenda antes de qualquer escore da etapa 1b. Nada foi
  executado (PRE-REGISTRO-ANALISE-2, "Emendas"; PROGRESSO, F4-IA-17 descartada, e decisão de 2026-10-11).
- **Para reabrir:** novo pré-registro, com critério de plausibilidade com fonte (o piso de 800 mm era `[sem fonte]`).
- **Contexto que já se sabia antes de rodar:** a chuva horária da ANA integrada chega a 34,8 mm/h em 2021–2024 (79 942 h
  válidas; PROGRESSO, F2-DADOS-11), abaixo de `f · i_T` = 60,1 mm/h. A versão central do balde degeneraria de novo
  (§5 do pré-registro), e a estação mais próxima fica a 17 km do Derby (§5, item 6).

## 5. Medidas antes e depois

Todas as linhas vêm de PROGRESSO, "Medidas antes e depois", salvo indicação. A coluna "Ressalva" traz o que a
REVISAO-CRITICA ou o próprio PROGRESSO dizem sobre comparabilidade.

| Métrica | Antes | Depois | Origem | Ressalva |
|---|---|---|---|---|
| Testes do backend executados | 20 | 240 (`5c8dc53`, V1–V15) | surefire | — |
| Cobertura do backend (linhas / ramos) | 26,1% / 17,6% | 64,8% / 51,5% (`5c8dc53`) | JaCoCo 0.8.12; `docs/medidas/jacoco-2026-10-10.csv` | métodos diferentes; denominador cresceu; sem CSV do depois (RC-08) |
| Cobertura do export | 0,6% | 74,6% (`7ccc0e8`) | JaCoCo | export reescrito depois (RC-08) |
| Timeouts HTTP | 0 | todos os clientes | F2-BACK-07 | — |
| Fusos distintos nas mesmas colunas | 5 | 0 na série consolidada | F1-BACK-03; ADR 0006 | — |
| Gravações por GET em `flood_predictions` | 1 | 0 | F2-BACK-08 | — |
| Export de 5 anos, 1 ponto | 40,9 s | 2,47 s | F2-BACK-09; F2-BACK-22 | "p95" de n = 10 é o máximo; bases diferentes; a fria (2,83 s) ficou fora (RC-07) |
| Operações no OpenAPI | 46 | 52 | `docs/openapi/` | — |
| Violações das regras de camada | não medido | 0 (4 regras) | F5-REV-02a | — |
| Portas de banco e IA em 0.0.0.0 | 2 | 0 | F1-QA-02 | — |
| Linhas em `observation` | 0 | 1,78 M (878 MB) | F2-BACK-09 | a estimativa "antes" de ~219 mil era em formato largo (RC-09) |
| Pontos com cota do MDT | 0/5 | 4/5 | F3-GEO-01/04 | — |
| Concordância do D8 com WhiteboxTools | 53–55% | 97–99,5% | F3-GEO-07 | — |
| Features zeradas na inferência | 9 de 28 | 0 (ausente vira 422) | F1-IA-02; F4-IA-08 | — |
| Baselines implementadas | 0 de 4 | B2, B3 e variantes | F1-IA-02; RESULTADOS §8 | — |
| Reprodução por comando único | não existia | hashes idênticos | F5-ACAD-02 | restauração do dump em banco vazio não testada |
| Tratamentos de `UNKNOWN` no front | 3 | 1 | F2-FRONT-01 | — |
| Testes do front | 5 | 89 | `npm test`; F4-FRONT-01c | medidas antigas do front não saíam de commit (RC-02) |
| Botões só-ícone sem nome | 8 | 0 | F4-FRONT-01a | — |
| Contraste mínimo das cores de risco | 2,15:1 | 3,19:1 | F4-FRONT-01a | abaixo de 4,5:1 para texto (RC-19) |
| Largura mínima utilizável | 836 px | ~360 px | F4-FRONT-01a | por código, sem verificação visual |
| Camada de terreno | C1: 20,95 MB / 5,8 s (reprovada) | C3: 2,20 MB / 2,95–2,98 s | ADR 0008, cabeçalho; F3-FRONT-03 | limite de 3 s; agregação não fixada antes (RC-12); falta celular real |
| Dias com alagamento registrado (cidade, 5 anos) | — | 15 → 18 com 2022 recuperado | F2-BACK-16/18 | — |
| Eventos-dia casados aos 5 pontos | — | 500 m: 3; 1 km: 6; 2 km: 34; 3 km: 52 | F2-BACK-09/16 | — |
| Horas de maré no período rotulado (2023–2024) | 0 | 17 544 | F2-BACK-21 | — |
| Intensidade horária máxima (2021–2024) | — | reanálise 16,7 mm/h; ANA 34,8 mm/h na série integrada (36 mm/h na agregação anterior); IDF 50,3 mm/h | F4-IA-13; F2-DADOS-10; F2-DADOS-11 | ANA reprovada no critério C2d de plausibilidade |
| H1, ΔAUC | — | +0,113 [−0,010; +0,252] | F4-IA-15 | degenerado |

## 6. Estado do código ao fim da revisão

| Repositório | Branch de referência | No GitHub | Detalhe |
|---|---|---|---|
| projeto-mapi-api | `integracao/fase-2` @ `013cd36` (V1–V15, terreno incluído); docs em `revisao/fase-0` | sim | `docs/arquitetura/api.md` |
| projeto-mapi-ai | `fix/contrato-ia` @ `331f6c5`; `feat/avaliacao-f1` @ `abbd831`; commits do autor em `feature/cenarios-alagamento-ia` | sim | `docs/arquitetura/ai.md` |
| projeto-mapi-front | `wip/preservacao-2026-10-11` @ `8570183`; commits do autor em `feature/frontend-hardening-audit` | sim | `docs/arquitetura/front.md` |

No momento desta escrita nada estava em `main` (`main..integracao/fase-2`: 108 commits; front: 21; `git rev-list --count`,
2026-10-11). A fusão de tudo em `main`, por PRs por tema mais a integração, está em curso no mesmo dia (orquestrador).

## 7. Lições de processo

### 7.1 Erros do próprio processo

- **RC-01: dados só num volume.** A primeira carga existiu por horas sem dump. O dump pós-carga (`24aa31ce…`) só veio
  depois da revisão crítica, e a cópia fora da máquina continua pendente. Lição: snapshot e cópia externa logo após
  qualquer carga.
- **RC-02: trabalho só local.** Nenhuma branch tinha ido ao remoto até a revisão crítica. Lição: push ao fim de cada tarefa.
- **RC-04: medir o dado que sustenta a pergunta primeiro.** O CKAN "em geral só com data" já aparecia na Fase 1, mas a
  contagem dos rótulos veio depois de ~80 commits de pipeline. A troca para a tarefa diária era previsível e não conta
  como acerto do pré-registro (HANDOFF-fase-2, correção).
- **Erratas:**
  - "30 variantes" eram 24 (RESULTADOS, INTERPRETACAO, PRONTIDAO e o deck corrigidos).
  - A cobertura de 53,5% não se reproduziu (vale 51,0%).
  - F2-ARQ-02 ficou "em andamento" por um script de marcação, apesar de concluída.
  - O handoff da Fase 2 dizia "1 a 2 reprovações por ADR", o que é falso para o ADR 0005 (RC-11).
  - "3 admins extras" eram pelo menos 5 (RC-20).
  - A área contribuinte do Ibura aparece como 573 046 e 573 223 m² em documentos diferentes (RC-18).
  - O MEMO ficou com 15 dias enquanto o dado já tinha 18 (RC-06).
- **Crédito indevido e aplicação ampla:** nenhum critério pré-fixado foi ajustado depois de ver o dado, mas um foi
  creditado sem merecer (RC-04) e outro foi aplicado além do texto (Cemaden, RC-15).
- **Checkpoints de uma só voz:** todos Opus, sem pareceres guardados e com aprovações de poucos minutos no `git log`
  (RC-11). Não substituem a leitura do orientador.
- **Decisões de método sob a autorização durável** (MEMO §5) deveriam ter voltado ao autor (RC-25).
- **Nível acima do previsto:** a Fase 0 inteira rodou numa sessão Opus (PROGRESSO, nota da tabela de tarefas).
- **Tarefa marcada como concluída sem o efeito pedido:** F2-OPS-01 não removeu nenhum admin (RC-20).
- **Agente seguiu depois de um critério reprovar:** na F2-DADOS-11 o C2d reprovou e o agente prosseguiu com a integração;
  o Arquiteto manteve a reprovação para a 2ª análise. Lição: critério reprovado interrompe a tarefa e a decisão volta ao Arquiteto.
- **Critério com número sem fonte:** o piso de 800 mm/ano do C2d era `[sem fonte]`; um critério pré-fixado também
  precisa de origem.

### 7.2 Incidentes

- Um agente parou o banco durante a sessão de outro (F2-DADOS-10). Sem perda, mas virou regra: um agente por vez no banco.
- O compose antigo criou um volume vazio por engano (removido com 0 tabelas conferidas; F2-BACK-09).
- A 1ª tentativa da troca de imagem falhou por script, só no banco novo, e foi refeita (F2-DADOS-08).
- O limite semanal de uso derrubou subagentes nas Fases 1–4. Eles foram retomados (PROGRESSO, "Bloqueios").
- Runs assíncronos disparados em paralelo: um falhou por rate limiter e outro saiu vazio; foram repetidos (F2-BACK-21).
- Dentro de git worktree, o plugin de commit lê o repositório principal (F5-REV-02a).
- O `push --all` também subiu 22 branches temporárias de agentes; 16 foram removidas do remoto (F5-OPS-02; decisão de
  2026-10-11).

### 7.3 O que funcionou

- **Critérios fixados antes de medir decidiram contra a opção preferida três vezes:** camada COG reprovada, ANA mantida
  em quarentena e 2ª análise não testada (C2d). Na reabertura da ANA, o critério novo foi fixado com dados novos (REVISAO-CRITICA, "Critérios
  pré-fixados"; F2-DADOS-10).
- **Testes pegaram erros reais:** o bug do D8 próprio (F3-GEO-07), o k6 que fazia 0 requisições (F1-BACK-05) e a falha de
  rede do Nominatim gravada como "endereço inexistente" (F2-BACK-18).
- **Expectativa declarada antes de rodar:** o pré-registro previu a degeneração de H2, e ela se confirmou sem olhar
  rótulos (F4-IA-13).

## 8. Como reproduzir

1. **Avaliação F1, modo congelado** (offline, cerca de 1 minuto), no `projeto-mapi-ai`, branch `feat/avaliacao-f1`:
   `avaliacao/reproduzir.sh congelado`, com as entradas em `AVALIACAO_F1_DIR` (padrão `../avaliacao-f1`). O script
   confere `ef6772b7…`, `114ddd32…`, `91c6945e…`, `cef4a97f…` → `4615d416…` → `3753ada8…` → `dcf020cb…` e falha na
   primeira divergência (`avaliacao/README.md`).
2. **Avaliação F1, modo fontes:** restaurar o dump `24aa31ce…` num banco vazio com TimescaleDB 2.30.2
   (`timescaledb_pre_restore()`, `pg_restore --no-owner`, `timescaledb_post_restore()`; `docs/snapshots/2026-10-10-troca-de-imagem.md`),
   refazer o backfill 2021-02→09 pelos dois runs de `POST /api/admin/ingestion/runs`, um de cada vez, e rodar
   `avaliacao/reproduzir.sh fontes`. A reanálise pode ter sido revisada: diferença aqui é achado, não defeito (RC-17).
3. **Terreno:** `tools/terreno/run.sh atributos` (GDAL 3.10.0 por digest, WhiteboxTools 2.4.0 com hash), com os 48 zips
   do PE3D fora do git. Duas execuções dão `ponto_terreno.csv` `114ddd32…` (F3-GEO-07). Tiles: `run.sh tiles`.
4. **Backend:** `mvn verify` com Docker ligado (Testcontainers sobe `timescaledb-ha:pg16.15-ts2.30.2`). Stack local
   sempre com `docker compose -p projeto-mapi-api`, a partir da worktree da integração. Build de release em clone comum
   ou no Docker com `GIT_COMMIT` (`docs/CHECKLIST-RELEASE.md` §5).
5. **Carga de dados:** pela sequência de `docs/INGESTAO.md`, esperando cada run sair de `RUNNING` antes do próximo.
6. **Conferir os dumps:** `sha256sum` contra os `.sha256` em `MAPI/snapshots/` (`3ace3cde…`, `eac26687…`, `35dccf45…`,
  `24aa31ce…`).
