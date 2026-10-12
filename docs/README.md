# Documentação do MAPi — índice

Comece por `VISAO-GERAL.md`. O estado da revisão e as decisões ficam em `../.claude/mapi/PROGRESSO.md`.

## Sistema (uso e manutenção)

| Documento | Para que serve |
|---|---|
| `VISAO-GERAL.md` | Como o sistema funciona de ponta a ponta; arquivos do PE3D; estado verificado |
| `PROMPT-CLAUDE-WEB.md` | Prompt pronto para colar no claude.ai: explica tudo em linguagem simples e gera um HTML completo |
| `arquitetura/api.md`, `ai.md`, `front.md` | Arquitetura de cada repositório |
| `API.md`, `openapi/openapi-integracao-fase2.json` | Rotas da API e contrato (o JSON é o snapshot de referência citado no API.md) |
| `INGESTAO.md` | Pipeline de ingestão (fontes, quarentena, versões, proveniência) |
| `MODELO-DE-DADOS.md` | Tabelas e migrações |
| `TERRENO-PE3D.md` | Processamento do MDT, atributos por ponto, validação da hidrologia |
| `SEGURANCA-LOCAL.md` | O que é seguro só em ambiente local |
| `CHECKLIST-RELEASE.md` | Verificações antes de uma entrega |
| `GLOSSARIO.md` | Termos e nomes de campos |
| `adr/` | Decisões de arquitetura (0001–0009), com emendas |
| `snapshots/` | Manifesto de cada dump do banco (hash e contagens); os dumps estão no Google Drive, pasta "MAPI — backup" |

## Dados externos e pendências

| Documento | Para que serve |
|---|---|
| `PENDENCIAS-DE-INFORMACAO.md` | O que depende de outras pessoas ou órgãos, por quê, e o que fazer |
| `pedidos/` | Rascunhos de pedidos a órgãos (envio pelo autor) |
| `extratos/` | Trechos das fontes consultadas (F-41, IBGE, hidrologia, S2iD) |
| `FONTES-E-LICENCAS.md` | Fonte, licença e citação de cada dado |
| `ROADMAP-REVISAO.md` | Backlog técnico priorizado |

## Dissertação

| Documento | Para que serve |
|---|---|
| `PRE-REGISTRO-AVALIACAO.md`, `PRE-REGISTRO-ANALISE-2.md` | Protocolos congelados; só mudam por emenda |
| `RESULTADOS-AVALIACAO-F1.md`, `INTERPRETACAO-F1.md` | Resultado da 1ª análise e leitura dele |
| `MEMO-ESCASSEZ-DE-ROTULO.md`, `PARAMETROS-MODELO-FISICO.md`, `MODELAGEM-IA.md` | Método e parâmetros |
| `REVISAO-CRITICA.md`, `PRONTIDAO-ACADEMICA.md`, `RELATORIO-REVISAO.md` | Contestação, prontidão para a banca e relatório final da revisão |
| `apresentacao/MAPi-disciplina.pptx` | Deck da disciplina |
| `planilhas/comparacao-modelos-f1.xlsx` | Tabela comparativa dos modelos e baselines da 1ª análise (AUC, IC, alertas), com sensibilidade e origem |
| `planilhas/comparacao-banco.xlsx` | Critérios e benchmark TimescaleDB+PostGIS × MySQL (ADR 0005) |

## Histórico da revisão

Os handoffs das fases 0–5, os achados por fase (`revisao/fase-*`), as screenshots, a matriz de prioridade, o relatório
de consistência entre repositórios, as medidas de linha de base (as duas planilhas voltaram para `planilhas/`) e o `TimescaleSetup.sql` saíram da árvore em 2026-10-11.
Continuam no git, na tag **`revisao-completa-2026-10-11`**:

```
git show revisao-completa-2026-10-11:docs/HANDOFF-fase-3.md
git checkout revisao-completa-2026-10-11 -- docs/revisao      # restaura a pasta, se precisar
```

Documentos congelados (pré-registros) e ADRs citam esses caminhos; leia-os na tag.
