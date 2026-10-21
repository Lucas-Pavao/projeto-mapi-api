# 0015 — Divergências entre a skill do front e AGENTS.md/GEMINI.md

- Status: **aprovado** (2026-10-21, Lucas, pergunta 42)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F1-REDESIGN-FRONT

## Contexto

Na F1 do redesign (branch `feat/redesign-f1` do `projeto-mapi-front`), o agente de front encontrou regras em conflito entre a skill `mapi-clean-arch-react` e os arquivos `AGENTS.md`/`GEMINI.md` do front. A regra do redesign manda registrar cada divergência em ADR e deixar a decisão para o autor. Nada abaixo foi aplicado por conta própria, exceto o que já foi aprovado na pergunta 37.

## Divergências e proposta

| # | Tema | Skill | AGENTS.md / GEMINI.md | Código hoje | Proposta |
|---|---|---|---|---|---|
| 1 | Cliente de dados | TanStack Query exige ADR | omissos | instalado na F1 (pergunta 37) | Registrar aqui como aprovado: TanStack Query é o cliente de cache das telas novas |
| 2 | Cor do risco | `RiskBadge` com tokens `risk-*` | mapear o enum de risco para variantes do shadcn | `RiskBadge` com tokens (F1) | Seguir o design system (tokens); atualizar AGENTS.md |
| 3 | Biblioteca de primitivos | — | cita Radix | usa `@base-ui` | O código vence; corrigir AGENTS.md para `@base-ui` |
| 4 | Features | uma pasta por feature | lista só `authentication` e `map` | 7 features novas na F1 (sensors, alerts, events, model, reports, admin, public-risk) | Aceitar uma feature por tela de domínio; atualizar GEMINI.md |
| 5 | Skill `frontend-design` | carregar antes de mudança visual | — | skill não existe no ambiente | Retirar a exigência da skill; o design system MAPi (artifact) é a referência visual |
| 6 | `MapView` com cerca de 1 100 linhas | extrair componentes e hooks | — | só o estilo foi trocado na F1 | Extrair na F3, numa tarefa própria, com teste antes e depois (sem mudar comportamento) |

## Consequências

- Melhor: as convenções voltam a ter uma fonte só, e a skill passa a refletir o código.
- Pior: AGENTS.md e GEMINI.md do front precisam ser editados, e a skill também (itens 5 e 6).
- Como medir: nenhuma divergência aberta entre a skill e AGENTS/GEMINI no fim da F3.

## Condições que reabririam a decisão

- Troca de biblioteca de primitivos ou do cliente de dados.
