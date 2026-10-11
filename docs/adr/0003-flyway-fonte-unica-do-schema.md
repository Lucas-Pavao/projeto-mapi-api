# 0003 — Flyway como fonte única do schema

- Status: aprovado
- Data: 2026-10-10
- Decisores: Arquiteto (autor), Lucas (aprovou em 2026-10-10)
- Tarefa: F1-ARQ-03

## Contexto
O `agents.md` §2.4 mandava replicar toda mudança estrutural no `TimescaleSetup.sql`. O código
já tinha migrado para Flyway (`db/migration/V1__baseline_schema.sql`, `V2__add_river_alert_levels.sql`,
`ddl-auto: validate`), e o próprio `TimescaleSetup.sql` traz no cabeçalho a marca "SUPERADO" e
não é mais montado pelo compose. O README (linhas 69, 105, 217) ainda o descreve como script
crítico de inicialização. **Divergência entre documentação e código: o código vence.**

## Opções consideradas
### A — Manter os dois sincronizados
Contra: duas fontes divergem com o tempo; nada garante a sincronia.
### B — Flyway como fonte única; `TimescaleSetup.sql` só como referência histórica
A favor: migração versionada, ordenada e validada no boot. Contra: perde-se o "script único" de leitura rápida.

## Decisão
**B.** Toda mudança de schema é uma nova migração `V<n>__descricao.sql`. O `TimescaleSetup.sql`
não é mais editado. Para ler o schema atual, use `docs/MODELO-DE-DADOS.md` (F2-DADOS-04).

## Consequências
`agents.md` §2.4 e README atualizados. Migração continua exigindo aprovação do Lucas antes de ser
aplicada (regra geral da revisão).

## Condições que reabririam a decisão
Nenhuma prevista.
