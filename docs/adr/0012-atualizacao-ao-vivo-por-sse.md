# 0012 — Atualização ao vivo por Server-Sent Events

- Status: **aprovado** (2026-10-21, Lucas, pergunta 32)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F0-REDESIGN-03

## Contexto

O mapa ao vivo e a central de alertas precisam refletir novas leituras e previsões sem recarregar. Hoje o front consulta por polling. Os ciclos de coleta rodam a cada 3 min (Cemaden), 5 min (APAC) e 15 min (ANA) (`SensorCollectionTask.java:49-61`). O backend é Spring MVC (servlet), sem WebFlux.

## Opções consideradas

### A — Manter polling
A favor: nada novo. Contra: atraso de até um intervalo inteiro; requisições vazias na maior parte do tempo; não entrega alertas de forma imediata.

### B — Server-Sent Events (`SseEmitter`)
A favor: unidirecional (o caso aqui), roda no Spring MVC, reconexão automática no navegador com `Last-Event-ID`, passa por proxy HTTP comum. Contra: uma conexão aberta por aba; `EventSource` não envia cabeçalho `Authorization`.

### C — WebSocket (STOMP)
A favor: bidirecional. Contra: não há mensagem do cliente para o servidor; mais infraestrutura (broker, configuração do nginx).

## Decisão

Opção B: `GET /api/stream`, `text/event-stream`.

- Eventos: `prediction` (resumo por ponto: `slug`, `riskLevel`, `probability`, `issuedAt`, `modelValidationStatus`, `degradationReason`), `sensor` (`sensorId`, `type`, `value`, `observedAt`), `risk-summary` (contagem por nível), `alert` (só para OPERADOR e ADMIN, ADR 0010), `heartbeat` a cada 25 s.
- Autenticação: o front abre o stream com `fetch` + `ReadableStream` (biblioteca `@microsoft/fetch-event-source`), que envia o `Authorization: Bearer`. Sem token na query string, para não vazar em log.
- O publicador assina o evento interno de fim de ciclo (ADR 0011) e as gravações de leitura; não consulta o banco por cliente.
- Limite: 3 conexões por usuário; timeout do emitter de 30 min, com reconexão do cliente.
- nginx: `proxy_buffering off` e `proxy_read_timeout` ≥ 60 s na rota `/api/stream`.

## Consequências

- Melhor: alerta chega em segundos; menos requisições vazias.
- Pior: conexões longas ocupam threads do Tomcat. Com virtual threads do Java 21 (`spring.threads.virtual.enabled`) o custo cai; medir antes de ligar.
- Muda: uma dependência nova no front; configuração do nginx.
- Medida: k6 em container com 50 conexões SSE simultâneas por 10 min; p95 de `/api/pontos` não pode piorar mais de 10%.

## Condições que reabririam a decisão

- Mais de uma instância do backend (precisaria de pub/sub, ex.: `LISTEN/NOTIFY` do PostgreSQL).
- Necessidade de mensagem do cliente para o servidor.
