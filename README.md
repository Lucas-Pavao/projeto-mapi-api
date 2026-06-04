# Projeto MAPI - API Central 🌊🚀

A **API MAPI** é o núcleo do ecossistema de resiliência urbana da Região Metropolitana do Recife (RMR). Ela atua como o orquestrador central, integrando dados meteorológicos, marés astronômicas e sensores telemétricos em tempo real para alimentar modelos de Machine Learning e fornecer inteligência para a prevenção de alagamentos.

## 🛠️ Tecnologias Escolhidas

- **Linguagem:** Java 21 (LTS) com Lombok.
- **Framework:** Spring Boot 3.4.0.
- **Banco de Dados:** PostgreSQL 16 com **TimescaleDB** (Otimizado para Séries Temporais e Hypertables).
- **Mensageria:** MQTT (Paho Client) para ingestão de dados IoT (ANA, CEMADEN, APAC).
- **Segurança:** Spring Security + JWT (com suporte a Refresh Token).
- **Documentação:** SpringDoc OpenAPI 2.0 (Swagger UI).
- **Geoprocessamento:** Algoritmo de Haversine para mapeamento dinâmico de estações e Fallback Nominatim (OpenStreetMap).

## 🏗️ Arquitetura e Especialização

O desenvolvimento segue uma estratégia de agentes especializados para garantir a integridade dos diferentes domínios do sistema:

- 🌊 **TideExpert:** Especialista em marés astronômicas (TabuaMare API + Porto do Recife).
- 📡 **IoTMaster:** Gestor de ingestão telemétrica e sincronização UTC-3.
- 🔒 **SecurityGuard:** Responsável pelo ciclo de vida de autenticação e proteção de dados.
- 🏗️ **ProjectArchitect:** Mantenedor da consistência arquitetural e padrões de código.

### 🔄 Fluxo de Operação Detalhado

O sistema opera em três camadas de dados sincronizadas:
1. **Ingestão Histórica (Cold Storage):** Através de endpoints de administração, o sistema busca séries temporais de até 5 anos da ANA, APAC e Open-Meteo para "aquecer" o banco de dados.
2. **Coleta Agendada (Warm Data):** Tarefas agendadas (`@Scheduled`) coletam previsões climáticas e dados consolidados de hora em hora.
3. **Tempo Real (Streaming):** Via MQTT, o sistema recebe pulsações telemétricas de sensores ativos, garantindo que o modelo de IA tenha os dados mais recentes para inferência.

## ✨ Funcionalidades / Features

- 📡 **Ingestão Multi-fonte:** Coleta automatizada de dados da ANA, APAC, CEMADEN e Open-Meteo.
- 🔄 **Coleta em Tempo Real:** Integração via MQTT para captura de dados de sensores com baixíssima latência.
- 🧪 **Geração de Datasets (AI Ready):** Consolidação de Sensores + Clima + Maré + Ocorrências em CSVs sincronizados para treinamento de modelos de IA via `DataExportService`.
- 🗺️ **Mapeamento Dinâmico:** O `repairStationMappings` gerencia a relação entre estações pluviométricas e pontos de monitoramento por proximidade espacial (Haversine).
- ⏳ **Sincronização Histórica:** Busca profunda de séries temporais para formação de base histórica robusta.

## ⚙️ Configuração (Variáveis de Ambiente)

A API pode ser configurada através das seguintes variáveis, geralmente definidas no `docker-compose.yml`:

| Variável | Descrição | Padrão |
| :--- | :--- | :--- |
| `POSTGRES_URL` | URL de conexão (JDBC) | `jdbc:postgresql://localhost:5432/tide_db` |
| `POSTGRES_USER` | Usuário do PostgreSQL | `mapi_user` |
| `POSTGRES_PASSWORD` | Senha do PostgreSQL | `mapi123` |
| `MQTT_BROKER_URL` | Endereço do broker MQTT | `tcp://broker.hivemq.com:1883` |
| `MQTT_TOPIC` | Tópico para escuta de sensores | `projeto-mapi/sensores/#` |
| `JWT_SECRET` | Chave secreta para assinatura JWT | (Chave de 64 caracteres) |
| `AI_API_URL` | URL do módulo de IA (FastAPI) | `http://localhost:8000` |
| `NAVY_API_URL` | Base URL para dados de maré | `https://www.marinha.mil.br` |
| `WEATHER_API_URL` | API de Clima (Open-Meteo) | `https://api.open-meteo.com/v1` |

## 📂 Estrutura de Pastas Detalhada

A organização do projeto segue o padrão Clean Architecture adaptado para Spring Boot, facilitando a escalabilidade e a manutenção por agentes de IA:

```text
projeto-mapi-api/
├── .github/workflows/       # Pipelines de CI/CD (GitHub Actions)
├── src/
│   ├── main/
│   │   ├── java/com/projeto/mapi/
│   │   │   ├── config/          # Configurações de Beans, Security, MQTT e Agendamentos
│   │   │   ├── controller/      # Camada REST (Exposição de Endpoints)
│   │   │   ├── dto/             # Objetos de Transferência de Dados (Imutabilidade)
│   │   │   ├── exception/       # Handlers globais e exceções customizadas
│   │   │   ├── mapper/          # Conversores entre Entidades e DTOs (MapStruct/Manual)
│   │   │   ├── model/           # Entidades JPA (Mapeamento Hipertabelas Timescale)
│   │   │   ├── repository/      # Interfaces de persistência (Spring Data)
│   │   │   ├── security/        # Filtros JWT e lógica de autenticação
│   │   │   ├── service/         # Interfaces de regras de negócio
│   │   │   │   └── impl/        # Implementações específicas (ANA, APAC, Clima)
│   │   │   └── util/            # Utilitários (Geoprocessamento, MQTT, Datas)
│   │   └── resources/
│   │       ├── application.yml  # Configurações de Perfil (Dev, Prod)
│   │       ├── static/          # Arquivos estáticos (se necessário)
│   │       └── templates/       # Templates de e-mail ou relatórios
│   └── test/                    # Suite de testes unitários e integração
├── TimescaleSetup.sql           # Script crítico de inicialização de Hipertabelas
├── Dockerfile                   # Build multi-stage otimizado para Java 21
├── docker-compose.yml           # Orquestração da Stack (API + DB + AI)
├── agents.md                    # Manual de operação para Agentes de IA
└── GEMINI.md                    # Dicionário de convenções e regras de ouro
```

## 🐳 Infraestrutura e Docker: Pontos Críticos

O ecossistema MAPI depende de uma orquestração precisa via Docker para garantir que os volumes e a rede estejam sincronizados.

### 1. Build Multi-Stage (Dockerfile)
Nosso `Dockerfile` utiliza build em dois estágios para garantir segurança e performance:
- **Build Stage:** Compila o código usando Maven 3.9 e Java 21, baixando dependências em cache.
- **Runtime Stage:** Utiliza uma imagem JRE leve (`eclipse-temurin:21-jre-jammy`), reduzindo a superfície de ataque e o tamanho da imagem final.

### 2. Persistência e TimescaleDB
O banco de dados PostgreSQL não é apenas um repositório, mas um motor de séries temporais.
- **Volume `postgres_data`:** Garante que os dados coletados (ANA/APAC) não sejam perdidos ao reiniciar o container.
- **Initialization Script:** O arquivo `TimescaleSetup.sql` é montado em `/docker-entrypoint-initdb.d/`. **Importante:** Se você já subiu o banco uma vez, o script não rodará novamente. Para resetar, use `docker volume rm`.

### 3. Comunicação Interna (Networking)
Os containers comunicam-se via nomes de serviço:
- A API se conecta ao banco em `jdbc:postgresql://postgres:5432/tide_db`.
- A API se comunica com a IA via `http://mapi-ai:8000`.
- **Dica:** Se rodar fora do Docker, lembre-se de alterar as variáveis de ambiente para `localhost`.

### 4. Healthchecks
O `docker-compose` está configurado para aguardar o banco estar "saudável" (healthcheck `pg_isready`) antes de subir a API. Isso evita erros de conexão na inicialização do Hibernate.

## 🛠️ Pontos de Atenção para o Funcionamento

Para que o sistema opere em sua capacidade total, atente-se aos seguintes detalhes técnicos:

- **Sincronização UTC-3:** O Brasil (Recife) opera em UTC-3. Todas as coletas de APIs externas (ANA/CEMADEN) que retornam UTC são convertidas automaticamente pela camada de `Service` para evitar discrepâncias nas predições.
- **Mapeamento de Estações (Haversine):** O sistema não depende de IDs estáticos. Ele busca as estações mais próximas de um `FloodPoint` em um raio configurável. Caso o mapeamento pareça incorreto, use o endpoint `/repair-stations`.
- **Memória do JVM:** Para ambientes de produção com grande ingestão de dados, recomenda-se ajustar as flags de memória no `docker-compose`: `JAVA_OPTS: "-Xmx512m -Xms256m"`.
- **Broker MQTT:** A API atua como um Subscriber. Se o broker estiver fora do ar, a API continuará funcionando para dados históricos, mas o streaming de tempo real será interrompido até o reconnect automático.

## 🧠 Inteligência Artificial (MAPI-AI)

O projeto MAPI utiliza um módulo de IA especializado para a predição de alagamentos. Este módulo consome dados via streaming da API para treinamento e inferência.

- **Fluxo AI-API:** A API expõe os dados brutos e processados, e o módulo de IA fornece as predições que são consumidas pela API para retornar aos usuários finais.
- **Repositório:** [projeto-mapi-ai](https://github.com/Lucas-Pavao/projeto-mapi-ai)

## 🚀 Como instalar e rodar

Para que tudo funcione corretamente, siga este ciclo completo de inicialização:

### 1. Preparação e Infraestrutura
Clone os repositórios e suba a stack docker. O banco será inicializado automaticamente com as **Hypertables** do TimescaleDB.
```bash
docker compose up -d --build
```

### 2. Ingestão Inicial de Dados (Bootstrapping)
Acesse o Swagger UI em `http://localhost:8080/swagger-ui.html` para disparar a carga inicial de dados históricos. Sem isso, o modelo de IA não terá dados para treinar.

1. **Reparar Estações:** Execute `POST /api/admin/ingestion/repair-stations` para mapear os pontos piloto às estações reais.
2. **Sincronização Total:** Execute `POST /api/admin/ingestion/historical-full-sync?years=5` para buscar dados climáticos e de sensores dos últimos 5 anos.
3. **Carga da Defesa Civil:** Use `POST /api/admin/ingestion/historical-civil-defense` com o ID do recurso CKAN (Recife) para importar as ocorrências históricas.

### 3. Treinamento do Modelo
Com os dados carregados no banco, dispare o treinamento no container de IA:
```bash
docker exec -it mapi-ai python main.py --mode train
```

### 4. Validação e Uso
Agora a API está pronta para fornecer predições em tempo real:
- **Verificar Saúde dos Dados:** `GET /api/admin/ingestion/check-integrity`
- **Consultar Predição:** `GET /api/pontos/{id_ponto}`

## 🤝 Como contribuir

1. Faça um **Fork** do projeto.
2. Crie uma **Branch** para sua modificação (`git checkout -b feature/novo-recurso`).
3. Siga as convenções em `GEMINI.md`.
4. Garanta que os testes passem: `mvn test`.
5. Abra um **Pull Request**.

## 📄 Licença

Este projeto está sob a licença **MIT**.

