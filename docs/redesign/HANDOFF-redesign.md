# Handoff — redesign e telas novas (sessão de 2026-10-21)

Sessão encerrada a pedido do autor com a F3 pela metade. Nada foi enviado ao GitHub e o banco do autor não foi tocado.

## Branches e PRs

| Repo | Branch | Worktree | Estado |
|---|---|---|---|
| api | `docs/redesign-f0` | checkout principal | F0: tabela de telas, ADRs 0010–0015, OpenAPI, PROGRESSO |
| api | `feat/redesign-f2` | `.claude/worktrees/redesign-f2` | F2 concluída: V16–V20, todos os endpoints; 339 testes, 0 falhas |
| ia | `feat/simulate-bucket` | `.claude/worktrees/simulate-bucket` | F4 concluída: `POST /v1/simulate/bucket`; 30 testes |
| front | `feat/redesign-f1` | `.claude/worktrees/redesign-f1` | F1 concluída: tokens, temas, componentes, Nav, rotas; 141 testes, Playwright 33/33 |
| front | `feat/redesign-f3` | `.claude/worktrees/redesign-f3` | F3 concluída (inclui a F1); 343 testes |

## F3: concluída

- 15 de 15 telas em `feat/redesign-f3`: vitest 343, Playwright 91 ok e 7 pulados; `MapView` de 969 para 365 linhas; SSE no mapa e nos alertas; nginx do stream.
- Perguntas 47–53 no `PROGRESSO.md`.

## Depende do autor

1. ~~Aplicar V16–V20 no banco real~~ **Feito em 2026-10-21**, com o ok do autor:
   - Dump antes: `MAPI/snapshots/tide_db-2026-10-21-pre-V16-V20.dump`, 197 KB, SHA-256 `47ace65695a98c851b7c22fb14c453bd22b8abb7419fc5e808106202879a7489`.
   - A API da F2 subiu pelo override `scratchpad/override-f2.yml`. O Flyway aplicou V16–V20 com sucesso e a API ficou healthy.
2. **Achado:** o volume `projeto-mapi-api_postgres_ha_data` foi recriado em 2026-10-13. O banco estava quase vazio: 112 linhas em `observation`, 0 em `flood_occurrence`, 1 em `users`. A carga histórica está em `snapshots/tide_db-2026-10-10-pos-carga.dump` (317 MB) e no tar do Drive. Restaurar depende de decisão do autor.
3. PRs abertos: api #66 (squash), front #19 (`feat/redesign-f3`, inclui a F1) e ia #11. Merge na `main` só por pedido do autor.
5. V21 (`users.token_version`) aplicada em 2026-10-21. Dump anterior: `snapshots/tide_db-2026-10-21-pre-V21.dump`, SHA-256 `f0fb24b4…`. Os tokens antigos deixaram de valer.
6. O compose roda com as três branches novas por meio de `scratchpad/override-redesign.yml`. Depois do merge, volta ao compose normal a partir da `main`.
4. Backup no Drive: não vale a pena com o banco atual quase vazio. Refazer depois de restaurar a carga.

## Próximas fases

- F5: Playwright contra a API real num banco descartável, axe, contraste, contrato, Lighthouse, k6 com 50 conexões SSE, teste de ponta a ponta do simulador (backend com a IA).
- F6 e PWA: depois da F5, por decisão do autor.

## Pendências técnicas

- APAC_METEO24H segue com fuso não verificado: sem chuva dessa fonte nos alertas, no público e na linha do tempo.
- O prefixo `ANA-` no casamento sensor ↔ estação é suposição; conferir com dado real.
- `RISK_MEANING` está duplicado no front e no backend.
- O limite de taxa público usa `X-Forwarded-For`, que pode ser falsificado fora do nginx.
- O agente de IA instalou `pytest` e `httpx` no `.venv` principal da IA.
- A pasta `tests` da IA, rodada inteira, dá segfault em `psycopg2`. Já acontecia antes do redesign.
