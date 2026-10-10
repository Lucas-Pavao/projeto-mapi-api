---
name: mapi-clean-arch-java
description: Convenções de arquitetura do backend MAPI (Java 21 / Spring Boot 3.4) derivadas do agents.md e do GEMINI.md do projeto-mapi-api. Use sempre que for criar, refatorar ou revisar código Java no projeto-mapi-api — controllers, services, DTOs, entidades JPA, repositórios, jobs @Scheduled (SensorCollectionTask), clientes das agências (APAC, ANA, Cemaden, TabuaMare, Open-Meteo, CKAN) ou testes de arquitetura com ArchUnit — mesmo que o pedido não fale em "arquitetura".
---

# MAPI — Clean Architecture no backend Java

Fonte de verdade: `agents.md` e `GEMINI.md` na raiz do projeto-mapi-api. Esta skill
resume essas regras e aponta onde o código real diverge delas. Se esta skill e o
`GEMINI.md` discordarem, **não escolha sozinho**: registre a divergência como ADR em
`docs/adr/` e pergunte ao Lucas.

## 1. Camadas que existem hoje (adaptação em camadas, não ports-and-adapters puro)

```
com.projeto.mapi/
├── config/      beans, SensorCollectionTask (@Scheduled), segurança, cron, AppProperties
├── controller/  adaptador de entrada REST; @Operation/@ApiResponse obrigatórios
├── dto/         Request/Response como Java record + jakarta.validation
├── exception/   GlobalExceptionHandler + ErrorMessage
├── mapper/      conversão DTO↔entidade
├── model/       entidades JPA (hypertables com chave composta id + timestamp)
├── repository/  Spring Data; queries nativas TimescaleDB (time_bucket)
├── security/    filtros JWT
├── service/     interface + impl/ por domínio (auth, export, flood, geocoding, sensor, tide, weather)
└── util/        Haversine e utilitários globais
```

Regra de dependência que vale **hoje**: `controller → service (interface) → repository/clients`.
Controllers não acessam repositório nem entidade JPA. Entidade nunca sai na resposta HTTP.

### Alvo proposto (ainda NÃO aprovado — depende de ADR)

O prompt da revisão pede "domínio sem anotação de framework". Isso conflita com o
`agents.md` atual, em que `model/` **é** a entidade JPA. Até existir ADR aprovado:
- não mova entidades nem crie pacote `domain/` por conta própria;
- código novo pode já separar modelo de domínio quando o ganho for claro e local
  (ex.: cálculo de risco puro, sem JPA), mantendo a entidade JPA em `model/`.

## 2. Divergências conhecidas entre convenção e código (inventário de 2026-10-10)

| Convenção | Código real | Tratamento |
|---|---|---|
| DTO como `record` (agents.md §5) | 25 de 36 arquivos em `dto/` não são record | não reescreva em massa; converta só o que tocar |
| MapStruct (agents.md §6) | sem dependência MapStruct; `TideMapper` manual | ADR antes de adicionar MapStruct |
| Converter para UTC-3 **antes de persistir** (agents.md §3.1) | `hibernate.jdbc.time_zone: UTC`; GEMINI.md diz armazenar em UTC | conflito aberto, ver ADR de fuso; não mude persistência |
| Schema em `TimescaleSetup.sql` (agents.md §2.4) | Flyway `db/migration/V1__baseline_schema.sql` também cria hypertables | duas fontes; mudança de schema = migração Flyway nova + atualizar o .sql, com aprovação |
| Testes de integração com H2 (GEMINI.md) | H2 não executa TimescaleDB/PostGIS | Testcontainers proposto; ADR |

## 3. Regras de implementação

- **Adaptadores de entrada**: REST em `controller/`; jobs em `config/SensorCollectionTask`.
  Um job nunca bloqueia o agendador: delega ao `taskExecutor` e envolve cada agência em
  Resilience4j (retry + circuit breaker). Falha de uma agência não derruba as outras.
- **Adaptadores de saída**: clientes HTTP das agências ficam em `service/<dominio>/...`
  com timeout explícito. Repositórios só em `repository/`.
- **Séries temporais**: toda entidade histórica tem `timestamp` na chave. Sem
  `@ManyToMany` nem `EAGER` em hypertable. Agregação temporal com query nativa.
- **Vínculo sensor↔ponto**: sempre pelo fluxo dinâmico `repairStationMappings`
  (Haversine em `util/`), nunca por ID fixo. Registre o raio usado.
- **IA**: chamada síncrona a `/v1/predict/flood`; em falha/timeout, persistir o dado e
  marcar a predição `UNKNOWN` (status `FAILED`) — nunca falhar a requisição original.
- **Enum de risco**: `LOW | MEDIUM | HIGH`, mais `UNKNOWN` como degradação.
- **Lombok** obrigatório em classes (não em records). `@Slf4j` para log.
- **Transações**: `@Transactional` no service, curto. Em job de ingestão, uma transação
  por lote, nunca uma para o ciclo inteiro.

## 4. Teste de arquitetura (ArchUnit) — a adicionar

Quando o ADR aprovar, congele a regra atual com algo como:

```java
@AnalyzeClasses(packages = "com.projeto.mapi")
class ArchitectureTest {
  @ArchTest static final ArchRule controllersNaoUsamRepositorio =
      noClasses().that().resideInAPackage("..controller..")
          .should().dependOnClassesThat().resideInAPackage("..repository..");
  @ArchTest static final ArchRule controllersNaoExpoemEntidade =
      noMethods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
          .should().haveRawReturnType(resideInAPackage("..model.."));
}
```

Rode antes de introduzir a regra e registre quantas violações já existem (medida "antes").

## 5. Prompt-gate antes de entregar

1. DTO novo é record com validação? Endpoint tem @Operation/@ApiResponse?
2. Data/hora externa tem fuso declarado e convertido conforme o ADR vigente?
3. Falha de agência tratada com exceção própria + Resilience4j?
4. Query compatível com índice da hypertable (filtro por `timestamp`)?
5. Mudança de schema? Então parou e pediu aprovação.
