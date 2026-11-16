# Lacunas da chuva por ponto (chuva-ponto-v4)

Extraído em 2026-11-16 de `point_rain_hourly` (`rule_version = 'chuva-ponto-v4'`), 5 pontos, 45 659 horas por ponto, de 2021-09-01 01:00 a 2026-11-16 11:00 UTC. Consultas: `docs/extratos/sql/lacunas-chuva-ponto.sql`. Lacuna = hora sem valor (nenhuma estação válida a até 5 km com leitura, ADR 0022).

DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.

## 1. Fração de horas sem valor

| Ponto | Horas | Lacunas | % | Em lacunas ≤ 24 h | Em lacunas > 24 h |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 45 659 | 109 | 0,24 | 109 | 0 |
| AV_RECIFE_IBURA | 45 659 | 1 858 | 4,07 | 354 | 1 504 |
| CIN_UFPE | 45 659 | 177 | 0,39 | 177 | 0 |
| JABOATAO_CENTRO | 45 659 | 82 | 0,18 | 82 | 0 |
| MASCARENHAS_IMBIRIBEIRA | 45 659 | 1 621 | 3,55 | 133 | 1 488 |

Por ano (% de horas sem valor):

| Ponto | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 |
|---|---|---|---|---|---|---|
| AGAMENON_DERBY | 0,24 | 0,64 | 0,16 | 0,33 | 0,03 | 0,00 |
| AV_RECIFE_IBURA | 0,17 | 0,64 | 0,23 | 8,72 | 7,10 | 5,07 |
| CIN_UFPE | 0,17 | 0,67 | 0,17 | 0,26 | 0,66 | 0,22 |
| JABOATAO_CENTRO | 0,72 | 0,68 | 0,00 | 0,00 | 0,00 | 0,01 |
| MASCARENHAS_IMBIRIBEIRA | 0,20 | 0,64 | 0,26 | 8,72 | 8,49 | 0,34 |

2021 começa em setembro; 2026 vai até 16/11.

## 2. Duração das lacunas

| Ponto | 1 h | 2–6 h | 7–24 h | > 24 h | Maior |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 9 | 12 (36 h) | 5 (64 h) | 0 | 22 h |
| AV_RECIFE_IBURA | 41 | 27 (85 h) | 16 (228 h) | 6 (1 504 h) | 744 h |
| CIN_UFPE | 29 | 33 (76 h) | 6 (72 h) | 0 | 23 h |
| JABOATAO_CENTRO | 10 | 8 (26 h) | 4 (46 h) | 0 | 17 h |
| MASCARENHAS_IMBIRIBEIRA | 11 | 16 (49 h) | 6 (73 h) | 2 (1 488 h) | 744 h |

Número de lacunas; entre parênteses, as horas somadas. As lacunas de mais de 24 h são meses inteiros (até 744 h = 31 dias): nenhuma estação do raio passou na checagem do mês. São de natureza diferente das curtas (falha de transmissão).

## 3. A lacuna depende da chuva?

Referência independente: a chuva de reanálise do Open-Meteo no mesmo ponto e hora (`OPEN_METEO_ARCHIVE`, até 2026-11-01), porque na lacuna não há chuva medida.

Taxa de lacuna curta (≤ 24 h) por faixa de chuva de reanálise:

| Chuva de reanálise na hora | Horas | Em lacuna curta | % |
|---|---|---|---|
| 0 mm | 120 381 | 434 | 0,361 |
| (0; 1) mm | 96 054 | 396 | 0,412 |
| [1; 5) mm | 6 530 | 23 | 0,352 |
| ≥ 5 mm | 493 | 2 | 0,406 |

Perfil da chuva de reanálise por tipo de hora:

| Tipo de hora | Horas | % com chuva | % ≥ 1 mm | % ≥ 5 mm | Média (mm) |
|---|---|---|---|---|---|
| com valor | 222 603 | 46,1 | 3,14 | 0,22 | 0,172 |
| lacuna ≤ 24 h | 855 | 49,2 | 2,92 | 0,23 | 0,157 |
| lacuna > 24 h | 2 992 | 42,4 | 1,57 | 0,07 | 0,120 |

**Conclusão.** A lacuna curta ocorre na mesma taxa (0,35% a 0,41% das horas) em hora seca e em hora de chuva: é independente da chuva, até onde a reanálise permite ver. Limites da medida: a reanálise suaviza picos (pico horário de 21,1 mm/h contra 62 mm/h medidos), e a faixa ≥ 5 mm tem só 2 horas em lacuna. Não há sinal de "estação que cai na tempestade".

Segunda medida, com a chuva medida da hora anterior (início de lacuna depois de hora com valor): 0,062% depois de hora seca, 0,24% a 0,34% depois de hora com chuva de até 10 mm, e 0 em 1 799 horas depois de 10 mm ou mais. O início de lacuna é mais frequente em tempo chuvoso, mas não na chuva forte.

## 4. Lacunas simultâneas

Das horas com lacuna curta, 56 atingem os 5 pontos ao mesmo tempo e 37 atingem 4 (queda da rede ou da carga); 337 atingem um ponto só.

## 5. Como simular (Etapa B2 do modelo v3)

1. Lacuna curta: independente da chuva, com a taxa por ponto da seção 1 (coluna "≤ 24 h") e a distribuição de duração da seção 2. Parte delas é simultânea entre pontos (seção 4).
2. Lacuna longa (mês inteiro): só em AV_RECIFE_IBURA e MASCARENHAS_IMBIRIBEIRA; em produção vira `UNKNOWN` pela regra de degradação (ADR 0007, emenda 3), então não é caso de previsão com entrada ausente.
