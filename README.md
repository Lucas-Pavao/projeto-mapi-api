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

## 📌 Principais Endpoints

Acesse o Swagger para detalhes: `http://localhost:8080/swagger-ui.html`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/mapi/precisao` | Consulta consolidada em tempo real para lat/lon. |
| `GET` | `/api/export/training-set` | Gera CSV mestre para treinamento de IA. |
| `POST` | `/api/fp` | Cadastra ponto de alagamento com auto-mapeamento de sensores. |
| `POST` | `/api/admin/ingestion/historical-full-sync` | Dispara sincronização histórica de 5 anos. |

---
*Desenvolvido para fortalecer a resiliência urbana e a inteligência climática.* 🌊🏙️
