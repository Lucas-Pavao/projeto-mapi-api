# 0018 — Vínculo estação↔ponto com raio máximo e origem declarada

- Status: aprovado (2026-11-08, Lucas)
- Data: 2026-11-08
- Decisores: Claude (autor), Lucas
- Tarefa: F6-DADOS-01

## Contexto

A chuva horária por ponto vem do pluviômetro mais próximo, sem limite de distância. Com a ANA como única série horária de 5 anos, os 5 pontos caem na mesma estação (39187800), de 11,6 a 19,9 km, e as séries dos pontos ficam idênticas (`docs/EVENTOS-DE-CHUVA.md`, primeira extração). O Cemaden tem estação a até 5 km de todos os pontos (de 0,69 km em AV_RECIFE_IBURA a 4,79 km em JABOATAO_CENTRO; `docs/REVISAO-INGESTAO-2026-11.md`, seção 3). O raio hoje está no código (`nearestStation`), e o `linkage_config` do tipo `SENSOR_POINT` (3 km, V6) não é lido pelo export nem pelos eventos.

## Opções consideradas

### A — Manter o mais próximo, sem limite
A favor: sempre há valor. Contra: chuva convectiva a 10 km já é outro evento; dá aparência de dado medido no ponto.

### B — Raio máximo; dentro dele, a estação mais próxima; fora, reanálise declarada
A favor: simples, auditável, uma série por ponto e fonte. Contra: descarta estações vizinhas que poderiam cobrir lacunas.

### C — Raio máximo; dentro dele, média ou máximo das estações
A favor: menos lacuna. Contra: a média suaviza o pico (o mesmo defeito da reanálise); o máximo superestima a frequência de eventos fortes.

## Decisão

Opção B, com regra e raio em configuração gravada (`linkage_config`, `kind = SENSOR_POINT`, lida pelo export e pelos eventos), não em código.

- Raio máximo padrão: **5 km** `[sem fonte]`: com ele todos os pontos têm estação Cemaden e nenhum fica com a ANA a 11 km ou mais. Sensibilidade: 2 km e 10 km.
- Mais de uma estação no raio: a mais próxima; empate pelo código. Lacuna da mais próxima fica lacuna (sem completar com vizinha).
- Nenhuma estação no raio: a série do ponto vem da reanálise (`OPEN_METEO_ARCHIVE`), com `rainOrigin = REANALYSIS` na linha e no manifesto.
- Sempre: `stationId`, `stationDistanceM`, `linkageConfigId` na linha e no manifesto, como já acontece.

## Consequências

- Melhor: cada ponto tem a sua série; a distância deixa de ser escondida.
- Pior: enquanto o histórico do Cemaden não entrar, os 5 pontos usam a reanálise (a ANA fica fora do raio); a série do ponto muda de significado, e datasets antigos e novos não são comparáveis sem olhar o manifesto.
- Muda: export, eventos de chuva e `observation_hourly_v` por ponto passam a ler o `linkage_config`; os testes de vínculo passam a cobrir os casos "dentro", "fora" e "empate".
- Medida: número de pontos com chuva medida (hoje 5 com estação a mais de 11 km; esperado 5 com estação a até 5 km depois do Cemaden).

## Condições que reabririam a decisão

- Densidade de estações que permita interpolação espacial com validação cruzada.
- Evidência de que a estação mais próxima é sistematicamente pior que a segunda (falha de sensor, abrigo).
