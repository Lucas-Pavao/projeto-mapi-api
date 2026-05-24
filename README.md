# 🌊 Projeto MAPI - API de Monitoramento e Alerta de Previsão de Inundações

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![MQTT](https://img.shields.io/badge/MQTT-Enabled-blueviolet.svg)](https://mqtt.org/)

## 📋 Descrição Geral

O **Projeto MAPI** é uma solução de inteligência urbana voltada para o monitoramento, previsão e alerta de alagamentos, com foco inicial na cidade do Recife. Esta API atua como o núcleo do sistema, integrando dados críticos de múltiplas fontes:

- **Marinha do Brasil**: Extração automática de dados de marés a partir de documentos PDF oficiais.
- **Sensores IoT**: Recepção de dados em tempo real via protocolo MQTT (nível de água, umidade, etc.).
- **Previsão do Tempo**: Integração com serviços de meteorologia para análise combinada.

O objetivo principal é fornecer dados estruturados e precisos para que gestores urbanos e cidadãos possam antecipar eventos de inundação causados pela combinação de chuvas intensas e marés altas.

---

## ✨ Funcionalidades Principais (Features)

- **🔄 Processamento de Marés**:
    - Extração automatizada de horários e alturas de marés de arquivos PDF da Marinha.
    - Consulta de tábuas de maré por porto (ex: Porto do Recife, Suape) e data.
    - Upload manual e ingestão de arquivos históricos.
- **📡 Integração IoT (MQTT)**:
    - Broker MQTT configurável para escuta de tópicos de sensores.
    - Persistência assíncrona de leituras de sensores em campo.
    - Consulta de leituras recentes e histórico por sensor.
- **🌦️ Dados Meteorológicos**:
    - Integração com a API Open-Meteo para dados climáticos em tempo real.
- **🔐 Segurança e Controle**:
    - Autenticação robusta utilizando JWT (JSON Web Tokens).
    - Sistema de Refresh Tokens para manutenção de sessão.
    - Controle de acesso baseado em perfis (Admin, Piloto, Usuário).
- **📍 Gestão de Pontos de Alagamento**:
    - Cadastro e monitoramento de localizações críticas de inundação.
- **📖 Documentação Interativa**:
    - Interface Swagger UI para exploração e teste de todos os endpoints.

---

## 🛠️ Tecnologias Utilizadas

### Backend
- **Linguagem**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.0
- **Segurança**: Spring Security & JJWT
- **Processamento de PDF**: Apache PDFBox 3.0
- **Integração de Sistemas**: Spring Integration (MQTT, JPA, HTTP)
- **Documentação**: SpringDoc OpenAPI v2

### Banco de Dados & Persistência
- **Principal**: PostgreSQL
- **Testes**: H2 Database (In-memory)
- **ORM**: Spring Data JPA / Hibernate

---

## 📂 Arquitetura e Estrutura de Pastas

O projeto segue a arquitetura padrão do Spring Boot, organizada por camadas de responsabilidade:

```text
src/main/java/com/projeto/mapi/
├── config/           # Configurações globais (MQTT, Segurança, OpenAPI)
├── controller/       # Endpoints REST (Porta de entrada da API)
├── dto/              # Objetos de Transferência de Dados (Entrada/Saída)
├── exception/        # Tratamento global de erros e exceções customizadas
├── mapper/           # Conversores entre Entidades e DTOs
├── model/            # Entidades JPA (Mapeamento do Banco de Dados)
├── repository/       # Interfaces de acesso ao banco (Spring Data)
├── security/         # Lógica de JWT e filtros de autenticação
└── service/          # Regras de negócio e integrações
    ├── impl/         # Implementações das interfaces de serviço
    └── scheduler/    # Tarefas agendadas (jobs)
```

---

## ⚙️ Pré-requisitos

Antes de começar, você precisará ter instalado em sua máquina:
- [Java 21 JDK](https://www.oracle.com/java/technologies/downloads/)
- [Maven 3.9+](https://maven.apache.org/download.cgi)
- [PostgreSQL 15+](https://www.postgresql.org/download/)
- Um Broker MQTT (opcional para dev, o projeto aponta para o HiveMQ por padrão)

---

## 🚀 Como Executar o Projeto

### 1. Clonar o Repositório
```bash
git clone https://github.com/seu-usuario/projeto-mapi-api.git
cd projeto-mapi-api
```

### 2. Configurar Variáveis de Ambiente
O projeto utiliza o arquivo `application.yml`. Você pode criar variáveis de ambiente no seu SO ou passar diretamente via linha de comando. Seguem as principais:

| Variável | Descrição | Valor Padrão |
|----------|-----------|--------------|
| `POSTGRES_URL` | URL do Banco de Dados | `jdbc:postgresql://localhost:5432/tide_db` |
| `POSTGRES_USER` | Usuário do Banco | `mapi_user` |
| `POSTGRES_PASSWORD` | Senha do Banco | `mapi123` |
| `MQTT_BROKER_URL` | URL do Broker MQTT | `tcp://broker.hivemq.com:1883` |
| `JWT_SECRET` | Chave secreta para JWT | (String aleatória de 64 chars) |

### 3. Executar com Maven
```bash
mvn clean install
mvn spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

---

## 🛣️ Rotas da API / Exemplos de Uso

### Documentação Swagger
Acesse para visualizar todos os endpoints detalhadamente:
`http://localhost:8080/swagger-ui.html`

### Exemplos Rápidos

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/auth/login` | Autentica usuário e retorna tokens JWT. |
| `GET` | `/api/tide/{harbor}` | Retorna a tábua de maré de um porto específico. |
| `GET` | `/api/sensors/latest` | Lista as últimas leituras de todos os sensores. |
| `POST` | `/api/tide/upload` | (Admin) Faz upload de PDF da marinha para processamento. |

---

## 🤝 Como Contribuir

1. Faça um **Fork** do projeto.
2. Crie uma **Branch** para sua feature (`git checkout -b feature/NovaFeature`).
3. Faça **Commit** de suas mudanças (`git commit -m 'Adicionando nova funcionalidade'`).
4. Faça **Push** da sua branch (`git push origin feature/NovaFeature`).
5. Abra um **Pull Request**.

---

## 📄 Licença

Este projeto está sob a licença **MIT**. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---
*Desenvolvido para fortalecer a resiliência urbana e a segurança da população.* 🌊🏙️
