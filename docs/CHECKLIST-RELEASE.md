# Checklist de release do backend

- Tarefa: F5-QA-02 · Data: 2026-10-11 · Escopo: `projeto-mapi-api`
- Use este checklist antes de marcar uma versão como pronta para a banca ou para outro ambiente.
- Cada item tem um comando ou um arquivo de prova. Item sem prova não conta como feito.
- Números registrados aqui devem ter origem (comando, arquivo:linha ou data), conforme `.claude/skills/mapi-docs-style/SKILL.md`.

## Estado do branch antes de usar

Verificado em 2026-10-11 no branch `feat/fuso-ana-reteste`. Os itens marcados com **[F5-REV-02a]** dependem do trabalho do branch `fix/rastreabilidade-build` (`docs/revisao/fase-5/F5-REV-02a.md`), que ainda não está neste branch:

- não há `ArchitectureTest` nem `CodeVersionTest` em `src/test`;
- o `pom.xml` não declara `git-commit-id-maven-plugin`, `archunit-junit5` nem `jacoco-maven-plugin`;
- o `Dockerfile` não recebe `GIT_COMMIT`, `GIT_DIRTY` nem `GIT_BRANCH`.

Antes de uma release, confirme que o branch de release contém essas mudanças. Se não contiver, o item correspondente falha e a release espera.

## 1. Ambiente

- [ ] Docker em execução. Os testes de integração sobem contêineres com Testcontainers (ADR 0004).
- [ ] Imagem do banco é a fixada no ADR 0005: `timescale/timescaledb-ha:pg16.15-ts2.30.2`. Confirme com `grep -n timescaledb-ha docker-compose.yml`.
- [ ] Compose sempre com o nome de projeto fixo:
  ```bash
  docker compose -p projeto-mapi-api <comando>
  ```
  Sem `-p`, o compose pode criar outro volume e usar um banco vazio ou antigo.
- [ ] Branch e árvore de trabalho registrados: `git status --porcelain` vazio, ou a lista de alterações anotada no PROGRESSO.

## 2. Suíte de testes

- [ ] Suíte verde a partir de build limpo:
  ```bash
  ./mvnw clean test
  ```
  Prova: `target/surefire-reports/` sem falhas nem erros. Anote o total de testes, falhas e ignorados.
- [ ] Nenhum teste ignorado sem motivo registrado no código ou no PROGRESSO.

## 3. Arquitetura (ArchUnit) **[F5-REV-02a]**

- [ ] `ArchitectureTest` passa com `allowStoreCreation=false` e `freeze.refreeze=false` (`src/test/resources/archunit.properties`).
- [ ] Nenhuma violação nova. Violação nova falha o teste. Violação corrigida some do `src/test/resources/archunit_store`; o diff do store entra no commit.
- [ ] Regras do ADR 0002 seguem valendo: controller não depende de `repository` e não retorna `model`; `model` não depende de `controller` nem de `dto`; `service` não depende de `controller`.

## 4. Cobertura

Método fixo, para que as medidas sejam comparáveis entre versões:

```bash
./mvnw clean org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
```

- [ ] O relatório existe em `target/site/jacoco/index.html`.
- [ ] A cobertura de linhas e de ramos é diferente de zero. Se o relatório estiver vazio, o agente não foi anexado à JVM dos testes. Pare e corrija o método antes de registrar qualquer número.
- [ ] Registrar, na entrada da release no PROGRESSO: versão, commit, data, cobertura de linhas e de ramos, e o comando usado. Números sem origem são marcados `[sem fonte]`.

Nota: o `pom.xml` deste branch não configura JaCoCo. O comando acima usa as coordenadas do plugin direto na linha de comando. Na primeira medida, confira o resultado conforme o item anterior.

## 5. Build e rastreabilidade do código **[F5-REV-02a]**

- [ ] Build de release sai de **clone comum** ou do **Docker com `GIT_COMMIT`**. O plugin git-commit lê o repositório errado dentro de `git worktree` (F5-REV-02a, §1, "Limites declarados").
- [ ] Clone comum:
  ```bash
  ./mvnw -B -DskipTests package
  cat target/classes/git.properties
  ```
  Prova: `git.commit.id` igual a `git rev-parse HEAD`; `git.dirty` igual a `false` se a árvore estiver limpa.
- [ ] Docker:
  ```bash
  GIT_COMMIT=$(git rev-parse HEAD) \
  GIT_DIRTY=$(test -n "$(git status --porcelain)" && echo true || echo false) \
  GIT_BRANCH=$(git branch --show-current) \
  docker compose -p projeto-mapi-api build mapi-api
  ```
  Sem essas variáveis, a versão sai como `unknown-build-without-git`. Isso reprova a release.
- [ ] A imagem construída pelo Docker ainda não foi testada com este fluxo (F5-REV-02a, "Limites declarados"). Na primeira release, registre o resultado do build no PROGRESSO.

## 6. Migrações Flyway

- [ ] Toda migração nova é aditiva: nova tabela, nova coluna nullable, novo índice. Confira:
  ```bash
  find src -path '*db/migration*' -name 'V*.sql' -newer pom.xml
  ```
  Leia cada arquivo listado. Procure por `DROP`, `TRUNCATE`, `RENAME` e troca de tipo em coluna existente.
- [ ] Remoção ou troca destrutiva só entra com ADR aprovado (ADR 0003) e plano de migração de dado.
- [ ] Migração aprovada pelo Lucas. Registrar o número da pergunta no PROGRESSO.
- [ ] Migração aplicada sem erro em banco de teste separado (nunca no volume com dado real). Os testes com Testcontainers já cobrem o caso de banco limpo.

## 7. Snapshot antes de migrar dado real

Antes de rodar qualquer migração ou carga sobre dado real:

- [ ] Gerar o dump fora do repositório:
  ```bash
  mkdir -p "$HOME/backups/mapi"
  DUMP="$HOME/backups/mapi/tide_db-$(date -u +%Y%m%dT%H%M%SZ).dump"
  docker compose -p projeto-mapi-api exec -T postgres \
    pg_dump -U mapi_user -Fc tide_db > "$DUMP"
  sha256sum "$DUMP" | tee "$DUMP.sha256"
  ```
- [ ] Guardar o SHA-256 no PROGRESSO, junto da data e da migração que virá depois.
- [ ] Testar a restauração em banco separado antes de migrar: `pg_restore` para outro banco e comparar contagens de `observation` e de `point_terrain`.
- [ ] Nunca gravar o dump dentro do repositório. Ele contém dado de agência e de usuário.

## 8. Export e manifesto

- [ ] Exportar o dataset de teste da release.
- [ ] O manifesto não tem `codeVersion` começando com `unknown`. Confira:
  ```bash
  jq -r '.codeVersion' manifesto.json | grep -v '^unknown' && echo OK
  ```
  Prova: a linha `OK` aparece. Se o campo começar com `unknown`, a rastreabilidade não está ativa (item 5).
- [ ] Manifesto traz `datasetId` (SHA-256 do CSV), período, fusos e `rule_version`. Esses campos vêm do ADR 0006.

## 9. OpenAPI e `docs/API.md`

- [ ] Regenerar o OpenAPI a partir da aplicação em execução:
  ```bash
  curl -s localhost:8080/v3/api-docs > docs/openapi/openapi-AAAA-MM-DD.json
  ```
- [ ] Contar operações e comparar com a versão anterior:
  ```bash
  jq '[.paths[] | keys[]] | length' docs/openapi/openapi-AAAA-MM-DD.json
  ```
- [ ] Atualizar `docs/API.md`: total de operações, rotas novas ou removidas, regras de acesso. A seção 1 do arquivo lista a fonte dos números.
- [ ] Rotas de admin só existem no perfil certo. Confira que `DELETE /api/admin/ingestion/wipe-database` não aparece fora do perfil `dev`.

## 10. Segredos fora do repositório

- [ ] `.env` está no `.gitignore` e não aparece em `git ls-files`:
  ```bash
  git ls-files | grep -E '(^|/)\.env$' && echo "ERRO" || echo OK
  ```
- [ ] `JWT_SECRET`, `POSTGRES_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` e `ADMIN_BOOTSTRAP_PASSWORD` vêm do `.env` ou do ambiente, não do `docker-compose.yml`.
- [ ] Riscos de credencial padrão e exposição de porta estão descritos em `docs/SEGURANCA-LOCAL.md`. Esse arquivo está no branch `fix/seguranca-local` e ainda não está neste branch. Antes de qualquer uso fora do ambiente local, faça a leitura dele e confirme que os itens da tabela 2 foram trocados.

## 11. Documentação de fase

- [ ] `.claude/mapi/PROGRESSO.md` atualizado: versão, commit, testes, cobertura, decisões tomadas, pendências.
- [ ] Handoff da fase atualizado (`docs/HANDOFF-fase-N.md`, até uma página) e `docs/handoff/fase-N.json` com `achados[]`.
- [ ] ADR novo para cada decisão de arquitetura, dado ou contrato tomada na release (ver `CONTRIBUTING.md`).
- [ ] Pré-registro e documentos congelados não foram editados sem emenda registrada.

## 12. Números com origem

- [ ] Cada número nos documentos da release traz origem: comando, arquivo:linha, data ou referência.
- [ ] Número sem origem é marcado `[sem fonte]` e entra como pendência no PROGRESSO.
- [ ] Revisão rápida: `grep -n '\[sem fonte\]' docs/*.md` e `grep -rn '\[sem fonte\]' .claude/mapi/PROGRESSO.md`. Resolva ou aceite cada ocorrência por escrito.

## Registro da release

Copie para o PROGRESSO ao fim:

```
Release: <versão> · commit: <sha> · data: <AAAA-MM-DD>
Testes: <total> total, <falhas> falhas, <ignorados> ignorados (./mvnw clean test)
Cobertura: linhas <x,x>% · ramos <x,x>% (JaCoCo 0.8.12, comando da seção 4)
ArchUnit: <violações novas> novas, <congeladas> congeladas
Migrações: <lista> · dump: <nome> · SHA-256: <hash>
Manifesto codeVersion: <valor>
OpenAPI: <operações> operações · docs/API.md atualizado: sim/não
```
