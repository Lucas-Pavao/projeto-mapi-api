# Cemaden PED — levantamento, fuso e qualificação (2026-11-09)

DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.

Registro da etapa 1 da carga do Cemaden pela Plataforma de Entrega de Dados (ADRs 0021 e 0022). Os arquivos brutos desta etapa ficaram fora do git e foram baixados de novo pela carga do pipeline. Nenhum número abaixo entra na série consolidada sem passar pelo bruto.

## 1. Contrato da API

Fonte: `https://sws.cemaden.gov.br/PED/api/ui/swagger.json` (PED 2.0.2), lido em 2026-11-09.

| Rota | Uso no MAPi |
|---|---|
| `POST sgaa.cemaden.gov.br/SGAA/rest/controle-token/tokens` `{email, password}` | token JWT (fora do swagger; testado em 2026-11-09) |
| `GET /pcds-cadastro/dados-cadastrais?codibge=` | estações, coordenadas, `data_instalacao` |
| `GET /pcds/dados_pcd?rede=11&codigo=&sensor=10&inicio=&fim=` | chuva de uma estação, até 30 dias, síncrono |
| `GET /controle-agendamento/pcds-dados-historicos` | não usado (até 3 dias de espera; arquivo expira em 30 dias) |
| `GET /pcds-tipo-estacao/sensores` | tipos 1 (pluviométrica), 3 (hidrológica) e 10 (geotécnica) têm o sensor 10 (chuva) |

O token vai no cabeçalho `token`. HTTP 202 com "Info / Nenhum resultado foi encontrado para essa requisição!" = sem dado no intervalo.

Formato da `dados_pcd` (CSV com `;`):

```
OBS.: PCD com horario UTC!
cod.estacao;nome;municipio;uf;latitude;longitude;datahora;sensor;valor;qualificacao;offset
261160609A;Imbiribeira;RECIFE;PE;-8.120975;-34.913983;2022-05-27 00:10:00;chuva;0.2;0004;
```

## 2. Estações a até 5 km dos pontos (cadastro, coordenadas da PED)

| Ponto | Nº ≤ 5 km | Mais próxima |
|---|---|---|
| AV_RECIFE_IBURA | 7 | Areias (261160621A), 0,69 km |
| JABOATAO_CENTRO | 7 | Engenho Velho (260790119H), 0,59 km, instalada em 2023; Centro (260790107A), 0,64 km |
| MASCARENHAS_IMBIRIBEIRA | 5 | Imbiribeira (261160609A), 1,04 km |
| AGAMENON_DERBY | 8 | Torreão (261160618A), 2,07 km |
| CIN_UFPE | 9 | San Martin (261160619A), 3,49 km |

48 estações no cadastro de Recife, Jaboatão, Olinda e Camaragibe. Instalação: 2013–2014 (pluviométricas do Recife), 2019+ (geotécnicas), 2023 (hidrológicas de Jaboatão). O campo `dh_inicio_inativo` não marca desativação: aparece com data anterior à da última transmissão. O período de operação sai dos dados (`point_station_link.valid_from/valid_to`).

## 3. Resolução

Contagem dos intervalos entre leituras de chuva (Areias e Várzea; junho/2015, outubro/2021, maio/2022 e junho/2025):

- Carimbo sempre em minuto múltiplo de 10.
- Seco: bloco de 60 min.
- Com chuva, 2015: toda leitura com chuva vem 10 min depois da anterior (Várzea: 468 de 468).
- Com chuva, 2021 em diante: 10 a 60 min (Várzea, maio/2022: 446 com 10 min, 62 com 20, 35 com 30, 27 com 40, 24 com 50, 20 com 60).
- 2025: cobertura pior (Areias sem dado em junho/2025; Várzea com 80 leituras e buracos de até 21 h).

Só 3–6% das horas de maio/2022 têm seis leituras. Daí a regra do ADR 0021: duração do bloco = intervalo até a leitura anterior; bloco acima de 60 min na quarentena.

## 4. Fuso

Teste de defasagem, maio/2022, chuva horária do Cemaden (soma dos blocos com fim na hora) contra referências já no banco. Correlação de Pearson da série horária, defasagem de −6 a +6 h.

| Cemaden | Referência | Melhor defasagem | r na melhor | r em 0 h |
|---|---|---|---|---|
| Várzea 261160601A | ANA 39187800 (fuso verificado, V14) | **0 h** | 0,847 | 0,847 |
| Areias 261160621A | reanálise, célula do Ibura | 0 h | 0,522 | 0,522 |
| Porto 261160620A | reanálise, célula do Derby | 0 h | 0,524 | 0,524 |
| Várzea 261160601A | reanálise, célula do CIn | +1 h | 0,499 | 0,488 |

O cabeçalho do arquivo declara UTC e a referência de fuso verificado (ANA) tem o máximo em 0 h. Resultado: `CEMADEN_PED` = UTC, `VERIFIED` (V24). A reanálise, suavizada, não discrimina defasagem de ±1 h; não entra na decisão.

## 5. Qualificação

A documentação da PED não descreve o campo `qualificacao` (swagger e `/modelo/formatos` lidos; busca na web sem resultado, 2026-11-09). Contagem nas 28 006 leituras de chuva baixadas na etapa 1 (29 estações em maio/2022; Areias e Várzea em 2015, 2021 e 2025):

| qualificacao | chuva = 0 | chuva > 0 |
|---|---|---|
| `0000` | 16 150 | 0 |
| `0004` | 0 | 11 856 |

O código acompanha 100% das leituras com chuva > 0. Hipótese: indicador de "houve basculada no bloco", não de qualidade. Enquanto o Cemaden não responder, o código vai para `observation.qualifier` e nenhuma regra filtra por ele.

Pergunta para o Cemaden (canal de atendimento da PED):

> Na saída da rota `/pcds/dados_pcd` (formato CSV), o que significa o campo `qualificacao`? Observamos `0000` em toda leitura de chuva igual a zero e `0004` em toda leitura maior que zero. Há outros códigos possíveis, e algum deles indica dado suspeito ou corrigido? Há documentação dos códigos?

Resposta: pendente.

## 6. Maio/2022, comparação com a reanálise

Pico horário pela regra da V22 (fonte: `dados_pcd` de 29 estações e `observation_hourly_v` da reanálise, 2026-11-09).

| Ponto | Mais próxima com dado: pico (mm/h) | Maior pico ≤ 5 km | Reanálise |
|---|---|---|---|
| AV_RECIFE_IBURA | Areias: 30,8 | Ibura: 46,8 | 14,9 |
| MASCARENHAS_IMBIRIBEIRA | Imbiribeira: 19,3 | Pina: 32,0 | 14,9 |
| AGAMENON_DERBY | Porto: 34,8 | Campina do Barreto: 40,3 | 16,7 |
| CIN_UFPE | Brega e Chique: 35,0 | Várzea: 44,8 | 16,7 |
| JABOATAO_CENTRO | Centro: 23,7 | Curado 4: 46,6 | 13,8 |

Defeitos vistos no mesmo mês: Torreão 0,0 mm (vizinhas ~700 mm), Cavaleiro 65 mm, Imbiribeira 398 mm e Jaboatão Centro 354 mm (cerca de metade). Eles motivaram a checagem espacial do ADR 0022. **Correção depois da carga completa:** nas horas em comum com as vizinhas, Imbiribeira e Jaboatão Centro têm razão 0,92 e 0,87; o total baixo vinha de 15–17% de horas sem dado, não de medição ruim. Torreão (travado) e Cavaleiro (razão 0,09) são defeitos de fato.
