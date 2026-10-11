# Como contribuir

Guia curto. O detalhe está em `docs/`.

## 1. Branches e PRs

- Uma branch por tema. Nomes curtos: `feat/...`, `fix/...`, `docs/...`, `test/...`.
- Um PR por tema, com base em `main`.
- PR segue as seções do estilo MAPI: O que mudou · Por quê · Como testar · O que ficou de fora · Medida antes/depois · Tarefa (ID do `.claude/mapi/PROGRESSO.md`).
- Não misture refatoração, dado e documentação no mesmo PR.

## 2. Decisão vira ADR

- Toda decisão de arquitetura, de dado ou de contrato tem um ADR em `docs/adr/NNNN-titulo-em-kebab.md`.
- Liste `docs/adr/` antes de criar, para não duplicar número.
- Formato: Status, Data, Decisores, Tarefa, Contexto, Opções consideradas, Decisão, Consequências, Condições que reabririam a decisão. Modelo completo em `.claude/skills/mapi-docs-style/SKILL.md`, seção 3.
- Status válidos: proposto, aprovado, substituído por NNNN, rejeitado.

## 3. Documento congelado não se edita em silêncio

- Pré-registro (`docs/PRE-REGISTRO-AVALIACAO.md`) e escores cegos são congelados por commit e SHA-256.
- Mudança posterior vira **emenda**: seção nova, com data, motivo e quem decidiu. Não se reescreve o trecho original.
- Emenda só vale antes da etapa que ela afeta. Depois da junção com rótulos, nada muda sem nova rodada registrada.
- Um ADR aprovado segue a mesma regra: alteração de conteúdo vira emenda, como o ADR 0007 emenda 1.

## 4. Testes são obrigatórios

- Toda mudança de comportamento tem teste.
- Antes de abrir PR, rode:
  ```bash
  ./mvnw clean test
  ```
- Testes de integração usam Testcontainers (ADR 0004), então o Docker precisa estar em execução. O banco de teste é TimescaleDB, na imagem fixada no ADR 0005.
- Mudança de schema tem migração Flyway e teste que aplica a migração.
- Regras de arquitetura são verificadas com ArchUnit. Violação nova falha o build.

## 5. Convenções

- **Fuso:** UTC na persistência; `America/Recife` só na exibição (ADR 0001).
- **Schema:** só muda por Flyway (ADR 0003). Migração nova é aditiva. Nunca edite migração já aplicada.
- **Camadas (ADR 0002):** `controller → service → repository`. Controller não acessa `repository` nem devolve entidade de `model`. `model` não depende de `controller` nem de `dto`.
- **JSON:** nomes de campo em inglês, camelCase (`floodPoint`, `codeVersion`). Termos de domínio em português ficam no código interno e no `docs/GLOSSARIO.md`.
- **Texto:** português do Brasil nos documentos. Sem emoji em docs novos.
- **Números:** todo número em documento tem origem rastreável.

## 6. Como rodar localmente

Pré-requisitos: Java 21, Docker.

```bash
# banco (nome de projeto fixo, para não criar outro volume)
docker compose -p projeto-mapi-api up -d postgres

# backend
./mvnw spring-boot:run
```

- API: `http://localhost:8080`. Swagger em `/swagger-ui/index.html`.
- Segredos (`JWT_SECRET`, senhas) ficam em `.env` na raiz, que não vai para o Git. Veja `docs/SEGURANCA-LOCAL.md`.
- Compose sempre com `-p projeto-mapi-api`.

## 7. Onde está o resto

- Arquitetura e decisões: `docs/adr/`
- API: `docs/API.md`
- Ingestão: `docs/INGESTAO.md`
- Glossário: `docs/GLOSSARIO.md`
- Fontes e licenças: `docs/FONTES-E-LICENCAS.md`
- Checklist de release: `docs/CHECKLIST-RELEASE.md`
- Handoffs de fase: `docs/HANDOFF-fase-N.md`
