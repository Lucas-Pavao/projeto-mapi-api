# 🌊 Projeto MAPI - API de Monitoramento e Alerta de Previsão de Inundações

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![MQTT](https://img.shields.io/badge/MQTT-Enabled-blueviolet.svg)](https://mqtt.org/)

## 📋 Descrição Geral

O **Projeto MAPI** é uma solução de inteligência urbana voltada para o monitoramento, previsão e alerta de alagamentos na Região Metropolitana do Recife (RMR). Esta API integra dados críticos de múltiplas fontes para treinar modelos de IA e fornecer alertas em tempo real.

---

## ✨ Funcionalidades Principais (Features)

- **📡 Integração IoT (MQTT)**:
    - Escuta ativa de tópicos de sensores **ANA, CEMADEN e APAC**.
    - Persistência assíncrona de pluviometria, nível de água, umidade do solo e estado de bateria.
- **🌦️ Inteligência Hidrometeorológica**:
    - Integração com **Open-Meteo API** para dados históricos e previsões (Chuva, Temperatura, Pressão Atmosférica).
    - Mapeamento dinâmico de estações pluviométricas via proximidade geográfica (Haversine).
- **🌊 Gestão de Marés**:
    - Integração multi-fonte (TabuaMare API / Open-Meteo Marine) para o Porto do Recife e Suape.
- **🚨 Dados da Defesa Civil**:
    - Ingestão automatizada de ocorrências históricas via **API CKAN (Dados Abertos)** para rotulagem de eventos de alagamento.
- **📊 Dataset para IA**:
    - Exportação consolidada de séries temporais unificando Clima + Sensores + Maré + Ocorrências para treinamento de modelos preditivos.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.0
- **Integração**: Spring Integration (MQTT, JPA, HTTP)
- **Banco de Dados**: PostgreSQL (Principal) / H2 (Testes)
- **Geoprocessamento**: Nominatim (OSM) para geocodificação reversa.
- **Documentação**: SpringDoc OpenAPI v2 (Swagger)

---

## 🚀 Como Executar o Projeto

### 1. Pré-requisitos
- Java 21 JDK
- Maven 3.9+
- PostgreSQL 15+

### 2. Configuração
Configure as credenciais do banco e do broker MQTT no `src/main/resources/application.yml` ou via variáveis de ambiente.

### 3. Execução
```bash
mvn clean install
mvn spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

---

## 📌 Documentação da API (Endpoints)

A API do MAPI está organizada por módulos funcionais. Para detalhes técnicos completos (esquemas de JSON, parâmetros), utilize o **Swagger UI** em `http://localhost:8080/swagger-ui.html`.

### 1. 🔍 Monitoramento e Inteligência (MAPI Core)
Estes endpoints consolidam dados de múltiplas fontes (Sensores + Maré + Clima) para fornecer uma visão em tempo real.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/mapi/precisao` | Retorna dados consolidados para uma latitude/longitude específica (incluindo o sensor mais próximo). |
| `GET` | `/api/mapi/pontos-piloto` | Lista os dados em tempo real para todos os pontos críticos cadastrados. |
| `GET` | `/api/fp` | Lista todos os Pontos de Alagamento (Flood Points) configurados. |
| `POST` | `/api/fp` | Cadastra um novo ponto com geocodificação automática e mapeamento de sensores. |

### 2. 📊 Exportação para IA (Data Science)
Focado na geração de massa de dados para treinamento e validação de modelos preditivos.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/export/training-set` | Gera e baixa um arquivo **CSV Mestre** unificando todo o histórico de sensores, clima, marés e ocorrências. |
| `GET` | `/api/export/health-report` | Retorna um relatório de "saúde" dos dados (identifica gaps de séries temporais). |

### 3. 📡 Sensores e Marés
Acesso direto aos dados brutos coletados via MQTT e APIs externas.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/sensors/latest` | Obtém a leitura mais recente de todos os sensores (ANA, APAC, CEMADEN). |
| `GET` | `/api/sensors/history/{id}` | Histórico completo de leituras de um sensor específico. |
| `GET` | `/api/tide/{harbor}` | Consulta a tábua de maré prevista para um porto (Porto do Recife ou Suape). |

### 4. ⚙️ Administração e Ingestão Histórica
Endpoints de alta carga para sincronização de séries temporais.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/admin/ingestion/historical-full-sync` | Dispara o processo global de sincronização profunda (5 anos atrás até hoje). |
| `POST` | `/api/admin/ingestion/repair-stations` | Reavalia geograficamente qual estação sensor é a melhor para cada ponto. |
| `POST` | `/api/admin/ingestion/align-events` | Alinha o horário de ocorrências da Defesa Civil ao pico de chuva medido no dia. |
| `DELETE` | `/api/admin/ingestion/wipe-database` | **CUIDADO**: Reseta 100% o banco de dados (limpeza total). |

### 5. 🔐 Autenticação
Segurança baseada em JWT.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/auth/register` | Cria um novo usuário no sistema. |
| `POST` | `/api/auth/login` | Autentica e retorna o `AccessToken` e `RefreshToken`. |
| `POST` | `/api/auth/refresh` | Renova um token expirado. |

---
*Desenvolvido para fortalecer a resiliência urbana e a inteligência climática.* 🌊🏙️
