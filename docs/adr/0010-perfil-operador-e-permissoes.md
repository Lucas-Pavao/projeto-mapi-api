# 0010 — Perfil OPERADOR e matriz de permissões

- Status: **aprovado** (2026-10-21, Lucas, pergunta 32)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F0-REDESIGN-01

## Contexto

Hoje existem dois perfis (`Role {USER, ADMIN}`, `model/Role.java`; CHECK em `users.role`, `V1__baseline_schema.sql:141`). A autorização é feita só por `requestMatchers` em `SecurityConfig.java:36-52`; não há nenhum `@PreAuthorize` no código (fonte: `grep -rn PreAuthorize src/main/java`, 2026-10-21, 0 resultados).

As telas novas criam ações de operação que não devem exigir ADMIN (que também roda ingestão e terreno): reconhecer alerta, confirmar ou descartar relato, editar regra de alerta. Também não devem ficar abertas a qualquer USER, que pode se cadastrar sozinho (`POST /api/auth/register` é público).

## Opções consideradas

### A — Manter USER/ADMIN e dar as ações de operação ao ADMIN
A favor: nenhuma migração. Contra: todo operador da sala de situação vira ADMIN e pode apagar dado (ingestão, terreno). Fere o menor privilégio.

### B — Novo perfil OPERADOR, hierárquico (USER < OPERADOR < ADMIN)
A favor: um perfil a mais, regra simples. Contra: migração V16 no CHECK; um perfil por usuário.

### C — Permissões finas (tabela `permission`, vários papéis por usuário)
A favor: flexível. Contra: custo alto para três tipos de usuário; nenhum requisito pede isso hoje.

## Decisão

Opção B. Um perfil por usuário; ADMIN herda OPERADOR, OPERADOR herda USER (`RoleHierarchy` do Spring Security). Ligar `@EnableMethodSecurity` e anotar os serviços/controllers novos com `@PreAuthorize`; os `requestMatchers` atuais continuam como primeira barreira.

| Ação | Anônimo | USER | OPERADOR | ADMIN |
|---|---|---|---|---|
| `GET /api/public/risk` | sim | sim | sim | sim |
| Ver mapa, pontos, sensores, maré, linha do tempo, desempenho do modelo | não | sim | sim | sim |
| Enviar relato (`POST /api/reports`) | não | sim | sim | sim |
| Receber alertas pelo SSE, listar alertas | não | não | sim | sim |
| Reconhecer alerta (`ack`) | não | não | sim | sim |
| Confirmar ou descartar relato | não | não | sim | sim |
| Criar e editar regra de alerta | não | não | sim | sim |
| Simular o balde | não | sim | sim | sim |
| Criar, editar e ativar ponto | não | não | não | sim |
| Saúde dos dados, ingestão, terreno | não | não | não | sim |
| Listar usuários, trocar perfil, convidar | não | não | não | sim |

Convite: `POST /api/admin/invites` gera um token de uso único com validade de 72 h e perfil pré-definido; o cadastro com token recebe esse perfil. O link é exibido para o ADMIN copiar; envio por e-mail fica fora (sem servidor SMTP configurado).

Desativar usuário (`users.active = false`) bloqueia login e revoga os refresh tokens. Um ADMIN não pode rebaixar nem desativar a si mesmo, e o sistema mantém pelo menos um ADMIN ativo.

## Consequências

- Melhor: operador da sala de situação sem poder destrutivo.
- Pior: mais um perfil para testar; cada endpoint novo precisa de teste de autorização por perfil.
- Muda: V16 (CHECK de `users.role`, coluna `users.active`, tabela `user_invite`); `SecurityConfig` ganha `RoleHierarchy` e `@EnableMethodSecurity`.
- Medida: teste de integração com a matriz acima (um caso por linha e perfil), verde no CI.

## Condições que reabririam a decisão

- Necessidade de permissões por região (ex.: operador só de Jaboatão).
- Integração com login institucional (SSO) da Prefeitura.
