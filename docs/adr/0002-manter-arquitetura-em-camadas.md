# 0002 — Manter a arquitetura em camadas atual do backend

- Status: aprovado
- Data: 2026-10-10
- Decisores: Arquiteto (autor), Lucas (aprovou em 2026-10-10)
- Tarefa: F1-ARQ-03

## Contexto
O prompt da revisão descrevia o alvo como ports-and-adapters com "domínio sem anotação de
framework". O `agents.md` §1 define camadas (`controller → service → repository`), com `model/`
sendo as próprias entidades JPA. Inventário: 14 entidades em `model/`, 13 com `jakarta.persistence`.

## Opções consideradas
### A — Migrar para ports-and-adapters (domínio puro + entidades JPA separadas)
A favor: domínio testável sem Spring. Contra: duplica 14 modelos e mappers; alto esforço sem
ganho mensurável para a dissertação; risco de regressão; esbarra no anti-escopo "redesenhar o domínio".
### B — Manter as camadas e congelar a regra com ArchUnit
A favor: zero migração; protege o que importa (controller não acessa repositório nem expõe entidade).
Contra: domínio continua acoplado ao JPA.

## Decisão
**B.** Mantém-se a estrutura do `agents.md`. A regra de dependência vigente é congelada com
ArchUnit. Lógica pura nova (ex.: cálculo físico do balde) pode nascer sem anotação de framework
quando for natural, sem obrigação de mover o que existe.

## Consequências
Skill `mapi-clean-arch-java` atualizada (alvo ports-and-adapters removido). Teste ArchUnit entra
em F1-BACK-02, com o número de violações existentes medido antes de ativar a regra.

## Condições que reabririam a decisão
Necessidade de rodar a lógica de risco fora do Spring (ex.: compartilhar com a IA em Python via
biblioteca) ou crescimento do domínio que torne os testes com Spring lentos demais.
