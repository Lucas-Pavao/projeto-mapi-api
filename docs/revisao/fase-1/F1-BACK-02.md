# F1-BACK-02 — Leitura dirigida do backend

Escopo: leitura de código (nenhum arquivo de código alterado). Base: `src/main/java/com/projeto/mapi` (147 arquivos .java, 8.922 linhas em `main`). Fora de escopo: ingestão histórica (`HistoricalDataServiceImpl`, `*HistoricalServiceImpl`, `repair-stations`). Decisões ADR 0001–0004 não foram reabertas. Nada foi executado; onde a conclusão depende de comportamento padrão de biblioteca (não visto no código) isso está marcado como "inferência".

Legenda: severidade critico/importante/desejavel; esforço P/M/G; `[DISSERTACAO]` = afeta validação, rastreabilidade ou reprodutibilidade.

---

## 1. Aderência à arquitetura

Medição "antes" do ArchUnit (grep de imports e assinaturas):

| Regra | Violações hoje | Evidência |
|---|---|---|
| Controller não depende de `repository` | 0 | nenhum import de `repository` em `controller/*.java` |
| Controller não devolve entidade de `model` (tipo de retorno, inclusive genérico) | 0 | todos os `ResponseEntity<...>` são DTO, `JsonNode`, `Double`, `Page<DTO>` |
| Controller não depende de `model` | 1 | `AuthController.java:9` (`import model.User`), usado em `:76` (`@AuthenticationPrincipal User`) |
| Controller não depende de `service.impl` | 0 | grep vazio |
| `dto` não depende de `model` | 2 | `FloodEventDTO`, `ScraperEventDTO` |
| Interface de `service` não expõe `model` | 1 | `SensorService.java:19` (`List<model.FloodPoint> getFloodPointsCache()`) |
| `service`/`config` não depende de `controller` | 0 | grep vazio |
| Repositório acessado só por `service`/`config` | 0 (fora de service: `ApplicationConfig`, `AdminBootstrapSeeder`, ambos em `config`) | |
| Regra de negócio em controller | 0 achados relevantes | maior lógica é `if (event == null)` (`FloodEventController.java:35`) e leitura de cookie (`AuthController.java:128`) |

Outras observações de aderência:
- DTO como record (agents.md §5): 26 de 36 arquivos em `dto/` não contêm `record` (a skill cita 25; contagem por `grep -L "record "` deu 26, divergência de 1 a conferir no ArchUnit). Sem MapStruct (só `TideMapper` manual).
- `MapiServiceImpl` tem 775 linhas e mistura sensores, clima, maré, IA, pontos e auditoria (F1-B2-23).
- `SensorCollectionTask` está em `service/sensor/`, não em `config/` como agents.md §1 diz (documentação desatualizada).

### Classe `ArchitectureTest` proposta (descrição; sem código)

Dependência a adicionar (hoje ausente do `pom.xml`): `com.tngtech.archunit:archunit-junit5`. Classe `com.projeto.mapi.ArchitectureTest`, `@AnalyzeClasses(packages = "com.projeto.mapi", importOptions = DoNotIncludeTests.class)`. Regras e o resultado esperado **hoje**:

1. `controllersNaoUsamRepositorio`: `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAPackage("..repository..")`. Esperado: 0 (passa).
2. `controllersNaoExpoemEntidade`: condição customizada sobre o tipo de retorno genérico (`JavaMethod.getReturnType().getAllInvolvedRawTypes()` em `..model..`), pois `haveRawReturnType` só veria `ResponseEntity`. Esperado: 0.
3. `controllersNaoDependemDeModel`: `noClasses()...controller...dependOnClassesThat().resideInAPackage("..model..")`. Esperado: 1 (`AuthController`). Tratar com `@ArchIgnore`/`FreezingArchRule` ou trocar `User` por `UserDetails`/DTO.
4. `dtoNaoDependeDeModel`. Esperado: 2 (congelar com `FreezingArchRule`).
5. `servicosPublicosNaoExpoemModel`: métodos públicos de interfaces em `..service..` (fora de `..impl..`) sem tipo de retorno em `..model..`. Esperado: 1 (`SensorService.getFloodPointsCache`).
6. `camadasRespeitadas` (`layeredArchitecture`): Controller só acessível por ninguém; Service acessível por Controller/Config; Repository acessível por Service/Config/Security. Esperado: 0 se `Config` e `Security` forem permitidos. `JwtAuthenticationFilter` usa `UserDetailsService`, não repositório.
7. `servicoNaoDependeDeController`. Esperado: 0.
8. Opcional: `slices().matching("com.projeto.mapi.(*)..").should().beFreeOfCycles()` — número de violações desconhecido; medir antes de ativar.

Total esperado ao rodar tudo congelado: 4 violações pré-existentes (1+2+1) mais o que o item 8 revelar.

**F1-B2-22** desejavel, esforço P: criar o teste e congelar as 4 violações.

---

## 2. Jobs de coleta

### 2.1 Agenda e execução

- `SensorCollectionTask.java:28-47`: três `@Scheduled(fixedRate)`: ANA 900.000 ms (15 min), APAC/Cemaden 180.000 ms (3 min), APAC/Meteorologia24h 300.000 ms (5 min). `application.yml` não define `spring.task.scheduling.pool.size`; não existe bean `TaskScheduler`.
- Inferência (padrão do Spring Boot, não visto no código): o agendador tem 1 thread. Logo ANA, Cemaden, Meteo, `DataCollectionTask` (1 h, `DataCollectionTask.java:28`), `TideTableSyncTask` (24 h), limpeza de cache (10 min, `CacheConfig.java:24`) e limpeza do rate limit rodam em série na mesma thread. O `taskExecutor` **não** é usado pelo agendador: ele só recebe `processSensorMessage` (`SensorServiceImpl.java:34`, `@Async("taskExecutor")`) e os jobs históricos.
- `ApplicationConfig.java:67-72`: `taskExecutor` com core=5, max=10, fila=100, prefixo `Ingest-`, política de rejeição padrão (sem `setRejectedExecutionHandler`, inferência: `AbortPolicy`). Capacidade efetiva antes de rejeitar: 10 threads + 100 na fila = 110 tarefas pendentes.
- agents.md §3.2 pede "delegar a persistência e a chamada de predição do modelo a pools separados": há um único pool, e a predição **não** é chamada pelo job (item 8).

### 2.2 Timeouts

Nenhum timeout configurado em lugar algum: `grep` por `Timeout|setConnectTimeout|setReadTimeout|Duration.of` só encontra `LoginRateLimitingFilter.java:37` (janela de 1 min) e `AuthController.java:96` (cookie). Todos os clientes usam `RestClient.builder()` cru (`ApplicationConfig.java:58`, `ApacScrapeClientImpl.java:34`, `FloodPredictionServiceImpl.java:21`, `NominatimGeocodingServiceImpl.java:22`, `CivilDefenseServiceImpl.java:31,108`, etc.) e `RestTemplate` sem timeout (`ApplicationConfig.java:63`). Inferência: sem timeout, o cliente HTTP do JDK espera indefinidamente. O único trecho com limite é o `@RateLimiter` da TabuaMare (`timeout-duration: 5s`, `application.yml`). Comentário de `AnaDataClientImpl` diz que a autenticação da ANA responde em ~30 s quando funciona.

**F1-B2-01** critico, esforço P, [DISSERTACAO]: sem timeout em nenhum cliente externo (APAC, ANA, IA, Open-Meteo); uma conexão pendurada bloqueia a única thread do agendador e para todas as coletas. Evidência: `ApacScrapeClientImpl.java:33-40`, `AnaDataClientImpl.java:60-64`, `FloodPredictionServiceImpl.java:20-24`.

**F1-B2-02** importante, esforço P: agendador de 1 thread (inferência) com ANA sequencial por estação (`AnaCollectorServiceImpl.java:63-66`, uma chamada HTTP + possível inventário por estação). Um ciclo lento atrasa os demais; `fixedRate` acumula execuções atrasadas. Definir `spring.task.scheduling.pool.size` ou `TaskScheduler` explícito.

### 2.3 Retry e circuit breaker (`application.yml`)

| Instância | CB: janela / mín. chamadas / limiar / espera aberto / half-open | Retry |
|---|---|---|
| `apacScrape` (`:138`) | 10 / 5 / 50% / 30 s / 3 | 3 tentativas, 1 s, só `ResourceAccessException` e `HttpServerErrorException` (`:164`) |
| `anaData` | 10 / 5 / 50% / 30 s / 3 | 3 tentativas, 1 s, mesmas exceções |
| `openMeteo`, `tabuaMare` | idem | 3 tentativas, 500 ms |
| IA (`/v1/predict/flood`) | **nenhum** | **nenhum** |

Problema estrutural: `ApacScrapeClientImpl.java:43-44` e `AnaDataClientImpl.java:67-68` usam `@Retry` + `@CircuitBreaker(fallbackMethod=...)`; o fallback (`:69-72` e `:98-101` aprox.) captura a exceção e devolve `List.of()`. Pela ordem padrão dos aspectos do Resilience4j (Retry externo, CB interno com fallback; inferência), o `Retry` nunca enxerga exceção. Os 3 attempts configurados não acontecem; o CB conta a falha mas o chamador recebe lista vazia. O próprio código reconhece o problema para o token da ANA (comentário em `AnaDataClientImpl`, retry feito manualmente em `ensureToken`).

**F1-B2-03** critico, esforço M, [DISSERTACAO]: fallback devolve lista vazia, o ciclo termina como "sucesso" com `processados=0 erros=0` (`ApacCollectorServiceImpl.java:63,115`; `SensorCollectionTask.java:62-64` incrementa `outcome="success"` e os contadores com 0). Falha total da fonte é indistinguível de "nenhuma estação nova". Retry configurado é código morto.

### 2.4 Idempotência

- `V1__baseline_schema.sql:44-45`: `sensor_data` tem `PRIMARY KEY (id, timestamp)` e `UNIQUE (sensor_id, timestamp)`; hypertable por `timestamp` (`:47`).
- Aplicação: leitura-antes-de-escrever (`SensorServiceImpl.java:150`, `findBySensorIdAndTimestamp`) e captura de `DataIntegrityViolationException` (`:216-221`). Não é upsert; é check-then-insert protegido por constraint. Correto no resultado, caro em consultas (1 SELECT por leitura por ciclo).
- `ApacCollectorServiceImpl.java:65-78` reduz cada ciclo à última leitura por estação (a fonte devolve ~8.700 itens por fetch, comentário `:65-69`).
- `weather_data` e `flood_predictions` (`V1:49-66`, `:78-97`) não têm constraint de unicidade além de `(id, timestamp)`: reexecução gera duplicatas.
- Captura de `DataIntegrityViolationException` dentro de método `@Transactional` (`SensorServiceImpl.java:33,216-221`): a exceção passa pelo proxy do `SimpleJpaRepository`, que marca a transação como rollback-only (inferência); o commit pode lançar `UnexpectedRollbackException`, engolida pelo `catch` externo (`:52-54`).

**F1-B2-04** importante, esforço M: idempotência por SELECT + captura de violação dentro de transação; N SELECTs por ciclo. Alternativa: `INSERT ... ON CONFLICT (sensor_id, timestamp) DO NOTHING` (query nativa; sem mudança de schema).

### 2.5 `taskExecutor`, perda por rejeição e contagem enganosa

`processSensorMessage` é `@Async`: o coletor só submete e incrementa `processed++` (`ApacCollectorServiceImpl.java:107-108`; `AnaCollectorServiceImpl.java:79-80`). Falhas de persistência acontecem depois, na thread `Ingest-*`, e são engolidas (`SensorServiceImpl.java:52-54`: `catch (Exception)` apenas loga). Logo `mapi_collector_records_processed_total` conta submissões, não gravações, e `records_errors_total` não reflete falhas de banco. Se um ciclo submeter mais de ~110 estações (Meteo com `meteorologia-rmr-only: false` coleta todas as estações do endpoint, `application.yml`) enquanto os workers estão lentos (cada um faz consulta de maré, item 3), as submissões excedentes são rejeitadas com `TaskRejectedException`, contadas como `errors++` no `catch` do coletor e **perdidas** (a próxima rodada só traz a leitura mais recente).

**F1-B2-05** importante, esforço M, [DISSERTACAO]: contador de "processados" mede submissão ao pool, não gravação; fila limitada (110) com rejeição abrupta; falha de persistência é só log.

### 2.6 Cenário: APAC fora do ar por 6 horas (passo a passo)

Premissas: servidor APAC recusa conexão (falha rápida). Cemaden a cada 3 min e Meteo a cada 5 min, ambos usando a mesma instância `apacScrape`.

1. Primeiro ciclo com falha: `fetchRawStations` lança `ResourceAccessException`; o CB registra a falha; o fallback loga `WARN "Falha ao coletar dados da APAC ..."` e devolve `List.of()` (`ApacScrapeClientImpl.java:69-72`). O `@Retry` não repete (item 2.3). Coletor retorna `(0,0)`; `SensorCollectionTask` loga `INFO "Coleta [APAC/Cemaden] concluída: processados=0 erros=0"` e registra `outcome=success`; `mapi.collector.failures` **não** incrementa (`SensorCollectionTask.java:60-70`).
2. Após 5 chamadas falhas (janela mínima = 5; em ~9 min entre Cemaden e Meteo) a taxa é 100% > 50% e o breaker abre; chamadas seguintes falham rápido com `CallNotPermittedException`, também tratada pelo fallback (mesmo log WARN).
3. O breaker fica aberto 30 s, vai a half-open, permite 3 chamadas, falha e reabre. Como a cadência (3 e 5 min) é maior que 30 s, na prática cada ciclo faz uma tentativa real ou rejeição imediata; o breaker oscila e o log WARN se repete a cada ciclo (~120 linhas Cemaden + ~72 Meteo em 6 h, cálculo 360/3 e 360/5).
4. Dado: nada é gravado em `sensor_data` para APAC por 6 h. O ANA continua (outra instância de breaker). Open-Meteo continua.
5. Quando a APAC volta: a próxima rodada pega apenas a **última leitura por estação** (`ApacCollectorServiceImpl.java:65-78`). Mesmo que a fonte guarde histórico rolante (comentário: ~150 leituras por estação), as leituras intermediárias das 6 h não são recuperadas. Não existe job de backfill automático; o caminho de reparo é manual via endpoints administrativos de histórico (fora do escopo desta leitura).
6. Alerta: nenhum. `prometheus.yml` não tem `rule_files`/alertmanager; o painel `04-coletores-resiliencia.json` mostra `resilience4j_circuitbreaker_state` apenas para olhar manualmente.
7. Efeito na IA/dataset: o dataset exportado (item 6) trata sensor ausente como precipitação 0 (`fillMissingValues`, `DataExportServiceImpl.java:211`), então as 6 h entram como "sem chuva" e não como "sem dado". [DISSERTACAO].
8. Cenário pior (host trava a conexão em vez de recusar): sem timeout (F1-B2-01) a thread do agendador fica presa e **todos** os jobs param até a conexão cair.

**F1-B2-06** critico, esforço M-G, [DISSERTACAO]: sem backfill automático e sem sinalização de lacuna; 6 h de APAC viram zeros no dataset. Mínimo: coletor retornar erro quando a fonte falha (não `List.of()`), métrica `failures`, regra de alerta, e registro de janela sem dado.

**F1-B2-07** critico, esforço P, [DISSERTACAO]: timestamp inválido ou ausente vira `LocalDateTime.now()` (`SensorServiceImpl.java:246-247,261`) e é gravado como se fosse a hora da medição; o teste `SensorServiceTest.java:65` (`shouldHandleInvalidTimestamp`) consagra esse comportamento. `APAC` sem hora vira `"N/A"` (`ApacCollectorServiceImpl.java:155,211`) e cai nesse fallback. Também `LocalDateTime` sem fuso (ADR 0001): marcar para a Fase 2, mas o descarte (rejeitar leitura sem timestamp) é decisão independente.

---

## 3. Transações

`@Transactional` encontrados (grep):

| Local | Escopo | Observação |
|---|---|---|
| `SensorServiceImpl.java:33` `processSensorMessage` | 1 payload, `@Async`, 1 transação por payload | inclui consulta de maré e `findAll` de pontos |
| `DataExportServiceImpl.java:23` (nível de classe, `readOnly=true`) | cada chamada pública, inclusive `parallelStream` por ponto | conexão retida durante chamadas externas (maré) |
| `TideServiceImpl.java:30,102` (`@Transactional`), `:57,67,90` (`readOnly`) | `getTideHeightAt` abre transação de escrita só para ler | invocado por leitura de sensor e por hora do export |
| `MapiServiceImpl.java:182,293,305,630` | `createFloodPoint`, `getAllFloodPoints`, `getFloodPointBySlug` | `getFloodPointBySlug` (`:305`) chama a IA (HTTP) e grava predição dentro da transação |
| `FloodEventServiceImpl.java:31,50` | criação/ingestão de eventos | |
| `RefreshTokenServiceImpl.java:32,58,64` | pequenas | |
| `HistoricalDataServiceImpl.java:98,340,356` | fora do escopo | |

Riscos de transação longa:
- `MapiServiceImpl.java:305-312` (`getFloodPointBySlug`, `@Transactional`) executa `getPreciseData` → HTTP à IA sem timeout (item 8) com a conexão do HikariCP presa.
- `DataExportServiceImpl.java:23`: export de 30 dias a `days=0` mantém a transação durante todo o cálculo; se a maré faltar localmente, cada hora chama a TabuaMare (limitada a 15/min) ainda dentro da transação.
- Job de ingestão: a granularidade é boa (1 transação por leitura, não por ciclo), conforme a skill. O risco é o custo dentro dela (F1-B2-09).
- `getAllFloodPoints` (`:293-302`) escreve (`syncSensors` altera `FloodPoint`) em uma operação de leitura GET.

**F1-B2-08** importante, esforço M: transação aberta durante chamadas HTTP externas (`MapiServiceImpl.java:305`, `DataExportServiceImpl.java:23`, `TideServiceImpl.java:102`). Mover HTTP para fora da transação; `readOnly=true` em `getTideHeightAt`.

---

## 4. Pools

Valores efetivos (nenhuma propriedade de pool no `application.yml`; `grep hikari|pool` em `src/main/resources` e `src/test/resources` sem resultado):

| Pool | Valor | Fonte |
|---|---|---|
| HikariCP `maximumPoolSize` | 10 (padrão do Hikari; o `stress-test.js` também assume "10 conexões por padrão") | não configurado |
| Tomcat `max-threads` | 200 (padrão Spring Boot; não visto no código, inferência) | não configurado |
| `taskExecutor` | core 5, max 10, fila 100 | `ApplicationConfig.java:70-72` |
| Agendador | 1 thread (inferência) | não configurado |

Comparação com a carga: o `taskExecutor` pode ter até 10 threads, cada uma segurando uma conexão por toda a transação de `processSensorMessage`, e o Hikari tem 10 conexões. Ou seja, o pool de ingestão sozinho pode esgotar o banco e deixar o tráfego HTTP (200 threads Tomcat) na fila de conexões. Somam-se os jobs que também usam o banco (agendador, exports com `parallelStream` no `ForkJoinPool.commonPool`, `DataExportServiceImpl.java:37,46`, cada ponto numa transação própria).

**F1-B2-09** importante, esforço P-M: pool Hikari e `taskExecutor` sem dimensionamento deliberado; `executor.max (10) = hikari.max (10)`. Definir `spring.datasource.hikari.maximum-pool-size` e `connection-timeout`, limitar o executor abaixo do Hikari, `CallerRunsPolicy` ou fila persistente.

Custo por leitura (dentro da thread `Ingest-*`): `TideServiceImpl.java:109` `findAllByYear(year)` carrega todas as `TideTable` do ano; `getTideHeightAt` navega `getGeoLocations()`, `getMonths()`, `getDays()`, `getHours()` (`OneToMany` em `TideTable.java:37,41`, `MonthData.java:30`, `DayData.java:30`, carregamento lazy dentro da transação) para escolher um porto e um dia. Isso se repete a cada leitura com coordenada, sem cache (o `@Cacheable` está em `getCurrentTideHeight`, não em `getTideHeightAt`).

**F1-B2-10** importante, esforço M: `getTideHeightAt` por leitura carrega o grafo inteiro das tábuas do ano (N+1 de coleções lazy). Cachear o porto mais próximo por estação e a tábua do dia.

### Dashboards e k6 (somente leitura)

`observability/grafana/dashboards/` (5 JSON), métricas usadas nas expressões:
- `01-teoria-das-filas` (7 painéis): `hikaricp_connections_{acquire_seconds_count,acquire_seconds_sum,usage_seconds_count,usage_seconds_sum,active,max,pending}`, `http_server_requests_seconds_{bucket,count,sum}`, `tomcat_threads_busy_threads`, `tomcat_threads_config_max_threads`.
- `02-http-overview` (4): `http_server_requests_seconds_{count,sum}`.
- `03-jvm-recursos` (6): `hikaricp_connections_{active,idle,max}`, `jvm_gc_pause_seconds_sum`, `jvm_memory_{used,max}_bytes`, `jvm_threads_{live,peak}_threads`, `process_cpu_usage`, `process_uptime_seconds`.
- `04-coletores-resiliencia` (6): `mapi_collector_{failures_total,records_errors_total,records_processed_total,run_seconds_bucket,run_seconds_count}`, `resilience4j_circuitbreaker_{state,calls_seconds_count}`.
- `05-k6-load-test` (7): `k6_{checks_rate,http_req_duration_avg,http_req_duration_p95,http_req_failed_rate,http_reqs_total,iterations_total,vus}`.

Lacunas de métrica: nenhum painel de `executor_*` (`executor_queued_tasks`, `executor_active_threads`, `executor_pool_size_threads`) apesar de o `stress-test.js` (comentário no cabeçalho) dizer que o `taskExecutor` "alimenta o dashboard 01"; nenhuma métrica da IA (latência/erro de `/v1/predict/flood`); nenhum contador de tarefas rejeitadas; nenhuma métrica de lacuna de dados por fonte (idade da última leitura por sensor).

`loadtest/`: `stress-test.js` (rampa até 70 VUs, cenário paralelo `heavy_export` de 2 VUs a `days=30`, cenário separado para `POST /api/flood-predictions`) e `spike-demo.js` (picos de até 220 VUs, serve a demonstração). Nenhum dos dois exerce o caminho de coleta nem o export completo (`days=0`).

**F1-B2-11** desejavel, esforço P: acrescentar `executor_*` e métricas da IA e de idade-do-dado aos dashboards. Dissertação: não, mas a idade-do-dado alimenta a decisão F1-B2-06.

---

## 5. Segurança

| Item | Achado | Classe | Aceitável só local? |
|---|---|---|---|
| JWT: segredo | `jwt.secret: ${JWT_SECRET}` sem default (`application.yml:37`): aplicação falha ao subir sem a variável | ok | sim e em produção |
| JWT: segredo no compose | `docker-compose.yml:50`: default público `72de30...e5eb` (64 hex = 64 bytes UTF-8, `Keys.hmacShaKeyFor` escolhe HS512 por tamanho, inferência) | importante | só local; qualquer um que leia o repositório forja token se o deploy esquecer `JWT_SECRET` |
| JWT: expiração | access 1 h (`JwtService.java:23`); refresh 30 dias opaco no banco (`application.yml:38`, `AuthController.java:96`); refresh não é rotacionado (`AuthenticationServiceImpl.refreshToken` devolve o mesmo) | desejavel | sim |
| JWT: claims | só `sub`, `iat`, `exp`; sem `iss`/`aud`; papel vem do banco a cada requisição (`JwtAuthenticationFilter.java` carrega `UserDetails`) | desejavel | sim |
| JWT: erros | `catch (Exception)` silencioso no filtro | desejavel | sim |
| CORS | origens `http://localhost:3000,http://127.0.0.1:3000` (`application.yml:98`), `allowCredentials(true)`, métodos GET/POST/PUT/DELETE/OPTIONS/PATCH, cabeçalhos explícitos (`SecurityConfig.java:62-78`) | ok | sim |
| CSRF | desabilitado (`SecurityConfig.java:35`) com autenticação por cookie `accessToken` `SameSite=Lax` (`AuthController.java:84-100`) | desejavel | sim; Lax mitiga POST cross-site |
| Rate limit | só `POST /api/auth/login` e `/register`, 5 tentativas/min por IP+rota, em memória (`LoginRateLimitingFilter.java:36-38`). Chave usa `X-Forwarded-For` do cliente sem validar proxy confiável (`:90-93`), logo é contornável com cabeçalho forjado. Nenhum limite nos demais endpoints (export, IA, `environmental-data`) | importante | só local |
| Registro aberto | `/api/auth/register` é `permitAll` (`SecurityConfig.java:37`) e cria `USER` (`AuthenticationServiceImpl.java`); `USER` acessa `/api/export/**` (dataset completo) e `POST /api/pontos`, `/api/flood-predictions` | importante | só local |
| Credencial do banco | `mapi123` no compose (`:8,30,44`) e default em `application.yml` (`POSTGRES_PASSWORD:mapi123`); porta `5433:5432` publicada em todas as interfaces (`docker-compose.yml:9-10`) | importante | só local, mas a publicação em 0.0.0.0 expõe o banco à rede local |
| mapi-ai | porta `8000:8000` publicada em todas as interfaces (`docker-compose.yml:32-33`), sem autenticação | importante | só local |
| Grafana | `admin` / `mapi123` por padrão (`docker-compose.yml:121`; README `:153`), ligado a `127.0.0.1:3001` | desejavel | sim |
| Prometheus | sem autenticação, `--web.enable-remote-write-receiver` (`docker-compose.yml:96`), ligado a `127.0.0.1:9090` | desejavel | sim |
| Actuator | exposição: `health,info,prometheus,metrics` (`application.yml:182`); porta própria `9404`, não publicada no host; `/actuator/health/**`, `/info`, `/prometheus` são `permitAll`; `/actuator/**` restante exige ADMIN (`SecurityConfig.java:48-49`); `health` com `show-details: when-authorized` | desejavel | sim; se a 9404 for publicada, `/prometheus` vaza métricas sem autenticação |
| Swagger | `/swagger-ui/**` e `/v3/api-docs/**` públicos (`SecurityConfig.java:38`) | desejavel | sim |
| Endpoints públicos de leitura | `GET /api/sensors/**` e `/api/weather/**` sem autenticação (`SecurityConfig.java:39`), incluindo `/api/sensors/{id}/history` paginado | desejavel | decisão de produto |
| Segredos no README | apenas `admin/mapi123` do Grafana, rotulado "uso local" (`README.md:153`); nenhum `.env` versionado (`git ls-files` sem `.env`) | ok | |
| Cookie `Secure` | default `true` no `application.yml:92`, desligado no compose | ok | |
| ADMIN bootstrap | só cria se variáveis definidas (`AdminBootstrapSeeder.java:41-61`) | ok | |

**F1-B2-12** importante, esforço P: segredo JWT com default público no compose; publicação do Postgres (5433) e da IA (8000) em todas as interfaces; registro público dando acesso ao export. Aceitável só para uso local em máquina própria.
**F1-B2-13** importante, esforço P: rate limit só em login/register e contornável por `X-Forwarded-For`; sem limite no export/IA.
**F1-B2-14** desejavel, esforço P: Grafana com senha padrão, Prometheus com remote-write sem auth, Actuator `prometheus` público na porta interna; refresh sem rotação.

---

## 6. Performance (por leitura)

### 6.1 N+1 e relacionamentos
- `FloodPoint` tem 3 `@ElementCollection(fetch = EAGER)` (`FloodPoint.java:36,42,48`). `floodPointRepository.findAll()` (chamado por requisição em `MapiServiceImpl.java:81` e `:296`, no job horário `DataCollectionTask.java:24`, no export `DataExportServiceImpl.java:34,44,290`) gera 1 + 3N consultas (N = nº de pontos). Contraria agents.md §2.2 (a regra fala de EAGER em séries temporais; `FloodPoint` não é série, mas o padrão é o mesmo). Em `getFloodPointsCache` a lista cacheada contém entidades mutáveis (modificadas em `SensorServiceImpl.java:113-127`).
- `MapiServiceImpl.getEnvironmentalData` (`:60-100`) executa `findAll()` e `exportUnifiedDataWithAccumulated(slug, 1)` por requisição; este último carrega sensores em raio, clima, eventos e calcula maré hora a hora (24+ chamadas a `getTideHeightAt`, cada uma com o grafo de tábuas, item 2/F1-B2-10), tudo para servir `GET /api/environmental-data`, que o `stress-test.js` martela.
- `SensorServiceImpl.java:101-129`: por leitura, percorre todos os pontos em memória (Haversine) e faz `floodPointRepository.save` em cada vínculo novo.
- `MonthData`/`DayData`/`HourData`: `@ManyToOne` sem fetch declarado = EAGER por padrão do JPA (`MonthData.java:19`, `DayData.java:19`, `HourData.java:18`, `GeoLocation.java:18`, `RefreshToken.java:28`); `FloodEvent` é LAZY (`FloodEvent.java:19`).

**F1-B2-15** importante, esforço M: `GET /api/pontos/{slug}` e `GET /api/environmental-data` fazem trabalho de export (24 h de série + maré + IA) por requisição.

### 6.2 Séries temporais sem paginação
- Paginados: `GET /api/sensors/{id}/history` (`@PageableDefault(size = 50)`, `SensorController.java:37-43`) e `/inventory`. Sem limite máximo de `size` configurado (`spring.data.web.pageable.max-page-size` ausente, padrão 2.000).
- Sem paginação: `GET /api/export/ia-dataset/{slug}` (`DataExportController.java:40-45`, lista completa), `/ia-dataset/{slug}/csv`, `/ia-dataset/all/csv` (`days=0` = desde `2021-01-01`, `DataExportServiceImpl.java:58`), `/ia-dataset/stream` (cada ponto inteiro em memória antes de emitir), `GET /api/sensors/ids` (`SELECT DISTINCT sensor_id` sobre a hypertable inteira, `SensorDataRepository` `findDistinctSensorIds`), `SensorService.getSensorHistoryByCode` (`findByCodeOrderByTimestampDesc`, sem limite; não exposto no controller hoje).
- `days` não é validado (`@Min/@Max` ausentes, valores negativos ou muito grandes aceitos; negativo vira `days > 0` falso → desde 2021).

### 6.3 Custo provável do `/api/export/ia-dataset/{slug}/csv`
Para `days` dias: `H = 24·days + 1` linhas (30 dias = 721 linhas; `days=0` = ~5,8 anos, cerca de 50.000 horas por ponto, cálculo 2021-01-01 a 2026-10-10 ≈ 2.109 dias · 24).
1. Query espacial JPQL `findSensorsByRadius` (`SensorDataRepository`): `acos(cos...)` calculado por linha; só o filtro `timestamp BETWEEN` usa índice; o raio não tem índice espacial. A expressão `acos(...)` pode exceder 1 por arredondamento e causar erro de domínio no PostgreSQL (inferência: `acos` fora de [-1,1] lança erro); `GeoUtils` (atan2) não tem esse problema, mas a query sim.
2. `findByLatitudeAndLongitudeAndTimestampBetween` compara `double` por igualdade (`DataExportServiceImpl.java:75`): frágil se a coordenada do ponto mudar de precisão.
3. Timeline `TreeMap` com H entradas; `builder.build()` chamado dentro do laço de leituras (`:97,105`) = alocação O(leituras).
4. Maré por hora: H chamadas `tideService.getTideHeightAt` (`:145`), cada uma com `findAllByYear` + travessia lazy (F1-B2-10); se a tábua local não cobre, chamada HTTP à TabuaMare (15/min, `application.yml` `ratelimiter`) dentro da transação de classe: 721 horas sem tábua levariam ≥ 48 min só no limite de taxa (721/15).
5. Rótulo `events.stream().anyMatch` por hora: O(H·E); `isTimeWithinEvent` assume janela de 3 h quando `endTime` é nulo (`:247`), um parâmetro de rotulagem sem registro de versão. [DISSERTACAO]
6. `generateCsv` (`:254-280`) monta tudo em `StringBuilder` com `String.format` por linha e devolve `ResponseEntity<String>`: memória O(H·~200 B) + cópia em String; para `all/csv` com `days=0` e N pontos paralelizados (`parallelStream`, `:46`) não há limite superior de memória.
7. Valores `null` saem literalmente como `null` no CSV (`tideHeight`, `severity`) — contrato frágil para o pipeline Python. [DISSERTACAO]

**F1-B2-16** importante, esforço M: export sem limite de `days`, em memória, maré hora a hora dentro de transação; risco de `OutOfMemory`/esgotamento do Hikari em `all/csv?days=0`.
**F1-B2-17** desejavel, esforço M: query de raio em JPQL sem índice espacial nem clamp do `acos`; pré-filtrar por bounding box (`latitude BETWEEN`) ou usar CTE.

### 6.4 Qualidade do dado exportado (impacto direto na dissertação)
- `fillMissingValues` (`DataExportServiceImpl.java:199-232`) preenche sem sinalizar: precipitação do sensor ausente = 0,0; temperatura = 25,0 °C inicial; pressão = 1013,0 hPa; umidade do solo = 30,0 inicial; nível de rio = último valor (forward-fill) ou 0,0; código do tempo = 0. O CSV não traz colunas de "imputado" nem de "tem leitura". O modelo aprende sobre valores fabricados e a avaliação não consegue separar dado real de preenchido.
- Agregação regional: máximo de `accumulatedPrecipitation` das leituras da hora (`:95-100`). Esse campo vem acumulado da fonte (janela da APAC), não incremento horário; o máximo regional de acumulados não é chuva por hora. Sem documentar a janela do acumulado, a feature `sensorPrecipitation` tem semântica ambígua. [DISSERTACAO]
- Acumulados `accumulated3h..48h` (`:188-197`) somam apenas `weatherPrecipitation` (Open-Meteo), não os sensores; a IA recebe em tempo real os mesmos acumulados via `MapiServiceImpl` (`runPredictionAndAudit`, `acc3h..acc24h`), então treino e inferência concordam, mas o nome da coluna sugere sensor.
- Tempo: `LocalDateTime.now()` na borda do intervalo (`:57`) e timestamps sem fuso (ADR 0001, já decidido).
- Nenhum cabeçalho de versão (commit, data de geração, parâmetros `days`) no CSV/JSON.

**F1-B2-18** critico, esforço M, [DISSERTACAO]: imputação silenciosa por constantes no export (`DataExportServiceImpl.java:199-232`).
**F1-B2-19** importante, esforço M, [DISSERTACAO]: semântica de `sensorPrecipitation` (máx. de acumulados) e de `accumulated*` (só Open-Meteo) não documentada; export sem manifesto de versão/parâmetros para reprodutibilidade.

---

## 7. Testes

Arquivos em `src/test` (8, incluindo `resources/application.yml`; 697 linhas no total, 7 classes com 18 métodos `@Test`/`void`):

| Arquivo | O que cobre |
|---|---|
| `MapiApplicationTests.java` (15 l.) | `contextLoads` com H2 |
| `SecurityAndValidationIntegrationTest.java` (126 l.) | 7 casos de MockMvc: admin sem token / com USER / com ADMIN, export sem token, 400 de parâmetros ausentes, 400 de campos em `POST /api/pontos`, 404 de slug |
| `SensorServiceTest.java` (83 l.) | `processSensorMessage` com repositório mockado: payload válido e timestamp inválido |
| `SensorDataFormatTest.java` (94 l.) | formatos APAC meteo, ANA tele, APAC pluvio |
| `ApacParsingTest.java` (72 l.) | parsing do HTML da APAC |
| `MapiServiceTest.java` (130 l.) | sensor próximo vs. clima, não mapear sensor distante |
| `HistoricalDataServiceTest.java` (89 l.) | mapeamento de estação ao ponto por proximidade |
| `CivilDefenseParserTest.java` (59 l.) | parser do CSV da Defesa Civil |
| `resources/application.yml` | H2, `ddl-auto: create-drop`, Flyway desligado, ANA/APAC desligadas |

Pontos relevantes: os testes rodam em H2 sem Flyway (`src/test/resources/application.yml`), logo nenhuma migração, hypertable, constraint `UNIQUE (sensor_id, timestamp)`, `DISTINCT ON` ou consulta nativa é exercitada (ADR 0004 prevê Testcontainers). `pom.xml` não tem `archunit`, `testcontainers` nem `jacoco`. Chaves JWT de teste fixas em `src/test/resources/application.yml`.

Caminhos críticos **sem teste**:
1. `SensorCollectionTask.runCycle` e métricas (sucesso/falha, `processed`).
2. `ApacCollectorServiceImpl.collect` (redução à última leitura, filtro RMR, ramo fluviométrico) e `AnaCollectorServiceImpl.collectAll`; só o parsing de formatos tem teste.
3. Resilience4j: retry/CB/fallback de APAC/ANA/Open-Meteo/TabuaMare (inclusive a ineficácia do retry, F1-B2-03).
4. Idempotência do banco: duplicata, `DataIntegrityViolationException`, rollback-only (F1-B2-04).
5. `FloodPredictionServiceImpl` (sucesso, timeout, 5xx, resposta vazia) e `runPredictionAndAudit` (o que é gravado em `flood_predictions`).
6. `DataExportServiceImpl` (`fillMissingValues`, rotulagem, acumulados, CSV): zero teste do artefato que vira treino do modelo.
7. `TideServiceImpl.getTideHeightAt` e interpolação.
8. `LoginRateLimitingFilter`, refresh de token, logout, expiração de JWT, CORS.
9. Fuso/horário em qualquer parser (ADR 0001) — nenhum teste verifica UTC.
10. `GlobalExceptionHandler` além de 400/404.

**F1-B2-20** importante, esforço G, [DISSERTACAO]: nenhum teste cobre o export do dataset nem a gravação em `flood_predictions`; testes de coleta e idempotência ausentes; suíte inteira em H2.
**F1-B2-21** importante, esforço M: `shouldHandleInvalidTimestamp` fixa como correto o fallback para "agora" (F1-B2-07).

---

## 8. Contrato de degradação da IA

- Chamada: `FloodPredictionServiceImpl.java:26-52`, `RestClient` com `baseUrl` = `AI_API_URL` (padrão `http://localhost:8000`, `application.yml`; compose define `http://mapi-ai:8000`), `POST /v1/predict/flood`, corpo `FloodPredictionRequestDTO`, resposta `AiPredictionDTO` (`flood_probability`, `risk_level`, `estimated_time_to_event`, `message`, `:65-77`).
- Quem chama: **não** é o job de coleta. Dois caminhos síncronos disparados por requisição HTTP: `POST /api/flood-predictions` (`MapiController.java:44-49` → `MapiServiceImpl.createFloodPrediction`, `:105`) e `GET /api/pontos/{slug}` (`MapiController.java:73` → `getFloodPointBySlug` `:305` → `convertToResponseDTO(..., true)` → `getPreciseData` `:396` → `runPredictionAndAudit` `:111-115`). `SensorCollectionTask` e `DataCollectionTask` não chamam a IA. A frase de agents.md §4.1 ("ao processar um dado de tempo real ... envie imediatamente") não é cumprida.
- Timeout: nenhum (F1-B2-01). Retry/CB: nenhum para a IA (só `openMeteo`, `tabuaMare`, `anaData`, `apacScrape` em `application.yml`). Cada GET de ponto bloqueia a thread HTTP e uma conexão Hikari (transação aberta) enquanto a IA não responde.
- Fallback: em exceção ou corpo nulo devolve `riskLevel="UNKNOWN"`, `floodProbability=0.0`, `message` com a causa (`:38-39,48-50,54-60`). `estimatedTimeToEvent` nulo. O `risk_level` devolvido pela IA é repassado como `String` sem validar contra `LOW|MEDIUM|HIGH|UNKNOWN` (`:44`): um valor inesperado (ou nulo) vira "SUCCESS".
- Persistido em `flood_predictions` (`MapiServiceImpl.java:153-173`, tabela `V1__baseline_schema.sql:78-97`): `timestamp` (`LocalDateTime.now()` na criação do request, `:147` e `:155`), `station_id` (sensor mais próximo ou `"VIRTUAL_STATION"`), latitude, longitude, chuva atual, acumulados 3/6/12/24 h, maré, nível de rio, `flood_probability` (**0.0 quando a IA falha**, `:166`), `risk_level` (`"UNKNOWN"` na falha), `status` (`"SUCCESS"` se `risk_level != "UNKNOWN"`, senão `"FAILED"`, `:168`), `message`. O `PENDING` prometido em agents.md não existe em nenhum lugar do código (`grep PENDING` vazio fora de comentários de documentação).
- Não persistido: versão do modelo (a resposta da IA não traz `model_version` no DTO), versão/hash do contrato, `nearbySensors` enviados (o payload completo vai à IA mas só os campos escalares ficam na tabela), latência, tentativa, `estimated_time_to_event`.
- Reprocessamento: **não existe**. Nenhum job relê linhas `FAILED`/`UNKNOWN`, nenhuma fila, nenhum campo de tentativas. Uma predição que falha produz uma linha `FAILED` com probabilidade 0,0 que nunca é revisitada.
- Efeito no cliente: `GET /api/pontos/{slug}` continua devolvendo o ponto com `floodPrediction` UNKNOWN (não falha); `POST /api/flood-predictions` pode devolver `201` com corpo `null` se `getPrediction` lançar exceção fora do `try` interno (`runPredictionAndAudit` retorna `prediction` nulo no `catch` externo `:176-178`).
- Escrita em GET: cada `GET /api/pontos/{slug}` insere uma linha em `flood_predictions` (poluição de auditoria, inflaciona a tabela, não-idempotente, e mistura consultas de UI com predições "oficiais"). O `stress-test.js` mesmo isola o endpoint de predição por causa de instabilidade da IA (comentário nas linhas iniciais).
- Estatística: linhas `FAILED` com `flood_probability = 0.0` contaminam qualquer análise posterior da tabela (média de probabilidade, calibração) se o filtro por `status` for esquecido; `NULL` seria a representação correta.

**F1-B2-24** critico, esforço M, [DISSERTACAO]: sem timeout/CB na chamada à IA, sem `PENDING`, sem reprocessamento, `0.0` em vez de `NULL` nas falhas, sem `model_version`, `risk_level` não validado. Contrato de agents.md §4.3 só cumprido pela metade (não trava a requisição, mas não deixa estado reprocessável).
**F1-B2-25** importante, esforço P, [DISSERTACAO]: `GET /api/pontos/{slug}` grava em `flood_predictions` a cada chamada (`MapiServiceImpl.java:396` + `:153-173`); a tabela mistura predições de UI e de avaliação.
**F1-B2-26** desejavel, esforço P: cache `floodPoints` retorna entidades mutáveis e é invalidado a cada 10 min por `@Scheduled` em `CacheConfig.java:23-24`, com comentário admitindo que `syncSensors` muta sem evicção; fonte de leitura obsoleta, não de corrupção.
**F1-B2-23** desejavel, esforço G: `MapiServiceImpl` (775 linhas) acumula pontos, sensores, clima, IA e auditoria; separar antes de adicionar PENDING/reprocessamento.

---

## Tabela de achados

| id | título | severidade | dissertação | arquivos | métrica |
|---|---|---|---|---|---|
| F1-B2-01 | Nenhum timeout em clientes externos (APAC, ANA, IA, Open-Meteo) | critico | s | `ApacScrapeClientImpl.java:33-40`, `AnaDataClientImpl.java:60-64`, `FloodPredictionServiceImpl.java:20-24`, `ApplicationConfig.java:58-65` | 0 timeouts configurados em 10+ clientes |
| F1-B2-02 | Agendador de 1 thread (inferência) e ANA sequencial | importante | n | `SensorCollectionTask.java:28-47`, `AnaCollectorServiceImpl.java:63-66` | 6 jobs em 1 thread |
| F1-B2-03 | Fallback devolve lista vazia: falha vira "sucesso 0/0" e Retry nunca dispara | critico | s | `ApacScrapeClientImpl.java:43-44,69-72`, `AnaDataClientImpl.java:67-68`, `SensorCollectionTask.java:60-70` | 3 tentativas configuradas, 0 efetivas |
| F1-B2-04 | Idempotência por SELECT + captura de violação dentro de transação | importante | n | `SensorServiceImpl.java:33,150,216-221`, `V1__baseline_schema.sql:45` | 1 SELECT por leitura/ciclo |
| F1-B2-05 | `processed` mede submissão, não gravação; fila de 110 rejeita e perde | importante | s | `ApplicationConfig.java:70-72`, `ApacCollectorServiceImpl.java:107-108`, `SensorServiceImpl.java:52-54` | capacidade 10+100=110 |
| F1-B2-06 | Sem backfill nem alerta após queda; 6 h de APAC viram zeros no dataset | critico | s | `ApacCollectorServiceImpl.java:65-78`, `prometheus.yml`, `DataExportServiceImpl.java:211` | 6 h = 120 ciclos Cemaden + 72 Meteo sem dado |
| F1-B2-07 | Timestamp inválido vira `now()`; teste consagra | critico | s | `SensorServiceImpl.java:246-247,261`, `SensorServiceTest.java:65` | 2 pontos de fallback |
| F1-B2-08 | Transação aberta durante HTTP externo | importante | n | `MapiServiceImpl.java:305`, `DataExportServiceImpl.java:23`, `TideServiceImpl.java:102` | 3 locais |
| F1-B2-09 | Hikari 10 = executor max 10; pools sem dimensionamento | importante | n | `ApplicationConfig.java:70-72`, `application.yml` (ausente) | 10 vs 10 conexões/threads |
| F1-B2-10 | Maré por leitura carrega grafo de tábuas do ano (N+1) | importante | n | `TideServiceImpl.java:103-150`, `TideTable.java:37,41` | 1 `findAllByYear` por leitura/hora |
| F1-B2-11 | Dashboards sem executor, IA e idade-do-dado | desejavel | n | `observability/grafana/dashboards/*.json` | 5 dashboards, 0 métricas de executor |
| F1-B2-12 | Segredo JWT público no compose; Postgres 5433 e IA 8000 em 0.0.0.0; registro aberto dá acesso ao export | importante | n | `docker-compose.yml:9-10,32-33,50`, `SecurityConfig.java:37` | 3 portas/segredos expostos |
| F1-B2-13 | Rate limit só em login/register e contornável por X-Forwarded-For | importante | n | `LoginRateLimitingFilter.java:36-38,90-93` | 5 req/min em 2 rotas |
| F1-B2-14 | Grafana admin/mapi123, Prometheus sem auth, Actuator prometheus público interno, refresh sem rotação | desejavel | n | `docker-compose.yml:96,121`, `SecurityConfig.java:48`, `AuthenticationServiceImpl.java` | 4 itens |
| F1-B2-15 | GET de ponto e `environmental-data` fazem trabalho de export + IA por requisição | importante | n | `MapiServiceImpl.java:60-100,305,396` | ~25 chamadas de maré por GET |
| F1-B2-16 | Export CSV sem limite de dias, em memória, maré por hora em transação | importante | n | `DataExportController.java:48-75`, `DataExportServiceImpl.java:23,58,145,254-280` | 721 linhas (30 d) a ~50.000 h/ponto (`days=0`) |
| F1-B2-17 | Query de raio JPQL sem índice espacial nem clamp do acos | desejavel | n | `SensorDataRepository.java` (`findSensorsByRadius`) | 1 varredura por hora-janela |
| F1-B2-18 | Imputação silenciosa por constantes no export (25 °C, 1013 hPa, 30 %, 0 mm) | critico | s | `DataExportServiceImpl.java:199-232` | 6 variáveis imputadas, 0 flags |
| F1-B2-19 | Semântica de `sensorPrecipitation`/`accumulated*` não documentada; export sem manifesto de versão | importante | s | `DataExportServiceImpl.java:95-100,188-197,254-280` | 0 metadados no CSV |
| F1-B2-20 | Sem teste do export, da gravação de predição, da coleta e da idempotência; suíte em H2 | importante | s | `src/test/**`, `src/test/resources/application.yml` | 7 classes, 18 testes, 0 sobre coleta/export/IA |
| F1-B2-21 | Teste fixa o fallback de timestamp para "agora" como correto | importante | s | `SensorServiceTest.java:65` | 1 teste |
| F1-B2-22 | ArchUnit ausente; 4 violações pré-existentes medidas | desejavel | n | `AuthController.java:9,76`, `FloodEventDTO`, `ScraperEventDTO`, `SensorService.java:19` | 4 violações (1+2+1) |
| F1-B2-23 | `MapiServiceImpl` com 775 linhas mistura 5 responsabilidades | desejavel | n | `service/impl/MapiServiceImpl.java` | 775 linhas |
| F1-B2-24 | IA sem timeout/CB, sem PENDING nem reprocessamento, 0.0 em falha, sem model_version, risco não validado | critico | s | `FloodPredictionServiceImpl.java:26-60`, `MapiServiceImpl.java:125-178`, `FloodPrediction.java:56` | 0 mecanismos de reprocesso |
| F1-B2-25 | GET /api/pontos/{slug} grava em `flood_predictions` | importante | s | `MapiServiceImpl.java:305,396,153-173` | 1 INSERT por GET |
| F1-B2-26 | Cache `floodPoints` com entidades mutáveis e evicção a cada 10 min | desejavel | n | `CacheConfig.java:23-24`, `SensorServiceImpl.java:375-379` | TTL efetivo 600 s |
