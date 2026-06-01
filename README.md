# 🌊 Projeto MAPI - API de Monitoramento e Alerta de Previsão de Inundações

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![MQTT](https://img.shields.io/badge/MQTT-Enabled-blueviolet.svg)](https://mqtt.org/)

## 📋 Descrição Geral

O **Projeto MAPI** é uma solução de inteligência urbana voltada para o monitoramento, previsão e alerta de alagamentos na Região Metropolitana do Recife (RMR). Esta API integra dados críticos de múltiplas fontes para treinar modelos de IA e fornecer alertas em tempo real.

---

## ✨ Funcionalidades Principais (Features)

- **📡 Integração IoT (MQTT)**: Escuta ativa de sensores ANA, CEMADEN e APAC.
- **🌦️ Inteligência Hidrometeorológica**: Integração com Open-Meteo API e mapeamento dinâmico de estações.
- **🌊 Gestão de Marés**: Integração multi-fonte (TabuaMare / Open-Meteo Marine).
- **🚨 Dados da Defesa Civil**: Ingestão automatizada de ocorrências históricas via API CKAN.
- **📊 Dataset para IA**: Exportação consolidada de séries temporais para treinamento de modelos.

---

## 🚀 Como Executar o Projeto

### 1. Pré-requisitos
- Java 21 JDK
- Maven 3.9+
- PostgreSQL 15+

### 2. Configuração
Configure as credenciais do banco e do broker MQTT no `src/main/resources/application.yml`.

### 3. Execução
```bash
mvn clean install
mvn spring-boot:run
```

---

## 📌 Documentação Completa dos Endpoints

Abaixo estão todos os endpoints disponíveis na API, organizados por categoria. Para detalhes técnicos de payloads, utilize o **Swagger UI** em `http://localhost:8080/swagger-ui.html`.

### 1. ⚙️ Administração e Ingestão (Histórico)
Endpoints de alta carga para carregar dados e sincronizar séries temporais.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/admin/ingestion/repair-stations` | Reavalia e repara o mapeamento de estações pluviométricas dos pontos. |
| `POST` | `/api/admin/ingestion/historical-weather` | Inicia ingestão de histórico de chuva (Open-Meteo) para todos os pontos. |
| `POST` | `/api/admin/ingestion/historical-sensors` | Inicia ingestão de histórico de sensores (ANA) para todos os pontos. |
| `POST` | `/api/admin/ingestion/historical-full-sync` | Executa sincronização TOTAL (Clima, ANA, APAC, Defesa Civil) de 5 anos. |
| `POST` | `/api/admin/ingestion/historical-civil-defense` | Inicia ingestão de histórico da Defesa Civil via CKAN (ID específico). |
| `POST` | `/api/admin/ingestion/historical-apac` | Inicia ingestão de histórico de chuva (APAC) para uma estação/ano. |
| `POST` | `/api/admin/ingestion/align-events` | Alinha eventos da Defesa Civil (00:00) ao pico de chuva real do dia. |
| `GET` | `/api/admin/ingestion/check-integrity` | Verifica a integridade dos dados (Gaps e quantidades no banco). |
| `DELETE` | `/api/admin/ingestion/wipe-database` | **LIMPA TODO O BANCO DE DADOS** (Clima, Sensores e Eventos). |

### 2. 🌦️ Clima (Weather)
Consulta direta ao serviço de meteorologia Open-Meteo.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/weather` | Obter dados climáticos atuais (lat/lon) via Open-Meteo. |

### 3. 📊 Exportação de Dados (IA)
Geração de datasets unificados para treinamento de modelos de IA.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/export/ia-dataset/{slug}` | Exporta dataset consolidado (Sensores + Clima + Maré + Labels) para um ponto. |
| `GET` | `/api/export/ia-dataset/{slug}/csv` | Exporta o dataset de um ponto específico em formato CSV. |
| `GET` | `/api/export/ia-dataset/all/csv` | Exporta dataset unificado de **TODOS** os pontos em formato CSV único. |
| `GET` | `/api/export/health-report` | Relatório detalhado sobre a densidade e saúde dos dados históricos. |

### 4. 🔍 MAPI (Pontos e Monitoramento)
Endpoints integrados do núcleo do Projeto MAPI.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/pontos` | Lista todos os pontos de monitoramento registrados. |
| `POST` | `/api/pontos` | Registra um novo ponto de monitoramento de alagamento. |
| `GET` | `/api/precise-data` | Busca dados ambientais precisos fundindo sensores locais e Open-Meteo. |
| `GET` | `/api/pontos/{id_ponto}` | Busca o status consolidado em tempo real de um ponto específico (slug). |

### 5. 📡 Sensores IoT
Monitoramento direto das leituras recebidas via MQTT/Sensores físicos.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/sensors/latest` | Ver todas as leituras recentes de todos os sensores cadastrados. |
| `GET` | `/api/sensors/{sensorId}/latest` | Ver a leitura mais recente de um sensor específico. |
| `GET` | `/api/sensors/{sensorId}/history` | Ver o histórico de leituras de um sensor específico. |
| `GET` | `/api/sensors/ids` | Listar todos os IDs de sensores únicos detectados no sistema. |

### 6. 🌊 Marine Data & Marés (Open-Meteo)
Integração com dados oceânicos.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/marine` | Obter dados marinhos (ondas, etc) por latitude e longitude. |
| `GET` | `/api/marine/wave-height` | Obter a altura da onda atual. |

### 7. 📅 Tábua de Maré (DevTu)
Integração direta com o serviço TabuaMare API.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/tabua-mare/tide/{harbor}/{month}/{days}` | Obter tábua de maré por porto e período. |
| `GET` | `/api/tabua-mare/states` | Listar todos os estados costeiros brasileiros suportados. |
| `GET` | `/api/tabua-mare/nearest` | Encontrar o porto mais próximo de uma coordenada. |
| `GET` | `/api/tabua-mare/harbors/{ids}` | Obter informações de portos específicos por seus IDs. |
| `GET` | `/api/tabua-mare/harbors/state/{state}` | Listar todos os portos de um estado específico. |

### 8. 🚨 Eventos de Alagamento (Ground Truth)
Gestão de ocorrências confirmadas de inundações (Labels).

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/eventos-alagamento` | Registra a ocorrência real de um alagamento em um ponto (via slug). |
| `POST` | `/api/eventos-alagamento/ingest` | Ingere dados brutos via scraper manual (usando coordenadas). |
| `GET` | `/api/eventos-alagamento/{slug}` | Retorna o histórico de alagamentos confirmados de um ponto específico. |

### 9. ⚓ Tide (Multi-fonte)
Fachada unificada para consulta de marés.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/tide/{harbor}` | Obter tábua de maré para um porto específico (Porto do Recife/Suape). |
| `GET` | `/api/tide/state/{state}` | Listar tábuas de maré de todos os portos de um estado. |
| `GET` | `/api/tide/search` | Pesquisar portos disponíveis por nome. |
| `GET` | `/api/tide/harbors` | Listar todos os nomes de portos cadastrados no sistema. |

### 10. 🔐 Autenticação
Segurança e gestão de usuários.

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/auth/register` | Cadastra um novo usuário no sistema. |
| `POST` | `/api/auth/login` | Realiza login e gera tokens JWT (Access e Refresh). |
| `POST` | `/api/auth/refresh` | Utiliza o Refresh Token para renovar o acesso sem novo login. |

---
*Desenvolvido para fortalecer a resiliência urbana e a inteligência climática.* 🌊🏙️
