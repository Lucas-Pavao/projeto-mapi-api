# 0013 — Endpoint público "Minha rua"

- Status: **aprovado** (2026-10-21, Lucas, pergunta 32)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F0-REDESIGN-04

## Contexto

A tela "Minha rua" do canvas deixa qualquer pessoa, sem conta, ver o risco perto de um endereço. Hoje toda rota fora de `/api/auth/*`, `GET /api/sensors/**` e `GET /api/weather/**` exige login (`SecurityConfig.java:37-52`).

Riscos: o modelo não está validado (ADR 0007 emenda 1); o público pode ler a classe como garantia. Um endpoint aberto pode ser raspado ou usado para carga. Só existem 5 pontos críticos; um endereço longe deles não tem previsão.

## Opções consideradas

### A — Não ter página pública
A favor: nenhum risco de interpretação pública de um modelo não validado. Contra: perde a parte "para quem vive a cidade" do produto.

### B — Endpoint público mínimo, com limite de taxa e sem dado pessoal
A favor: entrega a tela com superfície pequena. Contra: precisa de limite de taxa e de texto de aviso forte.

### C — Página pública com mapa completo (todos os pontos, sensores)
A favor: mais informação. Contra: expõe tudo sem login; maior custo de abuso.

## Decisão

Opção B: `GET /api/public/risk?lat&lon`.

- Entrada: `lat`, `lon` dentro da caixa da RMR; fora dela, 422. Coordenadas arredondadas para 3 casas (~110 m) antes de qualquer uso. Nenhum log grava a coordenada recebida.
- Saída: o ponto crítico mais próximo dentro de 1 km (`slug`, `name`, `distanceM` arredondada a 50 m, `riskLevel`, `issuedAt`, `validUntil`, `modelValidationStatus`, `degradationReason`), a chuva da última hora do pluviômetro mais próximo e o texto de `RISK_MEANING`. Sem ponto em 1 km: `nearestPoint = null` e a mensagem "Não há ponto monitorado perto deste endereço."
- Sem probabilidade numérica na resposta pública: só a classe, com `ExperimentalBadge` obrigatório no front.
- Limite de taxa: 30 requisições por minuto por IP, no mesmo padrão do `LoginRateLimitingFilter`; acima disso, 429 com `Retry-After`.
- Geocodificação do endereço no navegador (fora do backend); o backend só recebe coordenada.
- Cache de 60 s por célula arredondada.

## Consequências

- Melhor: o cidadão vê o risco perto de casa sem cadastro.
- Pior: classe experimental exposta ao público; mitigado pelo selo e pelo texto fixo.
- Muda: `SecurityConfig` libera só `GET /api/public/**`; filtro de taxa novo.
- Medida: IT com 31 requisições em 60 s → a 31ª recebe 429.

## Condições que reabririam a decisão

- Pedido da Defesa Civil para não expor previsão experimental.
- Abuso medido (> 10 000 requisições por dia de um mesmo /24).
