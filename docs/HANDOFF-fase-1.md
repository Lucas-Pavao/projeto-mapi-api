# Handoff — Fase 1 (2026-10-10)

**Feito.** 4 leituras dirigidas (subagentes Sonnet; 3 interrompidas pelo limite de uso e retomadas),
com 90 achados em `docs/revisao/fase-1/`. Também: `docs/CONSISTENCIA-ENTRE-REPOS.md`,
`docs/MATRIZ-PRIORIDADE-FASE-1.md` (checkpoint Opus independente: APROVADO com 6 ressalvas, todas
aplicadas), ADRs 0001–0004, snapshot auditável do banco, OpenAPI e linha de base de medidas.

**Branches locais (sem push; `gh` ausente):** api `revisao/fase-0` (docs), `fix/k6-remote-write-env`,
`fix/seguranca-local`; ai `docs/remove-mapi-edge`; projeto-mapi `docs/descontinuado`. No front, a
correção do README ficou não commitada sobre o WIP do Lucas.

**P0 (invalidam a dissertação), em ordem:** 0 snapshot (feito) → 9 Testcontainers → 4 pipeline de
ingestão idempotente com proveniência → 2 fuso UTC (5 tratamentos nas mesmas colunas) → 1 rótulo
circular (o realinhamento ao pico de chuva sobrescreve a hora do evento) → 3 ANA fora do export →
5 export honesto (imputação vira `null` + flag; manifesto) → Fase 3 → 6 ML válido (H = 0 hoje, 9/28
features zeradas na inferência, 0/4 baselines, artefato sem metadata).

**Fatos novos que mudam o plano.**
- O banco local **não tem histórico** (555 leituras de sensor, 40 de clima, 0 eventos; 2026-09-05 → 10-01). A premissa "migrar sem perder o histórico carregado" não se aplica localmente (pergunta 12).
- Nenhum cliente HTTP tem timeout, e falha da APAC vira "sucesso 0/0" (Retry nunca dispara).
- Cobertura 26,1% em linhas (export 0,6%). O p95 de cache fica em 0,8–4 ms. O export (143 ms) não é representativo com o banco vazio.
- O k6 `heavy_export` nunca rodava (`id_ponto`→`slug`) e o limite passava vazio. Corrigido.

**Perguntas abertas para o Lucas:** 8 contrato de predição, 9 rótulo (desativar o realinhamento e
reimportar o CKAN), 10 `wipe-database`, 11 se `flood_predictions` entra nos resultados, 12 onde está o
histórico. Pendente sem bloqueio: F0-ARQ-07 (evals das skills).

**Próxima tarefa:** F2-DADOS-01 (evidência do banco: Timescale × MySQL, PostGIS, volume projetado),
depois F2-DADOS-02 (ADR com checkpoint). F2-DADOS-05 (ADR do pipeline) pode começar em paralelo a
partir da proposta em `F1-BACK-03.md` §10.
