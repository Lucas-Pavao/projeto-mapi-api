# F2-BACK-12 — Completar o pipeline de ingestão (ADR 0006)

- Branch: `feat/pipeline-fontes` (a partir de `integracao/fase-2` em ca1cb91). Sem push. Nenhuma migração criada ou aplicada (V9 não foi necessária); `docker-compose.yml` intocado; nenhum endpoint removido.
- Status: itens 1 a 5 feitos, com testes verdes; item 6 (TabuaMare, Open-Meteo current/marine) ficou como pendência.

## Medidas (`./mvnw -q clean test`, JaCoCo 0.8.12 via CLI)

| | Base (ca1cb91) | Depois |
|---|---|---|
| Testes | 127 | 140 (0 falhas, 0 erros) |
| Linhas | 51,1% | 55,1% (2535 de 4603) |
| Ramos | 36,7% | 40,2% (887 de 2207) |

## 1. Convenção de rótulo horário do Open-Meteo (verificada)

Fonte: https://open-meteo.com/en/docs/historical-weather-api (e https://open-meteo.com/en/docs), consultadas em 2026-10-10. Trechos literais:

- "Total precipitation (rain, showers, snow) sum of the preceding hour."
- "Some variables like precipitation are calculated from the preceding hour as an average or sum."
- "Most weather variables are given as an instantaneous value for the indicated hour."
- Fuso: o padrão é GMT; "The requested timezone's UTC offset at request time is returned as utc_offset_seconds."

Conclusão: a precipitação rotulada `t` é a soma de `(t-1h, t]`, ou seja, rótulo no FIM do intervalo, igual à convenção do ADR (`variable_catalog.interval_convention = END`). **Nenhum ajuste no adaptador**; a citação foi para o Javadoc de `OpenMeteoArchiveAdapter`. A página não afirma "fim do intervalo" com essas palavras; a leitura "preceding hour" => fim é inferência direta do texto. Isso fecha o desvio 7 do F2-BACK-01. Commit `09959b9`.

## 2. CKAN no pipeline (T7)

- **Campo real**: o dataset `registro-de-atendimentos-da-defesa-civil` (datastore_search, recurso Atendimentos 2025) tem os campos `_id, Regional, Ano, Mês, Data, Ocorrencia, Solicitacao, Endereco, Bairro, Localidade, Grau_de_Risco, Data_da_Acao, Tipo_da_Acao, Quantidade, Altura, Largura, Area_m`. **Não há código Cobrade no dado.** Valores observados em 2024 (busca "alagamento"): `Ocorrencia` = "Alagamentos" (cerca de 116 registros), "Imóveis com Danos / Risco"; `Solicitacao` = "Vistoria Setor Alagado", "Vistoria Alagado", "Monitoramento Alagado". `Data` é `timestamp` e vem como `2024-05-28T00:00:00` (meia-noite).
- **Filtro adotado (limitação)**: texto, não Cobrade. Aceita `Ocorrencia` contendo "alagamento" (sem acento, caixa ignorada), lido de `label_filter` ("cobrade-alagamento", `accepted_texts`); termos "deslizamento", "enxurrada" e "inundação" excluem mesmo que "alagamento" também apareça. `Solicitacao` não entra (descreve o serviço, não o tipo). `cobrade_code` fica nulo e `cobrade_text` guarda `TEXT_MATCH:alagamento`. Contagens do filtro por ano não foram medidas.
- **Tempo**: `Data` à meia-noite = `DAY`, intervalo `[00:00, 24:00)` de Recife (UTC-3), nunca convertido em hora; com hora = `HOUR`, instante convertido de `America/Recife`, `[t, t+1µs)` (não se inventa duração). Data ilegível => quarentena `TS_UNPARSEABLE`. Nenhuma consulta de chuva (sem `alignFloodEventsToRainPeaks`).
- **Código**: `service/ingestion/occurrence` (`OccurrenceClassifier` puro, `OccurrenceIngestionService`), `OccurrenceRepository`. Fluxo: `ingestion_run` (`CKAN_RECIFE`) -> bruto em `raw_observation` (estação = `recurso#_id`, dedupe por hash da versão mais recente) -> `flood_occurrence` (revisão sobe só se o bruto mudou) -> geocoding fora da transação -> `occurrence_point_match` com `radius_m` e `linkage_config_id`. `rematch(raio)` cria/reutiliza o `linkage_config` do raio e grava novas linhas sem apagar as antigas. Raio inicial 3000 m (`mapi.ingestion.occurrence-radius-m`, `[sem fonte]`, continuidade do legado). Haversine em Java, ponto mais próximo dentro do raio.
- `CivilDefenseServiceImpl` agora também alimenta o pipeline (API e CSV) e o legado `FloodEvent` continua intacto. O sanitizador de endereço foi extraído para `CkanAddress` sem mudar a regra.
- **Teste T7**: `OccurrenceIngestionIntegrationTest` (3 casos: DAY/HOUR e filtro; troca de raio gera novas linhas; reimportação idempotente e revisão) e `OccurrenceClassifierTest` (6 casos). Commit `25d39e6`.

## 3. ANA, APAC meteorologia24h, Cemaden (T9)

- `SensorEnvelopeAdapter` + `AnaSoapAdapter` (`ANA_SOAP`), `ApacMeteo24hAdapter` (`APAC_METEO24H`), `CemadenAdapter` (`CEMADEN`), todos com `declaredTz = UNVERIFIED`. Os coletores (`AnaCollectorServiceImpl`, `ApacCollectorServiceImpl`) mantêm `sensorService.processSensorMessage` e, em seguida, chamam `LiveSensorStagingService.stage`, que usa o novo `IngestionPipelineService.executePushed` (run `LIVE`, sem intervalo). Falha do pipeline é só registrada e não afeta o legado.
- Resultado: bruto gravado, `ingestion_quarantine` com `TZ_UNVERIFIED`, nenhuma linha em `observation` até F2-DADOS-07 mudar `source_registry`; o teste mostra o `REVALIDATE` do bruto consolidando depois da verificação simulada.
- **T9**: `GET /api/sensors/{id}/latest` (e `/code/{code}/latest`) devolve `provenanceId` = run que gravou o mesmo envelope no bruto (recalculado pela chave de origem, sem migração; `SensorProvenanceResolver`). O teste confirma que o id existe em `ingestion_run` e que o `source` coincide (`ANA_SOAP`). `revision` fica nulo, porque não há observação consolidada enquanto o fuso é UNVERIFIED. O teste chama o `SensorController` direto (sem `spring-security-test` no projeto). Commit `1571abf`.
- Mapeamento provisório de variáveis (também depende de F2-DADOS-07): chuva acumulada vira `precipitation` com resolução `EVENT` e qualificador `WINDOW_UNVERIFIED` (janela desconhecida); nível d'água sem unidade (`UNIT_UNVERIFIED`). Resolução `EVENT` não entra em `observation_hourly_v`.
- Sem REFETCH por intervalo para essas fontes (só o dado corrente); REVALIDATE funciona.

## 4. Export sem imputação silenciosa

- Leitor da IA (`projeto-mapi-ai/mapi_ai/trainer.py:82`, `feature_engineering.py:230-308`): `pd.read_csv` e depois `prepare_scenario_features`, que **seleciona `df[FEATURE_COLUMNS]` por nome**. Colunas extras não quebram nada, e células vazias viram NaN. Esse leitor não consome o CSV do export diretamente (espera `current_rainfall`, `tide_level`, etc., de `flood_scenario_labels`); não há `ia-dataset` no repositório da IA.
- **Risco encontrado, fora do escopo do backend**: `prepare_scenario_features` faz `fillna(0.0)` nas features obrigatórias, ou seja, imputa 0 silenciosamente do lado da IA. Precisa de decisão na formulação do ML.
- Feito: `fillMissingValues` removido (não há mais 25 °C, 1013 hPa, 0 mm). Ausência = `null` no JSON e célula vazia no CSV (nunca o literal "null"). `accumulated{3,6,12,24,48}h` ficam vazios se a janela não tem N horas ou se alguma hora está sem precipitação (antes `calculateSum` tratava null como 0). `isFlooded` ausente continua `false` (ausência de ocorrência no rótulo, não imputação de feature).
- **Desvio de nome**: em vez de `<var>_imputed`, o CSV ganha `<var>_missing` (0/1) para 13 colunas, ao FIM do cabeçalho. Como nada é imputado, `_imputed` seria sempre 0; `_missing` carrega a informação útil. Se o autor preferir `_imputed`, é troca de nome.
- Manifesto: novos `missingPolicy` (texto) e `missingCounts` (contagem por coluna). Commit `4dc50c4`.

## 5. `Z` nos DTOs

- `UnifiedDataDTO`: novo campo aditivo `timestampUtc` (ISO-8601 com Z) e coluna `timestampUtc` no CSV; `timestamp` (sem fuso) permanece. O eixo da exportação passou a ser UTC de verdade (`LocalDateTime.now(UTC)`; antes usava o relógio da JVM, que aqui é UTC-3, enquanto a grade do clima era lida como UTC). Os campos `weather*` vêm do pipeline e valem para esse `Z`; os `sensor*` ainda vêm de `sensor_data` (hora local sem fuso, UNVERIFIED), alinhados pela hora crua, e o `Z` **não** atesta o fuso deles (documentado no Javadoc). Maré (`TideService`) idem.
- `SensorResponseDTO`: nenhum campo de tempo vem do pipeline novo (só `provenanceId`, que é UUID). `timestamp` fica sem `Z`, como no F2-BACK-01 (desvio 4), até a migração dos sensores.

## 6. TabuaMare e Open-Meteo current/marine — pendência

Sem adaptador. TabuaMare depende de `tide_tables.timezone` (a consultar no banco) e os coletores `current`/`marine` ainda usam `timezone=auto` (`source_registry` os marca `DECLARED` mas "só entram após trocar por GMT"). Fazer os dois antes da verificação de fuso seria rotular sem base.

## Pendências e riscos

1. Export ainda lê o rótulo de `flood_events` (legado), não de `flood_occurrence`/`occurrence_point_match`; falta expor `labelPrecision` e o `linkage_config`/`label_filter` no manifesto (ADR seções 6 e 2). É o próximo passo natural do T7.
2. Ocorrência que deixa de casar com o filtro depois de uma correção na fonte não é retirada de `flood_occurrence`.
3. O filtro por texto pode perder alagamentos descritos só em `Solicitacao` ("Vistoria Alagado") e a lista de recursos tem anos duplicados (24 recursos, 2025 três vezes); a deduplicação entre recursos repetidos não foi tratada (a chave natural é `recurso + _id`).
4. `docs/adr/0006` e `docs/INGESTAO.md` não estão na branch `integracao/fase-2` (só no checkout principal, não versionados nela); não foram editados. INGESTAO.md precisa registrar o filtro por texto, `executePushed` e `_missing`.
5. Fixtures de CKAN e sensores nos testes são sintéticos (formato observado), não respostas reais anonimizadas.
