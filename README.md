# 🌊 Projeto MAPI - API de Inteligência Urbana

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![TimescaleDB](https://img.shields.io/badge/TimescaleDB-PostgreSQL%2016-blue.svg)](https://www.timescale.com/)
[![MQTT](https://img.shields.io/badge/MQTT-Paho-772277.svg)](https://mqtt.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

O **MAPI (Monitoramento de Alagamentos e Predição Inteligente)** é uma API robusta projetada para servir como o "sistema nervoso" central de uma plataforma de resiliência urbana na Região Metropolitana do Recife (RMR). Ela integra dados meteorológicos, marés astronômicas e sensores IoT em tempo real para alimentar modelos de Machine Learning especializados na previsão de eventos de inundação.

---

## ✨ Funcionalidades Principais

*   📡 **Ingestão Multi-fonte:** Coleta automatizada de dados da ANA, APAC, CEMADEN, Marinha do Brasil e Open-Meteo.
*   🔄 **Processamento em Tempo Real:** Integração via **MQTT** para captura de dados de sensores com baixa latência.
*   🧠 **Ground Truth Alignment:** Algoritmo exclusivo para sincronizar registros históricos de alagamentos (labels) com picos regionais de precipitação.
*   📊 **Dataset Engineering:** Geração de fluxos contínuos (Streaming JSON) para treinamento e inferência de modelos de IA.
*   🗺️ **Geoprocessamento Inteligente:** Agregação de dados por proximidade espacial (Raio de 5km) para redundância e precisão.
*   🔐 **Segurança:** Autenticação e autorização robustas via **JWT (JSON Web Token)**.

---

## 🛠️ Tecnologias Escolhidas

### **Backend Core**
*   **Java 21 & Spring Boot 3.4.0:** Base de alto desempenho e escalabilidade.
*   **Spring Data JPA:** Abstração de persistência eficiente.
*   **Spring Security & JJWT:** Proteção de endpoints e gestão de identidade.
*   **Spring Integration:** Orquestração de fluxos de dados e integração MQTT.

### **Persistência e Dados**
*   **TimescaleDB (PostgreSQL 16):** Banco de dados otimizado para séries temporais e geoprocessamento.
*   **Paho MQTT:** Cliente para comunicação com brokers de sensores IoT.
*   **Apache Commons CSV & POI:** Manipulação de grandes volumes de dados para exportação.

### **Documentação e Utilitários**
*   **SpringDoc OpenAPI (Swagger):** Documentação interativa da API.
*   **Lombok:** Redução de boilerplate e código mais limpo.
*   **Jsoup:** Web scraping para fontes de dados que não possuem API rest.

---

## 📂 Estrutura de Pastas

```text
projeto-mapi-api/
├── 📁 src/
│   ├── 📁 main/
│   │   ├── 📁 java/com/projeto/mapi/
│   │   │   ├── 📁 config/          # Configurações de Beans, Security, MQTT
│   │   │   ├── 📁 controller/      # Endpoints REST (API)
│   │   │   ├── 📁 dto/             # Objetos de Transferência de Dados
│   │   │   ├── 📁 exception/       # Tratamento global de erros
│   │   │   ├── 📁 mapper/          # Conversores entre Entidades e DTOs
│   │   │   ├── 📁 model/           # Entidades do JPA e Tabelas Timescale
│   │   │   ├── 📁 repository/      # Interfaces de acesso ao banco
│   │   │   ├── 📁 security/        # Filtros e serviços de autenticação
│   │   │   ├── 📁 service/         # Lógica de negócio e integrações
│   │   │   └── 📁 util/            # Helpers e cálculos geográficos
│   │   └── 📁 resources/
│   │       ├── application.yml     # Configurações do Spring
│   │       └── 📁 static/          # Arquivos estáticos (se houver)
│   └── 📁 test/                    # Testes unitários e de integração
├── 📄 Dockerfile                   # Definição do container da API
├── 📄 docker-compose.yml           # Orquestração do ecossistema (DB, API, AI)
├── 📄 pom.xml                      # Gestão de dependências Maven
└── 📄 TimescaleSetup.sql           # Inicialização do banco de dados
```

---

## 🚀 Como Executar

### **1. Via Docker (Recomendado)**

Certifique-se de ter o Docker e Docker Compose instalados.

```bash
# Clone o repositório
git clone https://github.com/seu-usuario/projeto-mapi-api.git
cd projeto-mapi-api

# Suba todos os serviços (Banco, API e Cérebro IA)
docker compose up --build
```

A API estará disponível em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui.html`.

### **2. Desenvolvimento Local (Maven)**

Se preferir rodar apenas a API localmente:

1.  Tenha um PostgreSQL/TimescaleDB rodando (pode usar o container do banco separadamente).
2.  Configure as variáveis de ambiente no `application.yml` ou via export.

```bash
# Compilar o projeto
./mvnw clean install

# Rodar a aplicação
./mvnw spring-boot:run
```

---

## ⚙️ Configuração

As seguintes variáveis de ambiente podem ser configuradas:

| Variável | Descrição | Padrão |
|----------|-----------|---------|
| `POSTGRES_URL` | URL de conexão com o banco | `jdbc:postgresql://localhost:5433/tide_db` |
| `POSTGRES_USER` | Usuário do banco | `mapi_user` |
| `POSTGRES_PASSWORD`| Senha do banco | `mapi123` |
| `AI_API_URL` | URL do serviço de IA (Python) | `http://localhost:8000` |
| `JWT_SECRET` | Chave secreta para tokens JWT | (Gerada no boot se ausente) |

---

## 📖 Documentação da API

Acesse o Swagger UI para explorar os endpoints disponíveis:
👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Principais Endpoints:
*   `POST /api/auth/login`: Autenticação e geração de token.
*   `GET /api/precise-data`: Retorna dados consolidados e predição de risco.
*   `POST /api/admin/ingestion/align-events`: Sincroniza labels históricos.

---

## 🤝 Contribuição

1. Faça um **Fork** do projeto.
2. Crie uma **Branch** para sua feature (`git checkout -b feature/nova-feature`).
3. Dê um **Commit** nas suas alterações (`git commit -m 'Add: nova feature'`).
4. Faça um **Push** para a Branch (`git push origin feature/nova-feature`).
5. Abra um **Pull Request**.

---

## ⚖️ Licença

Distribuído sob a licença MIT. Veja `LICENSE` para mais informações.

---
*MAPI: Inteligência de dados para uma Recife mais resiliente.* 🌊🏙️
