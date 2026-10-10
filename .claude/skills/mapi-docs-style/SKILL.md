---
name: mapi-docs-style
description: Estilo de documentação do MAPI — português do Brasil claro, ADR no formato combinado, Mermaid, todo número com origem rastreável, handoffs de fase com teto de uma página. Use sempre que for escrever ou editar README, qualquer arquivo em docs/, ADR, handoff de fase, PROGRESSO.md, relatório da revisão ou descrição de PR nos repositórios projeto-mapi-api, projeto-mapi-ai ou projeto-mapi-front.
---

# MAPI — estilo de documentação

Os documentos alimentam uma dissertação de mestrado. Tudo precisa ser auditável.

## 1. Linguagem

- Português do Brasil, frases curtas, voz ativa. Termos de domínio exatamente como em
  `docs/GLOSSARIO.md` (soleira, preamar, lâmina, nível lá fora, ponto crítico, cota da rua,
  área contribuinte). Termo novo entra primeiro no glossário.
- Número, não adjetivo. "p95 caiu de 840 ms para 310 ms" — não "ficou mais rápido".
- **Todo número tem origem**: comando, query, arquivo:linha, dashboard ou referência.
  Formato: `(fonte: k6 ramping_load, 2026-10-12)` ou nota de rodapé. Número sem origem é
  marcado `[sem fonte]` e entra como pendência.
- Sem emoji em docs novos (README antigos com emoji não precisam ser limpos à força).

## 2. Organização

- `README.md` curto: o que é, como subir, onde está o resto. Detalhe vai para `docs/`.
- `docs/`: ARQUITETURA, API, INGESTAO, MODELO-DE-DADOS, MODELAGEM-IA, TERRENO-PE3D,
  FRONTEND, GLOSSARIO, FONTES-E-LICENCAS, `adr/`, `extratos/`, `HANDOFF-fase-N.md`,
  `handoff/fase-N.json`.
- Edição cirúrgica: altere o trecho, não reescreva o arquivo.

## 3. ADR — `docs/adr/NNNN-titulo-em-kebab.md`

Numeração sequencial de 4 dígitos; antes de criar, liste `docs/adr/` para não duplicar.

```markdown
# NNNN — Título

- Status: proposto | aprovado | substituído por NNNN | rejeitado
- Data: AAAA-MM-DD
- Decisores: <agente> (autor), <checkpoint Máximo>, Lucas
- Tarefa: F<fase>-<AGENTE>-<n>

## Contexto
Problema, com números e origem.

## Opções consideradas
### A — ...
A favor / contra / custo. (Cada opção descartada precisa de argumento real.)

## Decisão
Uma frase, depois detalhes.

## Consequências
O que fica melhor, o que fica pior, o que precisa mudar, como medir.

## Condições que reabririam a decisão
```

## 4. Diagramas

Mermaid dentro do `.md` (`flowchart`, `sequenceDiagram`, `erDiagram`). Nada de imagem
exportada sem o fonte.

## 5. Handoff de fase

`docs/HANDOFF-fase-N.md`, no máximo uma página: o que foi feito, decisões, achados por
severidade, pendências, próxima tarefa. Acompanha `docs/handoff/fase-N.json` com
`achados[] {id, titulo, severidade, afeta_dissertacao, arquivos, metrica}`.
A fase seguinte lê só esses dois arquivos.

## 6. PR

Seções: O que mudou · Por quê · Como testar · O que ficou de fora · Medida antes/depois ·
Tarefa (ID do PROGRESSO.md).
