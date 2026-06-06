# 🤖 Manual de Operação e Diretrizes de IA - MAPI API

Este documento funciona como um manual de restrições de engenharia, padrões de design e arquitetura para o repositório **MAPI API**. Qualquer agente de IA que gere, refatore ou analise código neste projeto deve cumprir rigorosamente as regras descritas abaixo.

---

## 🏗️ 1. Padrão Arquitetural (Clean Architecture + Spring Boot)

O projeto adota uma adaptação da Clean Architecture para isolar as regras de negócio das ferramentas de infraestrutura. A estrutura de pacotes é estrita e segmentada em camadas funcionais:

```text
com.projeto.mapi/
├── config/          # Inicialização de Beans, beans MQTT, segurança e agendamentos cron.
├── controller/      # Camada REST (Exposição de Endpoints). Deve usar anotações do Swagger.
├── dto/             # Objetos de Transferência de Dados (Imutáveis por padrão).
├── exception/       # Handlers globais (@ControllerAdvice) e exceções de negócio.
├── mapper/          # Interfaces do MapStruct para conversão de dados.
├── model/           # Entidades JPA (Mapeamento focado em tabelas temporais).
├── repository/      # Interfaces Spring Data (Consultas SQL analíticas).
├── security/        # Filtros JWT e contexto de segurança da aplicação.
└── service/         # Contratos (interfaces) e implementações (impl/).

```

### 🧠 Regra dos Agentes Especializados (Identidade de Escopo)

O desenvolvimento é orientado a componentes especializados dedicados a domínios de negócio claros. Ao criar lógicas, atribua-as à classe ou pacote que respeite esta especialização:

* 🌊 **TideExpert:** Toda e qualquer lógica analítica ou de integração com tabelas oceanográficas, marés astronômicas, previsões da Marinha ou dados do Porto do Recife.

* 📡 **IoTMaster:** Toda a lógica reativa de captura, validação, desserialização de payloads MQTT e tratamentos de barramentos telemétricos.

* 🔒 **SecurityGuard:** Filtros de requisições, criptografia, ciclo de vida de tokens JWT e verificação de claims de acesso.

* 🏗️ **ProjectArchitect:** Responsável pelo core estrutural da aplicação, padrões de design (Design Patterns), utilitários globais e validação de consistência sintática do projeto.

---

## ⏳ 2. Persistência Temporal e Otimização (PostgreSQL 16 + TimescaleDB)

O banco de dados não é um repositório relacional comum; ele utiliza as capacidades de séries temporais do **TimescaleDB** através de *Hypertables*.

### Diretrizes de Modelagem e Repositório para IA:

1. **Modelos Temporais (@Entity):** Ao criar ou estender entidades JPA em `model/` que armazenem dados históricos (como telemetria ou marés), lembre-se de que a chave primária composta ou o índice principal deve obrigatoriamente incluir a coluna de carimbo de data/hora (`timestamp` ou `data_coleta`).

2. **Evite Estruturas Pesadas de Joins:** Não gere relacionamentos `@ManyToMany` complexos com carregamento do tipo `FetchType.EAGER` em entidades de séries temporais. Isso causa degradação extrema de performance durante a ingestão em lote.

3. **Consultas Nativas (Native Queries):** Para buscar médias móveis, agrupamentos de tempo (ex: `time_bucket('1 hour', time)`) ou dados analíticos para predição, prefira escrever queries nativas usando as funções do TimescaleDB no repositório (`@Query(value = "...", nativeQuery = true)`).

4. **Alinhamento com o `TimescaleSetup.sql`:** Toda modificação estrutural que afete a criação de tabelas históricas deve ser replicada e documentada detalhadamente no arquivo script de inicialização `TimescaleSetup.sql`.

---

## 📡 3. Ingestão de Dados, Tempo Real e Fusos Horários

O sistema faz a fusão de dados em tempo real via streaming com dados históricos buscados via endpoints em batch.

### Diretrizes de Ingestão para IA:

1. **Sincronização Absoluta UTC-3:** O ecossistema MAPI é focado na Região Metropolitana do Recife, que opera no fuso horário UTC-3. APIs externas como Open-Meteo e ANA frequentemente retornam datas em formato UTC puro. O agente de IA **DEVE** garantir que qualquer parser ou serviço de ingestão converta esses carimbos de tempo explicitamente para UTC-3 antes de persistir no banco ou enviar para a inferência de IA, prevenindo desalinhamento temporal nas previsões.

2. **Resiliência no IoTMaster (MQTT):** O listener do broker MQTT atua como um barramento crítico de entrada. O código gerado não pode bloquear a thread principal de escuta. Utilize processamento assíncrono para delegar a persistência e a chamada de predição do modelo a pools de threads separados.

3. **Mapeamento Espacial (Fórmula de Haversine):** O utilitário de geoprocessamento em `util/` aplica o algoritmo de Haversine para vincular estações meteorológicas a pontos críticos por proximidade espacial real. Não crie vínculos por IDs estáticos (hardcoded); use sempre o fluxo dinâmico do `repairStationMappings`.

---

## 🔌 4. Integração de Microsserviços e Contratos de Rede (MAPI AI)

A API atua como o cliente síncrono do módulo de inteligência artificial.

### Diretrizes de Comunicação Inter-serviços para IA:

1. **Comunicação Síncrona via HTTP POST:** Ao processar um dado de tempo real, monte o payload contextual do sensor e envie imediatamente uma requisição via `RestTemplate` ou `WebClient` para o endpoint `/v1/predict/flood` do microsserviço configurado em `AI_API_URL`.

2. **Tratamento Estrito de Enums de Risco:** O microsserviço de inteligência artificial retorna um objeto contendo a probabilidade (0.0 a 1.0) e uma string que representa o nível de risco. A IA deve mapear esse retorno estritamente para o Enum interno da API (`LOW`, `MEDIUM`, `HIGH`).

3. **Padrão Circuit Breaker / Fallback:** Se a API de IA falhar ou apresentar timeout, o sistema central **NÃO PODE** travar ou falhar a requisição original de persistência. Capture a exceção na camada de serviço, salve os dados localmente no TimescaleDB e defina o status da predição atual como `PENDING` ou `UNKNOWN` para reprocessamento assíncrono posterior.

---

## 🔒 5. Contrato de DTOs e Convenções do Java 21

1. **Uso de Java Records:** Todo DTO gerado para entrada (`Request`) ou saída (`Response`) de dados nas rotas dos controladores deve ser implementado obrigatoriamente como um Java `record`. Isso garante imutabilidade por design, código mais limpo e melhor legibilidade.

2. **Validação de Payloads:** Use as anotações do pacote `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Min`, etc.) em todos os campos de DTOs de entrada para garantir a higienização dos dados na borda da aplicação.

3. **Anotações de Exposição (Swagger/OpenAPI):** Cada endpoint nos controladores deve conter tags `@Operation` e `@ApiResponse` detalhadas para que a documentação do Swagger UI permaneça legível para outros agentes ou usuários desenvolvedores.

---

## 📝 6. Checklist de Validação de Código (Prompt-Gate)

Antes de entregar qualquer código Java/Spring para este repositório, certifique-se de validar os seguintes itens:

* [ ] O código utiliza Java Records para DTOs e MapStruct para mappers?

* [ ] Qualquer data/hora vinda de fontes externas foi tratada e convertida adequadamente para o fuso UTC-3?

* [ ] Escrevi tratamento de exceções customizadas para cenários onde falhas de integração externa (ANA/APAC) possam ocorrer?

* [ ] A query SQL gerada para o banco é compatível com os índices de tempo das Hypertables do TimescaleDB?
