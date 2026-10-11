# F5-REV-02a — Rastreabilidade do build (RC-03) e ArchUnit (RC-13)

Branch `fix/rastreabilidade-build`, a partir de `integracao/fase-2` (`e27755b`). Sem push.
Testes: `./mvnw clean test` = **228 verdes, 0 falhas, 0 ignorados** (linha de base 218 + 4 de `CodeVersionTest` + 6 regras ArchUnit).

## 1. codeVersion vem do build

**Antes:** `IngestionProperties.codeVersion` valia `"unknown"` e nada o preenchia; o manifesto do export e a coluna `code_version` de `ingestion_run` saíam assim.

**Agora**
- `pom.xml`: `io.github.git-commit-id:git-commit-id-maven-plugin:9.0.1` (versão fixa; o parent do Spring Boot já o liga na fase `initialize`) gera `target/classes/git.properties` com `git.commit.id` (completo), `git.commit.id.abbrev`, `git.dirty`, `git.branch`, `git.commit.time` e `git.build.time`. `failOnNoGitDirectory=false`: sem `.git` o build não quebra.
- `IngestionProperties.getCodeVersion()`: override `mapi.ingestion.code-version` > commit completo do `git.properties` (+ sufixo `-dirty` se havia alteração não commitada) > `"unknown-build-without-git"` (constante `NO_GIT`, explícita). O bean `GitProperties` do Spring Boot é injetado (`required=false`).
- Usam esse valor: manifesto do export (`DataExportServiceImpl`), `ingestion_run.code_version` (pipeline e ocorrências). A auditoria de predição não grava versão de código, só do modelo; não houve o que mudar nela.
- `application.yml`: `management.info.git.mode=full` para o `/actuator/info` mostrar commit completo, dirty e data do build.
- **Docker:** o `.dockerignore` exclui `.git` e o `Dockerfile` copia só `pom.xml` e `src`, então o plugin não vê o repositório. O `Dockerfile` recebe `GIT_COMMIT`, `GIT_DIRTY`, `GIT_BRANCH` (build-args) e grava `src/main/resources/git.properties` (ignorado no `.gitignore`) antes do `mvn package`. Sem os args o valor é `unknown-build-without-git`, visível no manifesto e no `/actuator/info`. O `docker-compose.yml` repassa as variáveis do ambiente.
- Testes: `CodeVersionTest` (fallback explícito, commit completo, `-dirty`, override, e o `git.properties` real do build não resulta em `unknown`); `DataExportIntegrationTest` agora afirma que `manifest.codeVersion` não começa com `unknown`.

**Como conferir**
- Local: `./mvnw package`, depois `cat target/classes/git.properties`.
- Docker: `GIT_COMMIT=$(git rev-parse HEAD) GIT_DIRTY=$(test -n "$(git status --porcelain)" && echo true || echo false) GIT_BRANCH=$(git branch --show-current) docker compose build mapi-api`.
- Execução: `curl localhost:9404/actuator/info` (porta de management; campo `git.commit.id`, `git.dirty`, `git.build.time`). No export, `manifest.codeVersion` repete o commit completo (`<sha>` ou `<sha>-dirty`).

**Limites declarados**
- O `Dockerfile` não foi construído nesta sessão (só o raciocínio e o plugin em repositório comum, onde `dirty=true` foi detectado corretamente).
- O plugin, rodando dentro de uma `git worktree`, leu o repositório principal (branch e commit errados). Em clone normal funciona. Builds de release devem sair de clone comum ou do Docker com `GIT_COMMIT`.
- `codeVersion` registra o commit, não garante que a árvore estava limpa se o build for feito no Docker sem `GIT_DIRTY` (fica `unknown`).
- A RC-03 tem outras partes (modelo sem metadata, UNKNOWN com `degradationReason`, tabela de linhagem) que não fazem parte desta tarefa.

## 2. ArchUnit (ADR 0002)

`com.tngtech.archunit:archunit-junit5:1.4.1` (teste). `ArchitectureTest` com 6 regras, cada uma em `FreezingArchRule`; store versionado em `src/test/resources/archunit_store` e configuração em `src/test/resources/archunit.properties` (`allowStoreCreation=false`, `freeze.refreeze=false`: violação nova falha, violação corrigida some do store).

| Regra | Violações existentes |
|---|---|
| Controller não depende de `repository` | 0 |
| Controller público não retorna tipo de `model` (inclui genéricos) | 0 |
| `model` não depende de `controller`/`dto` | 0 |
| `service` não depende de `controller` | 0 |
| `dto` não depende de `model` (achado da Fase 1) | 12 |
| `service` público não expõe tipo de `model` (achado da Fase 1) | 8 |

As duas últimas regras não estavam na lista do ADR, mas cobrem os achados medidos na Fase 1; por isso entram congeladas.

**Violações congeladas (20)**
- `dto -> model` (12 linhas, 2 classes, 1 tipo): `FloodEventDTO` e `ScraperEventDTO` usam o enum `model.FloodEvent.Severity` (campo, getter, setter, builder, construtor).
- `service` expõe entidade (8 métodos, 2 entidades): `RefreshTokenService`/`RefreshTokenServiceImpl` (`createRefreshToken`, `findByToken`, `verifyExpiration` retornam `RefreshToken`) e `SensorService`/`SensorServiceImpl.getFloodPointsCache()` retorna `FloodPoint`.

**Comparação com a medição da Fase 1 (4 violações)**
- `AuthController -> model.User`: o import ainda existe, mas é uso interno; nenhum método de controller retorna ou recebe entidade como contrato, então as regras do ADR dão 0 (esta dependência não é uma regra do ADR 0002).
- `FloodEventDTO`/`ScraperEventDTO -> model` e `SensorService` expondo `FloodPoint`: continuam, agora registrados. `RefreshToken` é violação nova em relação à lista citada.

Para corrigir uma violação antiga: ajuste o código e rode o teste; o store encolhe sozinho e o diff do `archunit_store` entra no commit.
