# F1-BACK-03 — Revisão da ingestão histórica (Agente 1, Backend + Dados)

Escopo: `projeto-mapi-api`, somente leitura, branch `revisao/fase-0`. Caminhos relativos a `src/main/java/com/projeto/mapi/` (abreviado `m/`) salvo indicação.
Severidade: critico / importante / desejavel. Esforço: P (<0,5 dia), M (1–3 dias), G (>3 dias). `[DISSERTACAO]` = afeta rastreabilidade ou validade científica.
Limites desta revisão: não houve acesso ao banco nem chamadas às APIs externas. O que depende do conteúdo real das fontes está marcado como **a verificar** com o teste sugerido.

## Resumo

- Nenhum endpoint de ingestão é um pipeline repetível de verdade: são cargas pontuais com deduplicação ad hoc. `weather_data` **não tem constraint única** e a "idempotência" é a heurística `>8000 linhas no ano` (`HistoricalDataServiceImpl.java:234`); reprocessar o ano corrente ou um ano incompleto duplica linhas.
- O fuso é heterogêneo dentro da mesma tabela: Open-Meteo histórico sem `timezone` (GMT por padrão), Open-Meteo coletor com `timezone=auto` (hora de Recife), ANA histórico com `-3h` assumindo UTC (sem citação), ANA ao vivo sem deslocamento, APAC/Cemaden/CKAN/TabuaMare sem tratamento e sem documentação no código.
- Proveniência: histórico APAC/ANA não grava `raw_data`, nem horário de coleta, nem versão; correção retroativa da agência é impossível (primeiro valor gravado vence).
- Granularidades são misturadas na mesma coluna `accumulated_precipitation` (diário APAC, sub-horário ANA/Cemaden) e o export toma o **máximo** por hora: o total diário da APAC aparece como pico às 00:00.
- Rótulo circular: `alignFloodEventsToRainPeaks` move o início do evento para o pico de chuva (sobrescreve o dado original), então rótulo e feature são correlatos por construção.
- Raios de vínculo: 3 km (repair, align, export, CKAN, dual-link) e 2 km (APAC); 10 km é só log de depuração. Nenhum é configurável nem gravado como metadado.
- Volume (5 pontos, 5 anos): ~219 mil linhas em `weather_data` (~55 MB); o custo real está na coleta ao vivo de `sensor_data` com `raw_data` (estimativa de ordem de GB/ano, premissas na seção 6).

## 1. Backfill pontual ou pipeline repetível? Reprocessar duplica?

Todos os endpoints estão em `controller/HistoricalDataController.java` (`/api/admin/ingestion/**`, ADMIN: `security/SecurityConfig.java:50`), disparam `@Async` e retornam 202; não há registro de execução (run id, intervalo, contagens, erro).

| Fluxo | Evidência | Reprocessar o intervalo? |
|---|---|---|
| `historical-full-sync?years=5` (`:66`) → `ingestHistoricalData` | `HistoricalDataServiceImpl.java:55-95`: semeia pilotos, clima por ponto, ANA, APAC "TODOS" por ano, CKAN, depois alinhamento | Composto; herda os comportamentos abaixo. `startYear = endYear - years` (`:61`) cobre `years+1` anos civis; CKAN usa `endYear - years + 1` (`CivilDefenseServiceImpl.java:231`): janelas diferentes no mesmo comando |
| Clima Open-Meteo | `:226-265`; tabela sem unique (`V1__baseline_schema.sql:50-64`; `model/WeatherData.java:8-9` só índice) | **Duplica.** Só pula o ano se já houver >8000 linhas (`:234`). Ano corrente tem <8000 até dezembro, logo cada execução reinsere de 1º/jan até hoje-2 (`:240-241`); ano parcialmente carregado (ex.: 7000 linhas) é rebaixado inteiro e duplica 7000. Ano com 8001–8759 linhas nunca é completado |
| ANA histórico | `AnaHistoricalServiceImpl.java:122` (select + skip), unique `(sensor_id,timestamp)` (`V1:36`) | Idempotente por inserir-se-ausente, **primeiro valor vence**. Duas linhas com mesmo timestamp no mesmo XML não são checadas contra o lote: `saveAll` (`:144-150`) lança violação e o resto do ano da estação é perdido (exceção capturada em `:65-67`) |
| APAC histórico | `ApacHistoricalServiceImpl.java:275` (select) e `:292-300` | Idempotente, mas falha de unique em um lote de 500 é engolida como "provável duplicata" e descarta também as linhas novas do lote |
| CKAN Defesa Civil | `FloodEventServiceImpl.java:69-76`: um evento por (ponto, **dia**) | Idempotente, mas com perda: dois alagamentos no mesmo dia/ponto colapsam em um. Pacote "dados vivos" não depende do ano (`CivilDefenseServiceImpl.java:239-241`), logo o mesmo recurso é reprocessado `years` vezes por execução. `ingestFloodEvents` é chamado por `this` (`:246-247`), então o `@Async` não vale (sem proxy) |
| `align-events` (`:73`) | `HistoricalDataServiceImpl.java:99-159` | **Destrutivo**: sobrescreve `start_time`/`end_time` (`:151-154`) sem guardar o original; depois do primeiro alinhamento o dado "00:00 por falta de hora" deixa de ser distinguível |
| `repair-stations` (`:86`) | `:355-428` | Operação de curadoria síncrona, não ingestão. Idempotente (Set), nunca remove vínculo. Depende da data de execução: usa só sensores com leitura nos últimos 7 dias (`:366`) |
| `tide-sync` / tarefa diária | `TideTableSyncServiceImpl.java:235-242` (delete + insert por porto/ano) | Idempotente por substituição. Mês vazio após 3 tentativas é pulado (`:131-135`) e a tarefa `syncCurrentYearIfMissing` (`:71-78`) nunca repara, pois o ano "já existe" |
| `POST /api/pontos/scenarios` | `controller/MapiController.java:60-65`; `service/impl/MapiServiceImpl.java:631-714` | Não é backfill: tira um instantâneo **no instante da chamada** (`LocalDateTime.now()`), com clima/maré/onda atuais. Cada POST insere nova linha (sem unique). O DTO não aceita data (`dto/FloodScenarioLabelRequestDTO.java`): impossível rotular evento passado. Não entra no export |
| `wipe-database` (`:100`) | `HistoricalDataServiceImpl.java:339-353` | Apaga usuários, tokens, eventos, sensores, clima, marés e pontos (não apaga `flood_scenario_labels`/`flood_predictions`). Sem confirmação; um DELETE de ADMIN destrói a base da dissertação |

Conclusão: é carga pontual com dedupe parcial. Para pipeline repetível faltam chave natural em `weather_data`, registro de execução e política explícita de correção (ver seção 10).

## 2. Proveniência: o que existe e o que falta

Existe: `sensor_data.source` ("APAC_PORTAL" `ApacHistoricalServiceImpl.java:282`; "ANA_HISTORICAL" `AnaHistoricalServiceImpl.java:128`; fonte textual no coletor ao vivo), `code` (GMMC/código ANA), `station_name`, `lat/lon`, `raw_data` **somente no fluxo ao vivo** (`SensorServiceImpl.java` ~`:157`), `tide_tables` guarda `timezone`, instituição e `card` da DHN (`TideTableSyncServiceImpl.java:112-121`), `weather_data.created_at` (`@PrePersist`, `model/WeatherData.java:52-54`).

Falta:
- **ANA histórico sobrescreve `sensor_id` pelo slug do ponto** (`AnaHistoricalServiceImpl.java:83-91`): perde a identidade da estação no campo chave (fica só em `code`). Dois sensores do mesmo ponto (pluviômetro e fluviométrico) compartilham `(slug, timestamp)`; o segundo é descartado silenciosamente pela deduplicação (`:122`). [DISSERTACAO]
- Histórico APAC/ANA: sem `raw_data`, sem instante de coleta (`created_at` não existe em `sensor_data`), sem URL/parâmetros da consulta, sem versão do portal. [DISSERTACAO]
- Open-Meteo: sem modelo/reanálise de origem, sem `elevation`/grade (o DTO lê `elevation` mas não grava; `dto/WeatherArchiveResponseDTO.java:9`), sem coordenada da grade real (grava a requisitada, `HistoricalDataServiceImpl.java:271-273`; o coletor grava a do payload `WeatherServiceImpl.java:73-74`, então as linhas do coletor **não casam** com a igualdade exata usada no export `DataExportServiceImpl.java:75`). Sem `weather_data.source`.
- CKAN: não grava id do registro, id do recurso/pacote, endereço original como campo, coordenadas geocodificadas nem a distância ao ponto; só `confirmed_by = "DEFESA_CIVIL_RECIFE"` e a descrição concatenada (`CivilDefenseServiceImpl.java:269-270`, `FloodEventServiceImpl.java:83`). Severidade fixa MEDIUM (`:268`). [DISSERTACAO]
- Vínculo sensor↔ponto: só ids em tabelas de junção (`V1` `flood_point_pluviometers` etc.), sem raio, distância, data do vínculo, nem quem criou (repair, APAC 2 km, dual-link 3 km).
- Nenhum campo de versão/correção da agência, nem de qualidade (flag de validação).
- Identificador de proveniência exposto nas respostas: inexistente (`UnifiedDataDTO` não tem fonte nem estação).

## 3. Lacuna, fora de faixa, unidade, correção retroativa

- **Lacuna**: `fillMissingValues` (`DataExportServiceImpl.java:199-234`) inventa valores: chuva de sensor ausente vira 0,0 (`:211`), nível d'água assume 0,0 inicial (`:207`), umidade do solo 30,0 (`:206`), temperatura 25,0 (`:203`), pressão 1013,0 (`:204`), weatherCode 0; sem coluna de ausência. [DISSERTACAO] No ao vivo, `chuva` ilegível vira 0,0 (`ApacCollectorServiceImpl.java` `parseCemaden`, `parseDoubleOrDefault(..., 0.0)` ~`:129`). O coletor ao vivo guarda só a leitura mais recente por estação por ciclo (`:66-80`), embora o endpoint devolva ~150 leituras de histórico rolante: indisponibilidade do coletor causa perda permanente dos intermediários.
- **Fora de faixa**: não há validação alguma (chuva negativa, nível sentinela, pressão absurda). ANA usa `Double.parseDouble` sem try (`AnaHistoricalServiceImpl.java:132,135,139`); um valor malformado aborta o ano da estação. `parseTimestamp` cai para `LocalDateTime.now()` quando não consegue ler a data (`SensorServiceImpl.java:247,261`), gravando leitura com instante errado. [DISSERTACAO]
- **Unidade**: fixada por suposição. ANA define `unit="mm"` e depois sobrescreve com `"m"` se houver nível (`:131-139`); coluna `unit` fica enganosa para linhas com chuva e nível. Nível: ANA em m (suposto), APAC `nivel_rio` sem conversão. Nenhuma checagem de mudança de unidade ou de datum.
- **Correção retroativa**: inexistente. Dedupe por `(sensor_id,timestamp)` mantém o primeiro valor (ANA `:122`, APAC `:275`, ao vivo `SensorServiceImpl.java:150`). Efeito concreto: o relatório APAC do mês corrente é lido com `end = agora` (`ApacHistoricalServiceImpl.java:64`); o total do dia parcial é gravado e **nunca atualizado**. Open-Meteo archive é reanálise que pode ser revisada; ano com >8000 linhas nunca é rebaixado.
- `checkDataIntegrity` declara "OK" se `weatherCount > 30000` (`HistoricalDataServiceImpl.java:333`), sem olhar lacunas, duplicatas nem sensores.

## 4. Granularidade por fonte e reamostragem

| Fonte | Granularidade armazenada | Evidência |
|---|---|---|
| APAC histórico (portal HTML) | **Diária**, timestamp 00:00 | `ApacHistoricalServiceImpl.java:189` |
| ANA SOAP histórico | Conforme a fonte (provavelmente 15 min a 1 h; **a verificar**) | `AnaHistoricalServiceImpl.java:95-154`, nenhuma agregação |
| Cemaden (via endpoint APAC `cemaden`) | Ao vivo: poll de 3 min, 1 leitura/estação/ciclo; sem histórico | `SensorCollectionTask.java:37`; `ApacCollectorServiceImpl.java:66-80` |
| APAC meteorologia24h | Ao vivo: poll de 5 min | `SensorCollectionTask.java:43` |
| Open-Meteo clima | Archive: horária (7 variáveis); coletor: instantâneo "current" (15 min na fonte) a cada 1 h | `HistoricalDataServiceImpl.java:252`; `DataCollectionTask.java:21` |
| Open-Meteo marine | **Nenhuma série**: só leitura pontual no cenário; fora do export | `MarineServiceImpl.java:29-37,64-90` |
| TabuaMare/DHN | 3–4 extremos por dia (preamar/baixa-mar) | `TideServiceImpl.java:132-136` |
| CKAN Defesa Civil | Evento; normalmente só data (→ 00:00) | `CivilDefenseServiceImpl.java:279,282` |

Reamostragem no export (`DataExportServiceImpl.java`): linha do tempo horária contínua (`:81-86`, início fixo 2021-01-01 se `days=0`, `:58`); sensores truncados à hora e **máximo** entre estações para chuva e nível (`:90-108`), última leitura vence para umidade do solo (`:118`); clima: último vence na hora, sem tratar duplicatas (`:124-133`); maré recalculada por interpolação cossenoidal entre extremos do **mesmo dia** (`TideServiceImpl.java:158-187`), com degrau nos limites do dia (`:182-186`) e uma consulta `findAllByYear` com carga completa por hora exportada (`:109`, 43,8 mil chamadas por ponto em 5 anos); acumulados 3/6/12/24/48 h somam só `weatherPrecipitation` (Open-Meteo), **ignorando os pluviômetros** (`:188-197`, `:178-182`).

Consequências: (a) total diário APAC (00:00) e leituras horárias ANA/Cemaden na mesma coluna e agregados por máximo geram pico espúrio às 00:00; (b) os dados da ANA nunca chegam ao export: o histórico não grava `lat/lon` (`AnaHistoricalServiceImpl.java:126-131`) e o export busca por raio (`:62-63`) e por `weatherStationIds` (`:66-71`), não por `pluviometerStationIds`. [DISSERTACAO]

## 5. Fuso por fonte (insumo direto do ADR 0001)

O ADR 0001 decide UTC na persistência. As colunas atuais são `TIMESTAMP WITHOUT TIME ZONE` (`V1:18,51,80,101`) e `hibernate.jdbc.time_zone: UTC` (`application.yml:21`). Atenção: o efeito desse parâmetro sobre `LocalDateTime` depende do fuso da JVM e **deve ser verificado empiricamente** (inserir instante conhecido e ler com `psql`) antes de qualquer migração.

| Fonte | Fuso de origem (evidência) | Tratamento atual | Correção necessária para UTC |
|---|---|---|---|
| Open-Meteo archive (`weather_data` histórico) | Consulta sem `timezone` (`HistoricalDataServiceImpl.java:245-253`) → padrão GMT segundo a documentação Open-Meteo (não citada no código; **confirmar** com `utc_offset_seconds` da resposta, que o DTO ignora). Formato `2024-01-01T00:00` sem offset | `LocalDateTime.parse` (`:274`), grava como veio | Histórico já está em UTC: só rotular, sem somar horas. Passar `timezone=GMT` explícito e parsear como UTC |
| Open-Meteo forecast/current (coletor → `weather_data`) | `timezone=auto` (`WeatherServiceImpl.java:47`) → hora local de Recife (UTC−3), `2026-10-10T14:15` sem offset | Parse direto (`:75`), grava como veio na mesma tabela do item anterior | Mudar para `timezone=GMT`. Linhas existentes do coletor: +3 h. Distinguir das de archive pelos minutos (archive sempre `:00`; coletor tem minutos variáveis) e por lat/lon (coletor usa a grade da resposta); ambas têm `created_at` |
| Open-Meteo marine | `timezone=auto` (`MarineServiceImpl.java:37`) → local; só uso pontual | Compara com `OffsetDateTime.now()` do servidor (`:70`) e cai para `LocalDateTime.now().getHour()` (`:80`); se a JVM estiver em UTC, desvia 3 h | `timezone=GMT` e comparar com `Instant`; hoje não há série gravada, só valor em `flood_scenario_labels` |
| ANA SOAP histórico | Formatos aceitos: ISO, `dd/MM/yyyy HH:mm:ss` e `yyyy-MM-dd HH:mm:ss` (`:103-111`). Fuso **não documentado**; comentário afirma "ANA/CEMADEN enviam dados em UTC" sem citação (`:117-118`) | `ISO_DATE_TIME` descarta offset eventual; `minusHours(3)` incondicional (`:119`) | **A verificar** antes de migrar: correlação cruzada com Open-Meteo/APAC em tempestade conhecida e leitura de um XML bruto. Se a ANA entrega hora local, o `-3h` atual está errado (deslocamento de 3 h) e a conversão correta é +3 h sobre a hora local. Rótulo da fonte deve ser declarado por parser |
| ANA ao vivo (`Data_Hora_Medicao`, via `SensorServiceImpl.java:58-65`) | Não documentado | **Sem** deslocamento: inconsistente com o histórico da mesma agência | Mesmo teste acima; unificar a regra por fonte |
| APAC histórico (portal) | Datas diárias sem hora (`ano/mês` + dia); dia civil local presumido | `LocalDateTime.of(y,m,d,0,0)` (`:189`) | Tratar como dia civil `America/Recife`: 00:00 local = 03:00 UTC; melhor modelar como intervalo diário `[00:00,24:00)` local e não instante |
| APAC meteorologia24h / Cemaden ao vivo (`Data-hora`) | Formato e fuso não documentados no código | Via `parseTimestamp` (`SensorServiceImpl.java:245-262`): ISO/`dd/MM/yyyy`/`yyyy-MM-dd` sem fuso; falha vira `now()` | **A verificar** em amostra do `raw_data` (campo `Data-hora`) e comparação com o horário de coleta; se local, +3 h para UTC. Eliminar o fallback `now()` |
| TabuaMare/DHN | Horas `HH:mm:ss` por dia (`TideServiceImpl.java:189-205`); a tabela guarda `timezone` da fonte (`TideTableSyncServiceImpl.java:117`) mas o código a ignora | Interpreta como relógio local do timestamp consultado (`:137-149`) | Ler `tide_tables.timezone` (`SELECT DISTINCT timezone`); converter dia/hora ao instante UTC com esse fuso; hoje a consulta compara hora local do chamador com hora da tábua |
| CKAN Defesa Civil | Datas `dd/MM/yyyy[ HH:mm:ss]`, ISO ou `yyyy-MM-dd` sem fuso (`CivilDefenseServiceImpl.java:273-285`); em geral só data | `LocalDateTime` (offset ISO descartado); data pura → 00:00 | Assumir `America/Recife` e documentar; data sem hora deve virar intervalo diário com precisão "dia", não 00:00 como se fosse instante |
| Relógio do servidor (`now()`) | JVM, fuso não declarado (ADR 0001) | 24 usos de `LocalDateTime.now()`, inclusive `created_at`, janelas do export e cenário | `Clock`/`Instant`, `TZ=UTC` no container |

## 6. Volume projetado

Pontos críticos: **5** pilotos semeados (`service/impl/MapiServiceImpl.java:573-620`; slugs `AV_RECIFE_IBURA`, `CIN_UFPE`, `AGAMENON_DERBY`, `JABOATAO_CENTRO`, `MASCARENHAS_IMBIRIBEIRA`). Não há `data.sql`; novos pontos entram por `POST /api/pontos`.

Conta-base: pontos × horas × anos = 5 × 8.760 × 5 = **219.000 ponto-horas** (219.150 com bissexto).

| Hypertable | Variáveis | Linhas (5 pontos, 5 anos) | Valores | Bytes/linha (estimativa) | Tamanho |
|---|---|---|---|---|---|
| `weather_data` (archive) | 7 pedidas (temperatura, umidade, pressão, precipitação, weather_code, vento, radiação) | 219.000 | 219.000 × 7 = 1.533.000 | ~136 de heap + ~112 de 3 índices ≈ 250 | ≈ 55 MB |
| `weather_data` (coletor 1/h) | 9 | +43.800 por ano | — | 250 | ≈ 11 MB/ano |
| `sensor_data` histórico APAC diário | 1 (chuva) | S × 365 × 5; com S=150 estações (premissa a confirmar com `SELECT count(DISTINCT sensor_id)`): 273.750 | 273.750 | ~200 + ~160 de 4 índices ≈ 360 | ≈ 99 MB |
| `sensor_data` ao vivo (Cemaden/APAC) | 1–3 por linha + `raw_data` | R × 144 × 365 por ano; R=100 estações, leitura nova a cada 10 min (premissa): 5,26 milhões/ano | — | ~200 + `raw_data` ~500 + índices ~160 ≈ 900 | ≈ 4,7 GB/ano |
| `flood_scenario_labels` | 21 colunas | 1 por POST | — | ~250 | desprezível |
| Export (não persistido) | 17 colunas | 219.000 | 219.000 × 17 = 3,7 milhões | ~150 B de CSV | ≈ 33 MB |

Escala do export em memória (`generateCsv` monta uma `String` única, `DataExportServiceImpl.java:254-280`; o controller devolve `String`): 50 pontos ≈ 330 MB; 200 pontos (8,76 milhões de linhas) ≈ 1,3 GB, inviável sem streaming. Nenhum `compress_chunk`/retention nas migrations: `raw_data` é o fator dominante de crescimento. As cifras de bytes são estimativas de ordem de grandeza (cabeçalho de tupla 24 B, alinhamento, sem TOAST); validar com `pg_total_relation_size` após carga.

## 7. Bruto × tratado: vale separar?

Sim. Evidências do código:
1. `alignFloodEventsToRainPeaks` destrói o instante original do evento (`HistoricalDataServiceImpl.java:151-154`).
2. `ANA_HISTORICAL` troca `sensor_id` por slug e `-3h` incondicional: não é possível desfazer sem a resposta original (`AnaHistoricalServiceImpl.java:83-91,119`).
3. `fillMissingValues` e `max` por hora vivem no export, misturando tratamento com exposição (`DataExportServiceImpl.java:89-122,199-234`).
4. Sem `raw_data` no histórico, qualquer reinterpretação de fuso (ADR 0001) exige rebaixar a fonte, que pode ter sido revisada.
5. Correções retroativas só são possíveis se o bruto é versionado (hoje primeiro valor vence).

Bruto = append-only, na unidade/fuso da fonte, com payload, instante de coleta e hash; tratado = série consolidada reconstruível do bruto por regras versionadas. Detalhe na seção 10.

## 8. Raios de vínculo

| Uso | Raio | Onde | Configurável? | Gravado como metadado? |
|---|---|---|---|---|
| repair-stations: sensor mais próximo → pluviômetro do ponto | 3,0 km (único vizinho mais próximo; sem coordenadas → vincula a **todos** os pontos) | `HistoricalDataServiceImpl.java:413` (`:374-387` para sem coordenadas) | Não (literal) | Não; só o id na junção. Log mostra distância (`:420`) |
| Vínculo ao carregar APAC histórico | 2,0 km (primeiro encontrado, não o mais próximo) | `ApacHistoricalServiceImpl.java:240` | Não | Não |
| Dual-link ao vivo | 3,0 km | `SensorServiceImpl.java:106` | Não | Não |
| Alinhamento de evento ao pico de chuva | 3,0 km | `HistoricalDataServiceImpl.java:113` | Não | Não |
| Export (sensores por raio) | 3,0 km | `DataExportServiceImpl.java:63`; query `repository/SensorDataRepository.java:31-34` | Não | Não |
| Ocorrência CKAN ↔ ponto | **3,0 km** (mais próximo dentro do raio; erro se fora) | `FloodEventServiceImpl.java:63` | Não | Não (distância e coordenadas geocodificadas não são gravadas) |
| 10 km | Apenas **log de depuração** de pontos próximos | `FloodEventServiceImpl.java:57-59` | — | — (não é critério de casamento) |
| Descoberta ANA | 50 km | `config/AppProperties.java:45` | Sim (única) | Não |

Observações: o export usa o raio, enquanto a ingestão ANA usa o mapeamento por id; são duas definições diferentes de "sensor do ponto" que podem divergir. A fórmula de distância existe em duas versões (Haversine `util/GeoUtils.java:4-12` e lei dos cossenos em JPQL `SensorDataRepository.java:31-39`); no JPQL, `acos` de argumento ligeiramente >1 por arredondamento (sensor exatamente sobre o ponto) pode causar erro de domínio no PostgreSQL (**a verificar**). Haversine em si está correto (R=6371 km). A decisão de 3 km é única em um parâmetro e deveria ser registrada por ponto/sensor. [DISSERTACAO]

## 9. Export CSV (`GET /api/export/ia-dataset/{slug}/csv`)

Controller `controller/DataExportController.java:48-61`; geração `DataExportServiceImpl.java:254-280`.

- **Colunas** (`:256`): `floodPointSlug, timestamp, sensorPrecipitation, sensorWaterLevel, sensorSoilHumidity, weatherPrecipitation, weatherTemperature, weatherPressure, weatherCode, tideHeight, accumulated3h, accumulated6h, accumulated12h, accumulated24h, accumulated48h, isFlooded, severity`.
- **Unidades**: não constam no cabeçalho. Presumidas: mm (chuva), m (nível/maré), °C, hPa, % solo. Sem fuso: `timestamp` é `LocalDateTime.toString()` ("2026-10-10T14:00", sem segundos quando zero), enquanto o JSON usa padrão com segundos (`dto/UnifiedDataDTO.java`). Valores nulos saem como a palavra `null` (`String.format("%s")`).
- **Rótulos**: `isFlooded` = hora dentro de `[início truncado, fim]` de qualquer evento do ponto; sem fim, +3 h (`:244-251`); severidade do primeiro evento (`:158-165`). Sem eventos para o ponto, tudo é `false`: ausência de registro vira "não alagou" (negativo falso). CKAN registra reclamações, não ausência de alagamento.
- **Vazamento de informação futura** (análise por coluna):
  - Acumulados olham para trás (`:191`), mas incluem a hora corrente; correto só se a previsão for feita no fim dessa hora.
  - **Rótulo circular**: após `alignFloodEventsToRainPeaks`, o início do evento é, por construção, a hora de máxima chuva regional (`HistoricalDataServiceImpl.java:120-151`). Features de chuva e rótulo ficam correlacionados por procedimento, não por fenômeno. [DISSERTACAO]
  - Rótulo na mesma hora das features: o dataset não tem alvo deslocado para o horizonte H; quem treina precisa deslocar, e o export não impede erro. Janela +3 h após o pico marca como "alagado" horas posteriores ao pico.
  - Preenchimento: `fillMissingValues` usa só LOCF (passado), sem vazamento direto, mas introduz constantes inventadas.
  - Maré vem da tábua astronômica: previsível, legítima como feature futura (glossário).
  - Treino com reanálise Open-Meteo e inferência com previsão: assimetria treino/serviço (não é vazamento, é viés).
  - Split por tempo não é garantido pelo export; ordem por ponto, sem fold.
- **Outros**: `days=0` usa 2021-01-01 fixo; `exportAllPointsData` usa `parallelStream` com acesso JPA (`:37-39`); CSV inteiro em `String`.

## 10. Proposta de arquitetura alvo (insumo para ADR futuro F2-DADOS-05; não é decisão)

Princípios: bruto imutável e versionado; tratamento determinístico e reexecutável; UTC no tratado; identificador de proveniência em cada linha e em cada resposta; migração aditiva sem apagar o histórico carregado.

```mermaid
flowchart LR
  subgraph Fontes
    A[APAC portal] --- B[ANA SOAP] --- C[Cemaden/APAC ao vivo] --- D[Open-Meteo] --- E[TabuaMare/DHN] --- F[CKAN Recife]
  end
  Fontes --> I[Ingestor por fonte\n(parser declara fuso)]
  I --> R[(raw_observation\nappend-only\nsource, station_id, payload,\nsource_ts_text, source_tz,\ningested_at, fetch_run_id, content_hash)]
  I --> RUN[(ingestion_run\nintervalo, versão do parser,\ncontagens, erros)]
  R --> V[Validação\nfaixa, unidade, duplicata,\nlacuna, fuso]
  V -->|rejeita| Q[(quarantine\nmotivo + regra)]
  V --> S[(observation_curated\nhypertable UTC, timestamptz\nUNIQUE source,station,ts,variable\nquality_flag, provenance_id,\nrule_version, superseded_by)]
  S --> E2[Export/API\nprovenance_id nas respostas]
  S --> L[(flood_label\nevento original + versão alinhada\nmétodo, raio, distância)]
  L --> E2
```

Pontos-chave:
1. **Staging bruto**: `raw_observation` guarda o texto original de data, o fuso declarado pelo parser (`source_tz`), payload e `content_hash`; nova versão da agência = nova linha (não sobrescreve); `ingestion_run` registra intervalo e versão do parser. Substitui o dedupe "primeiro vence".
2. **Validação** como serviço puro com regras versionadas (`rule_version`): faixas físicas, unidade esperada por variável, reconhecimento de sentinelas, detecção de duplicata e lacuna; rejeitados vão para `quarantine` com motivo. Nunca preencher com constante; ausência = `NULL` + `quality_flag`.
3. **Série consolidada** em `timestamptz` UTC, chave natural `(source, station_id, variable, ts_utc)` com upsert `ON CONFLICT DO UPDATE` condicionado a versão mais nova (a anterior fica apontada por `superseded_by`). Variável explícita por linha (formato longo) resolve a mistura diário × horário: cada linha leva `resolution` (`PT5M`, `PT1H`, `P1D`). Reamostragem horária fica na camada de export, com regra documentada e sem usar `max` entre resoluções diferentes.
4. **Rótulos**: guardar o evento CKAN original (id do registro, recurso, endereço, coordenadas geocodificadas, precisão "dia/hora") e o alinhamento como tabela derivada (método, raio, distância, `rule_version`), sem sobrescrever o original. Vínculos sensor↔ponto em tabela `point_station_link(raio_km, distância_km, método, vigência)`.
5. **Idempotência**: toda carga é por `(fonte, estação, intervalo)`; rerrodar produz o mesmo estado (upsert + hash). `weather_data` ganha `UNIQUE (lat, lon, timestamp)` (inclui a coluna de particionamento, exigência do TimescaleDB).
6. **Migração incremental sem perder histórico** (Flyway, cada passo aprovado separadamente, conforme ADR 0001): (a) criar tabelas novas sem tocar nas atuais; (b) copiar o histórico existente para `raw_observation` marcado `legacy=true`, com `source_tz` inferido por fonte conforme a tabela da seção 5 depois da verificação empírica, `ingested_at` desconhecido; (c) gerar a série consolidada a partir do bruto legado; (d) comparar contagens e somatórios por fonte/ano com as tabelas antigas; (e) migrar leitores (export, API) para a série nova e só então `ALTER ... TYPE timestamptz` ou aposentar as antigas. Fazer backup antes de qualquer (b).
7. **Identificador exposto**: `provenanceId` (ou `source` + `stationId` + `ingestedAt` + `ruleVersion`) nas respostas de `/api/export/ia-dataset` e nos DTOs de sensor/clima; no CSV, colunas adicionais e um arquivo de manifesto (versão do dataset, fuso, raios, regras).
8. **Operação**: `wipe-database` removido ou restrito a ambiente de teste; `years` validado; execução registrada em `ingestion_run`.

## Achados

Detalhamento (esforço entre parênteses):

- **F1-B3-01 (critico, M) `weather_data` sem chave única e dedupe por heurística** — `HistoricalDataServiceImpl.java:234`, `V1__baseline_schema.sql:50-64`. Duplica ao reprocessar ano corrente/parcial.
- **F1-B3-02 (critico, G) Fuso heterogêneo na mesma coluna** — seção 5. [DISSERTACAO]
- **F1-B3-03 (critico, P) `-3h` da ANA sem fonte e ANA ao vivo sem deslocamento** — `AnaHistoricalServiceImpl.java:117-119`; `SensorServiceImpl.java:58-65`. [DISSERTACAO]
- **F1-B3-04 (critico, M) Rótulo circular por alinhamento ao pico de chuva e sobrescrita do original** — `HistoricalDataServiceImpl.java:99-159`; `DataExportServiceImpl.java:244-251`. [DISSERTACAO]
- **F1-B3-05 (critico, M) Dados ANA nunca chegam ao export (sem lat/lon, `sensor_id` = slug)** — `AnaHistoricalServiceImpl.java:83-91,126-131`; `DataExportServiceImpl.java:62-71`. [DISSERTACAO]
- **F1-B3-06 (critico, M) `sensor_id` sobrescrito pelo slug causa colisão silenciosa entre estações** — `AnaHistoricalServiceImpl.java:83-91,122`. [DISSERTACAO]
- **F1-B3-07 (importante, M) Granularidades misturadas e `max` por hora (pico diário APAC às 00:00)** — `ApacHistoricalServiceImpl.java:189`; `DataExportServiceImpl.java:94-100`. [DISSERTACAO]
- **F1-B3-08 (importante, M) Imputação por constantes e ausência vira 0** — `DataExportServiceImpl.java:199-234`; `ApacCollectorServiceImpl.java` (`parseDoubleOrDefault 0.0`). [DISSERTACAO]
- **F1-B3-09 (importante, M) Sem proveniência no histórico (sem `raw_data`, coleta, versão, fonte em clima)** — seção 2. [DISSERTACAO]
- **F1-B3-10 (importante, G) Sem correção retroativa; dia parcial APAC congelado** — `ApacHistoricalServiceImpl.java:64,275`; `AnaHistoricalServiceImpl.java:122`. [DISSERTACAO]
- **F1-B3-11 (importante, P) Raios (2/3 km) fixos no código, não configuráveis nem gravados; 10 km é só log** — seção 8. [DISSERTACAO]
- **F1-B3-12 (importante, M) CKAN: dedupe por dia/ponto, severidade fixa, sem id/coordenada/distância, filtro por palavras (não Cobrade), reprocesso `years` vezes do pacote "vivo"** — `FloodEventServiceImpl.java:63,69-76`; `CivilDefenseServiceImpl.java:192-200,239-241`. [DISSERTACAO]
- **F1-B3-13 (importante, P) `repair-stations` depende dos últimos 7 dias e vincula estação sem coordenadas a todos os pontos** — `HistoricalDataServiceImpl.java:366,374-387`. [DISSERTACAO]
- **F1-B3-14 (importante, P) Parser com fallback `now()` e `parseDouble` sem tratamento** — `SensorServiceImpl.java:247,261`; `AnaHistoricalServiceImpl.java:132-139`. [DISSERTACAO]
- **F1-B3-15 (importante, M) Coletor ao vivo guarda só a última leitura por estação; perda de histórico rolante** — `ApacCollectorServiceImpl.java:66-80`. [DISSERTACAO]
- **F1-B3-16 (importante, M) Falha de lote APAC engolida e sem registro de execução/integridade** — `ApacHistoricalServiceImpl.java:292-300`; `HistoricalDataServiceImpl.java:333`.
- **F1-B3-17 (importante, M) Export: maré por hora com `findAllByYear` + log por chamada; CSV em `String`; `parallelStream` com JPA** — `TideServiceImpl.java:101-156`; `DataExportServiceImpl.java:37-39,254-280`.
- **F1-B3-18 (importante, P) Maré: `timezone` da DHN ignorado; mês vazio nunca reparado; degrau nos limites do dia** — `TideTableSyncServiceImpl.java:117,131-135`; `TideServiceImpl.java:182-186`. [DISSERTACAO]
- **F1-B3-19 (importante, M) Linhas do coletor Open-Meteo usam lat/lon da grade e não casam com igualdade exata do export** — `WeatherServiceImpl.java:73-74`; `DataExportServiceImpl.java:75`. [DISSERTACAO]
- **F1-B3-20 (importante, M) Cenários: instantâneo "agora", sem data, sem idempotência, fora do export** — `MapiServiceImpl.java:631-714`; `dto/FloodScenarioLabelRequestDTO.java`. [DISSERTACAO]
- **F1-B3-21 (importante, P) `wipe-database` apaga usuários e a base inteira por um DELETE** — `HistoricalDataController.java:100`; `HistoricalDataServiceImpl.java:339-353`.
- **F1-B3-22 (desejavel, P) Acumulados do export só usam Open-Meteo e CSV sem unidades/fuso/`null` literal/manifesto** — `DataExportServiceImpl.java:188-197,256-277`. [DISSERTACAO]
- **F1-B3-23 (desejavel, P) Fórmula de distância duplicada (Haversine × lei dos cossenos com `acos` sem clamp)** — `GeoUtils.java:4-12`; `SensorDataRepository.java:31-39`.
- **F1-B3-24 (desejavel, P) Janelas de anos inconsistentes (`years+1` × `years`) e `years` sem validação** — `HistoricalDataServiceImpl.java:60-61`; `CivilDefenseServiceImpl.java:231`.
- **F1-B3-25 (desejavel, P) `@Async` em auto-chamada (CKAN) sem efeito** — `CivilDefenseServiceImpl.java:246-247`.
- **F1-B3-26 (importante, G) Arquitetura staging → validação → consolidado (proposta, seção 10)** — novo ADR F2-DADOS-05. [DISSERTACAO]

## Tabela de achados

| id | título | severidade | dissertação | arquivos | métrica |
|---|---|---|---|---|---|
| F1-B3-01 | `weather_data` sem unique; dedupe >8000 linhas | critico | n | HistoricalDataServiceImpl.java:234; V1:50-64 | 0 constraints únicas; duplica no ano corrente |
| F1-B3-02 | Fuso heterogêneo por fonte na mesma coluna | critico | s | HistoricalDataServiceImpl.java:245; WeatherServiceImpl.java:47; AnaHistoricalServiceImpl.java:119 | 9 fontes/caminhos, 5 tratamentos distintos |
| F1-B3-03 | `-3h` ANA sem citação; ANA ao vivo sem deslocamento | critico | s | AnaHistoricalServiceImpl.java:117-119; SensorServiceImpl.java:58-65 | erro potencial de 3 h |
| F1-B3-04 | Rótulo circular (alinhamento ao pico, sobrescreve original) | critico | s | HistoricalDataServiceImpl.java:99-159; DataExportServiceImpl.java:244 | 100% dos eventos 00:00 realinhados |
| F1-B3-05 | ANA fora do export (sem lat/lon) | critico | s | AnaHistoricalServiceImpl.java:83-131; DataExportServiceImpl.java:62-71 | 0 linhas ANA no export |
| F1-B3-06 | `sensor_id`=slug causa colisão entre estações | critico | s | AnaHistoricalServiceImpl.java:83-91,122 | 1 estação por (ponto, timestamp) |
| F1-B3-07 | Diário × sub-horário com `max` por hora | importante | s | ApacHistoricalServiceImpl.java:189; DataExportServiceImpl.java:94 | pico espúrio 1/dia/estação |
| F1-B3-08 | Imputação por constantes; ausência vira 0 | importante | s | DataExportServiceImpl.java:199-234 | 6 constantes inventadas |
| F1-B3-09 | Sem proveniência no histórico | importante | s | ApacHistoricalServiceImpl.java:276; AnaHistoricalServiceImpl.java:126 | 0 campos de versão/coleta/payload |
| F1-B3-10 | Sem correção retroativa; dia parcial congelado | importante | s | ApacHistoricalServiceImpl.java:64,275 | primeiro valor vence |
| F1-B3-11 | Raios fixos e não gravados | importante | s | HistoricalDataServiceImpl.java:413; ApacHistoricalServiceImpl.java:240; FloodEventServiceImpl.java:63 | 6 usos de 3 km, 1 de 2 km, 0 configuráveis |
| F1-B3-12 | CKAN: dedupe por dia, severidade fixa, sem id/coord | importante | s | FloodEventServiceImpl.java:69-83; CivilDefenseServiceImpl.java:192-270 | 1 evento/ponto/dia |
| F1-B3-13 | repair-stations: janela de 7 dias; meteo a todos | importante | s | HistoricalDataServiceImpl.java:366-387 | 5/5 pontos recebem toda estação sem coordenadas |
| F1-B3-14 | Fallback `now()` e `parseDouble` cru | importante | s | SensorServiceImpl.java:247,261; AnaHistoricalServiceImpl.java:132 | 2 fallbacks silenciosos |
| F1-B3-15 | Coletor ao vivo descarta leituras intermediárias | importante | s | ApacCollectorServiceImpl.java:66-80 | 1 de ~150 leituras/estação/ciclo |
| F1-B3-16 | Lote APAC engolido; integridade só conta linhas | importante | n | ApacHistoricalServiceImpl.java:292-300; HistoricalDataServiceImpl.java:333 | limiar fixo 30000 |
| F1-B3-17 | Export lento/pesado em memória | importante | n | TideServiceImpl.java:101-156; DataExportServiceImpl.java:254-280 | ~43,8 mil consultas de maré/ponto; ~33 MB (5 pts) a 1,3 GB (200 pts) |
| F1-B3-18 | Maré: fuso ignorado, mês vazio sem reparo | importante | s | TideTableSyncServiceImpl.java:117,131; TideServiceImpl.java:182 | 0 usos de `tide_tables.timezone` |
| F1-B3-19 | Clima do coletor não casa com igualdade lat/lon | importante | s | WeatherServiceImpl.java:73; DataExportServiceImpl.java:75 | 1 grade ≠ coordenada do ponto |
| F1-B3-20 | Cenários só "agora", sem data, fora do export | importante | s | MapiServiceImpl.java:631-714 | 0 cenários no dataset |
| F1-B3-21 | `wipe-database` destrói tudo | importante | n | HistoricalDataController.java:100; HistoricalDataServiceImpl.java:339 | 7 repositórios apagados |
| F1-B3-22 | Acumulados só Open-Meteo; CSV sem unidade/fuso | desejavel | s | DataExportServiceImpl.java:188-197,256 | 5 janelas só de reanálise |
| F1-B3-23 | Duas fórmulas de distância; `acos` sem clamp | desejavel | n | GeoUtils.java:4; SensorDataRepository.java:31-39 | 2 implementações |
| F1-B3-24 | Janelas de anos inconsistentes | desejavel | n | HistoricalDataServiceImpl.java:61; CivilDefenseServiceImpl.java:231 | `years+1` vs `years` |
| F1-B3-25 | `@Async` por auto-chamada sem efeito | desejavel | n | CivilDefenseServiceImpl.java:246 | 2 chamadas |
| F1-B3-26 | Arquitetura staging→validação→consolidado | importante | s | (proposta, ADR F2-DADOS-05) | 3 camadas, 1 `provenanceId` |
