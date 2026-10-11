# Handoff — Fase 2: banco, ingestão, contrato e primeira carga (2026-10-10)

**Decisões (ADRs, todos aprovados):**
- **0005:** PostgreSQL 16 + TimescaleDB + PostGIS. A imagem fixa `timescaledb-ha:pg16.15-ts2.30.2` foi trocada por dump/restore com contagens idênticas, e o volume antigo foi removido.
- **0006:** pipeline staging → validação/quarentena → série consolidada versionada em UTC, com `provenanceId`.
- **0007:** contrato de predição — enum fechado, `null` em falha, `degradationReason`, previsão feita no ciclo de coleta e GET sem gravar.

Cada ADR passou por checkpoint Máximo, com 1 a 2 reprovações e refação.

**Código** (`integracao/fase-2`, HEAD e27755b, migrações V1–V13; nada no `main` ainda):
- 218 testes, 0 falhas;
- cobertura de 64,5% em linhas e 51,1% em ramos (eram 26,1% e 17,6%);
- export de 5 anos com p95 de 2,47 s (era 40,9 s);
- 0 timeouts HTTP antes, todos os clientes com timeout agora;
- os 5 tratamentos de fuso misturados viraram `source_tz` declarado por fonte;
- 52 operações no OpenAPI (`docs/API.md`).

**Banco local** (`projeto-mapi-api_postgres_ha_data`; snapshot pós-carga `24aa31ce…` só foi feito depois da revisão crítica (RC-01) — antes, o volume era a única cópia):
- Open-Meteo e ERA5-Land de 5 anos, maré 2021–2026 extraída dos PDFs da DHN (44 568 h), terreno e CKAN deduplicado;
- 1,78 M+ linhas em `observation`;
- Cemaden com fuso verificado (UTC−3); ANA e APAC 24h continuam em quarentena (o critério pré-fixado falhou para a ANA).

**Achado que muda a dissertação:** o CKAN tem 225 ocorrências, todas com precisão de dia, e só **18 dias com
alagamento na cidade em 5 anos**. O casamento a 500 m dá 3 eventos-dia nos 5 pontos. A regra pré-registrada
trocou a tarefa principal para a diária, mas nem a diária tem poder estatístico (ver
`docs/MEMO-ESCASSEZ-DE-ROTULO.md`). **A Fase 4 depende da pergunta 28 (F1 ou F2).**
Correção (REVISAO-CRITICA RC-04): o disparo da regra era previsível desde a Fase 1 (F1-BACK-03 já dizia que o
CKAN tem "em geral só data"), e a contagem de rótulos deveria ter vindo antes de ~80 commits de pipeline. Não é
um "acerto" do pré-registro, e sim uma falha de sequência: medir o rótulo primeiro.

**Pendências:**
- F2-DADOS-10: a ANA será reaberta com teste pré-fixado quando houver ≥ 200 leituras ao vivo; nível em cm → m.
- 15 lacunas de documentação OpenAPI no código.
- Os admins de revisão criados por agentes (≥ 5, segundo a RC) ficam no banco local.
- O compose do checkout principal (branch de docs) aponta a imagem antiga: use sempre a worktree da integração com `-p projeto-mapi-api`.

**Lições de processo:**
- O critério fixado antes de medir decidiu contra a opção preferida na camada COG e no fuso da ANA. A troca para a tarefa diária era previsível (RC-04) e não conta como prova do método.
- Medir o dado que sustenta a pergunta (rótulos) antes de construir a infraestrutura em volta dele.
- Agentes simultâneos no banco exigem serializar: um por vez com compose de pé.
