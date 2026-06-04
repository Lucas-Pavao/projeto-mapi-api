# Projeto MAPI - API Central 🌊🚀

A **API MAPI** é o núcleo do ecossistema de resiliência urbana da Região Metropolitana do Recife. Ela atua como o orquestrador central, integrando dados meteorológicos, marés astronômicas e sensores IoT em tempo real para alimentar modelos de Machine Learning e fornecer dados consolidados para o frontend.

## 🛠️ Tecnologias Escolhidas

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.4.0
- **Banco de Dados:** PostgreSQL 16 com TimescaleDB (Séries Temporais)
- **Mensageria:** MQTT (Paho Client) para integração com sensores
- **Segurança:** Spring Security e JWT (JSON Web Tokens)
- **Documentação:** SpringDoc OpenAPI (Swagger)

## ✨ Funcionalidades / Features

- 📡 **Ingestão Multi-fonte:** Coleta automatizada de dados da ANA, APAC, CEMADEN e Open-Meteo.
- 🔄 **Processamento em Tempo Real:** Captura e armazenamento de dados de sensores via MQTT com baixa latência.
- 🧠 **Geração de Datasets:** Fluxos de exportação de dados sincronizados para treinamento de modelos de IA.
- 🗺️ **Geoprocessamento:** Agregação de dados por proximidade espacial (Haversine) e geocodificação.
- 🔐 **Gestão de Identidade:** Autenticação robusta para administração e acesso seguro aos dados.

## 📂 Estrutura de Pastas

```text
projeto-mapi-api/
├── src/
│   ├── main/
│   │   ├── java/com/projeto/mapi/
│   │   │   ├── config/          # Configurações (Security, MQTT, Beans)
│   │   │   ├── controller/      # Endpoints REST
│   │   │   ├── dto/             # Objetos de Transferência de Dados
│   │   │   ├── model/           # Entidades JPA e Tabelas Timescale
│   │   │   ├── repository/      # Interfaces de acesso ao banco
│   │   │   ├── service/         # Lógica de negócio e integrações
│   │   │   └── util/            # Helpers e cálculos geográficos
│   │   └── resources/
│   │       └── application.yml  # Configurações do Spring
│   └── test/                    # Testes unitários e integração
├── Dockerfile                   # Definição do container da API
├── docker-compose.yml           # Orquestração do ecossistema
├── pom.xml                      # Gestão de dependências Maven
└── TimescaleSetup.sql           # Script de inicialização do banco
```

## 📋 Pré-requisitos

- Java 21 JDK.
- Maven 3.8+.
- Docker e Docker Compose (opcional, mas recomendado).
- PostgreSQL com extensão TimescaleDB.

## 🚀 Como instalar e rodar

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/Lucas-Pavao/projeto-mapi-api.git
   cd projeto-mapi-api
   ```

2. **Via Docker (Recomendado):**
   ```bash
   docker compose up --build
   ```

3. **Execução Local (Maven):**
   - Configure o banco de dados no `src/main/resources/application.yml`.
   - Execute:
   ```bash
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

A API estará disponível em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui.html`.

## 🤝 Como contribuir

1. Faça um **Fork** do projeto.
2. Crie uma **Branch** para sua modificação (`git checkout -b feature/novo-endpoint`).
3. Faça o **Commit** de suas alterações (`git commit -m 'Add: novo endpoint de sensores'`).
4. Faça o **Push** para a sua Branch (`git push origin feature/novo-endpoint`).
5. Abra um **Pull Request**.

## 📄 Licença

Este projeto está sob a licença **MIT**.
