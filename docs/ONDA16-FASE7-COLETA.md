# Onda 16, Fase 7 — coleta e qualidade

- Data: 2026-11-23 · Status: aplicado (aval geral do Lucas de 22/11) · branch `onda16/fase7-coleta-qualidade` nos três repositórios

| Item | Resultado | Evidência |
|---|---|---|
| 7.1 Fuso da APAC meteorologia | **Inconclusivo; continua em quarentena.** O serviço `meteorologia24h` tem uma estação só (Cemaden 260790119H, Jaboatão Centro, Rio Duas Unas). As 141 leituras guardadas (09 a 23/11) e as 48 do dia têm 0 mm; a série da PED baixada termina em 31/10, sem sobreposição. O teste está pronto (`projeto-mapi-ai/mapi_ai/rain/fuso_apac.py`, regra escrita antes, testado com dado sintético) e roda quando houver 3 eventos com ≥ 5 mm/h nas duas fontes | `select count(*) ... from raw_observation where source = 'APAC_METEO24H'` em 23/11 (141; 0 com chuva); `curl http://dados.apac.pe.gov.br:41120/meteorologia24h/` em 23/11 00h25 local |
| 7.2 Treino × serviço no rain_v1 | **Corrigido.** O serviço usa a rede do treino (46 estações da PED): estação sem leitura fica no denominador de `net_valid_frac` e das frações de chuva. As 22 estações só ao vivo ficam fora das entradas até o próximo treino e vêm listadas em `stations_ignored_live_only`. Somas móveis arredondadas a 1e-6 mm no treino, no serviço e na simulação de atraso. Teste de paridade com tolerância escrita (1e-6 mm; frações iguais) em 3 instantes × 5 pontos. Diferença no instante da reprodução: de 1,13 mm para 0,28 mm no q50 de 6 h; o resto vem do dataset de treino salvo antes do arredondamento (reconstruir o dataset fica para o próximo treino) | `projeto-mapi-ai/tests/test_rain_paridade.py`; `python -m mapi_ai.flood.reproduce` em 23/11 |
| 7.3 Acumulado medido por ponto | **Feito.** `GET /api/pontos/{slug}/chuva-medida` lê `point_rain_accum_v` (chuva-ponto-v4: maior valor da hora entre as estações válidas a até 5 km; acumulado com hora sem valor é nulo). O card do ponto mostra a chuva de 3 h medida e, no rodapé, 6 e 24 h; sai a "estimada · Open-Meteo" | `PointMeasuredRainIntegrationTest`; `PointCard.test.tsx` |
| 7.4 Dados Vivos da SEDEC | **Não atualizam: sem coletor.** "Sedec Solicitações Tempo Real" tem 173 registros, todos de 29/03/2023; "SEDEC Vistorias Tempo Real" tem 1 registro, de 25/10/2021 (CKAN de dados.recife.pe.gov.br, conjunto "Demandas dos Cidadãos e Serviços – Dados Vivos Recife", licença ODbL, consultado em 23/11/2026 03h33 UTC). Só a faixa de datas foi lida. Os arquivos baixados foram para o armazenamento lacrado (`MAPI/rotulos-externos/lacrado/sedec/`, com SHA-256) | `datastore_search` com `sort=_id` e `fields=_id,ano,mes` |
| 7.5 Maré | A TabuaMare cobre até 01/01/2027 (extremos); a reserva harmônica (`TIDE_HARMONIC_F41`, UTide sobre a tábua DHN 2021–2024) cobre até 31/12/2027, o que inclui a quadra chuvosa de 2027. Sem maré num passo: com o refluxo em `NIVEL`, o ponto fica `SEM_DADO / MARE_AUSENTE`; em `INFO`, só o fator fica sem dado. As constantes harmônicas do CHM (pedido 01) não chegaram: o pedido não foi enviado | `select source, max(ts_utc) from observation_current where variable = 'tide_height'` em 23/11 |
| 7.6 Limiares do Duas Unas | Continuam só na tela. O próprio `meteorologia24h` traz 200/230/330 para a estação 260790119H, sem unidade. Pergunta no pedido novo à APAC (`docs/pedidos/10-apac-duas-unas-limiares-e-fuso.md`, não enviado), que também pergunta o fuso e o histórico | — |
| 7.7 Operação | (a) Limite de conexões ao vivo por usuário: 3 → 10, configurável (`mapi.stream.max-connections-per-user`). (b) Histórico do Open-Meteo por modelo: carga na subida, desligada por padrão (`MAPI_OPEN_METEO_HISTORY_LOAD=true` uma vez); disparada no implante desta fase. (c) Snapshot do OpenAPI: `/v3/api-docs` é público; `docs/openapi/openapi-2026-11-23.json` | `OpenMeteoHistoryStartupLoader`; `LiveStreamServiceImpl` |

## Também nesta fase

- O pedido 08 (Jaboatão) passou para a posição nova do ponto (Fase 3); o quadrado de 600 m antigo não cobria a Av. Barão de Lucena.

## Pendências

- Rodar `fuso_apac.py` quando a estação 260790119H tiver 3 eventos com chuva nas duas fontes; até lá, quarentena.
- Reconstruir o dataset do rain_v1 com o arredondamento novo no próximo treino (e decidir, no mesmo treino, as 22 estações só
  ao vivo).
- Enviar os pedidos 01 (constantes do CHM) e 10 (APAC): só o Lucas envia.
