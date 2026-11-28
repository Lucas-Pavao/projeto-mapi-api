# Congelamento para a validação de 2027 (onda 16, Fase 9.2)

- Data: 2026-11-28 · Status: **procedimento pronto; nada congelado ainda**. Congela-se antes de 15/03/2027, depois de o
  Lucas e o orientador fecharem os números de `docs/PRE-REGISTRO-VALIDACAO-2027.md`.

## O que é congelar

Marcar com uma tag git **assinada** (`validacao-2027-congelada`) o commit de cada repositório que estará no ar durante todo o
período, e provar que o código servido é esse commit. Daí até o fim do período nada muda no modelo, nos parâmetros nem na
regra (pré-registro, seção 1).

## Passo a passo

1. Fazer o merge dos PRs da onda 16 na main dos três repositórios (decisão do Lucas).
2. Refazer as imagens com o commit gravado nelas:
   ```
   GIT_COMMIT=$(git rev-parse HEAD) \
   AI_GIT_COMMIT=$(git -C ../projeto-mapi-ai rev-parse HEAD) \
   FRONT_GIT_COMMIT=$(git -C ../projeto-mapi-front rev-parse HEAD) \
   docker compose build mapi-api mapi-ai mapi-front && docker compose up -d
   ```
3. Conferir: `scripts/congelamento/congelar-validacao-2027.sh` (só confere; não cria nada). Ele recusa se algum repositório
   não estiver na main, tiver arquivo alterado, estiver diferente do `origin/main` ou se o commit no ar for outro
   (`/actuator/info` da api, `/health` da ia, `/version.json` do front).
4. Criar as tags: `scripts/congelamento/congelar-validacao-2027.sh --criar` (e `--enviar` para mandar ao GitHub).
5. Registrar aqui e no `PROGRESSO.md` os três commits e a data.

## Assinatura

Em 2026-11-28 não há chave GPG nem `user.signingkey` configurados; há a chave SSH `~/.ssh/id_ed25519.pub`. O script assina com
ela só na própria chamada (`git -c gpg.format=ssh -c user.signingkey=...`), sem mudar a configuração global. Outra chave:
`MAPI_CHAVE_ASSINATURA=<caminho .pub>`. Para conferir uma tag (`git tag -v`) é preciso um arquivo de assinantes permitidos
(`gpg.ssh.allowedSignersFile`) com essa chave.

## Durante o período

- Correção de bug que muda o nível invalida o período a partir dela: o relatório separa antes e depois.
- Buracos de operação (pilha parada) entram no relatório semanal do modo sombra (`shadow_weekly_report`, Fase 9.4) e são
  contados à parte. Exemplo já visto: a pilha ficou parada de ~24/11 01h a 28/11 12h UTC de 2026 (PROGRESSO, 28/11).

## Registro

| Repositório | Commit congelado | Data |
|---|---|---|
| projeto-mapi-api | — | — |
| projeto-mapi-ai | — | — |
| projeto-mapi-front | — | — |
