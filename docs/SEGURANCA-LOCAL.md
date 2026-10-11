# Segurança do ambiente local (docker compose)

O `docker-compose.yml` deste repositório é **só para desenvolvimento e demonstração local**. Ele
traz credenciais padrão conhecidas. Não use em servidor acessível pela rede sem antes trocar o que
está na tabela 2. Tarefa F1-QA-02, 2026-10-10. Achados de origem: F1-B2-12, F1-B2-13, F1-B2-14 e
F1-B3-21 (achados da fase 1, na tag `revisao-completa-2026-10-11`).

## 1. Exposição de portas

| Serviço | Porta no host | Escuta em | Risco |
|---|---|---|---|
| Postgres | 5433 | 127.0.0.1 (desde F1-QA-02; antes 0.0.0.0) | baixo localmente |
| MAPI AI | 8000 | 127.0.0.1 (desde F1-QA-02; antes 0.0.0.0) | baixo |
| Prometheus | 9090 | 127.0.0.1 | sem autenticação própria; aceitável só local |
| Grafana | 3001 | 127.0.0.1 | senha padrão; aceitável só local |
| MAPI API | 8080 | 0.0.0.0 | intencional, para acesso de outro dispositivo na apresentação; protegida por JWT |
| MAPI Front | 3000 | 0.0.0.0 | intencional (idem) |

## 2. Credenciais padrão e como trocar

| Item | Padrão | Variável para sobrescrever |
|---|---|---|
| Postgres (banco, API e IA) | `mapi123` | `POSTGRES_PASSWORD` |
| Grafana admin | `admin` / `mapi123` | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |
| Segredo do JWT | valor fixo publicado no compose (L50) | `JWT_SECRET` — **qualquer pessoa com o repositório consegue forjar tokens se o padrão for mantido** |
| Admin inicial | vazio | `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD` |

Defina num arquivo `.env` (não versionado) ao lado do `docker-compose.yml`.

## 3. Riscos que dependem de mudança de código (backlog, matriz item 10)

- O registro de usuário é aberto e dá acesso ao export de dados (F1-B2-12).
- O rate limit cobre só login e registro e pode ser contornado com `X-Forwarded-For` (F1-B2-13).
- O refresh token não é rotacionado (F1-B2-14).
- `DELETE /api/admin/ingestion/wipe-database` apaga todas as tabelas, inclusive a de usuários (F1-B3-21). Remover ou restringir depende da sua decisão (pergunta 10 do PROGRESSO.md).
