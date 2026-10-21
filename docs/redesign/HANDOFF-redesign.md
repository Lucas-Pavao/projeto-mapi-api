# Handoff — redesign e telas novas (sessão de 2026-10-21)

Sessão encerrada a pedido do autor com a F3 pela metade. Nada foi enviado ao GitHub e o banco do autor não foi tocado.

## Branches (todas locais, sem push)

| Repo | Branch | Worktree | Estado |
|---|---|---|---|
| api | `docs/redesign-f0` | checkout principal | F0: tabela de telas, ADRs 0010–0015, OpenAPI, PROGRESSO |
| api | `feat/redesign-f2` | `.claude/worktrees/redesign-f2` | F2 concluída: V16–V20, todos os endpoints; 339 testes, 0 falhas |
| ia | `feat/simulate-bucket` | `.claude/worktrees/simulate-bucket` | F4 concluída: `POST /v1/simulate/bucket`; 30 testes |
| front | `feat/redesign-f1` | `.claude/worktrees/redesign-f1` | F1 concluída: tokens, temas, componentes, Nav, rotas; 141 testes, Playwright 33/33 |
| front | `feat/redesign-f3` | `.claude/worktrees/redesign-f3` | F3 em 7 de 15 telas; 270 testes, `tsc` limpo |

## F3: onde parou

- Feito: nginx do `/api/stream`; `MapView` de 969 para 365 linhas; Mapa (SSE, contagem, pulso por alerta ativo); Ponto; Sensores; Alertas (ack, SSE, regras); Relatos; Dados; Pontos (admin).
- Interrompido: Modelo, no commit `39154c3` marcado `wip`. Revisar antes de seguir.
- Falta: Simulador, Maré, Evento, Relatório, Usuários (com convite), Minha rua, Login.
- Para retomar: branch `feat/redesign-f3`, mesmo briefing da F3, a partir da tela Modelo.

## Depende do autor

1. Aplicar V16–V20 no banco real, com dump e hash antes. Até lá, nada de compose com a branch da F2.
2. Push e PRs dos cinco ramos acima. Juntar `4bb976b` com `9c0ce42` antes: o primeiro reprova o ArchUnit isolado.
3. Backup novo no Drive, se o banco mudar.

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
