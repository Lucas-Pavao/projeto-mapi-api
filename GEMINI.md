# 🌊 Projeto MAPI - Instruções Estratégicas para Gemini

Bem-vindo ao repositório do **Projeto MAPI** (Monitoramento e Alerta de Pontos de Inundação). Esta API é o núcleo de inteligência urbana para a Região Metropolitana do Recife (RMR), focada na antecipação de eventos climáticos extremos.

## 📋 Visão Geral do Sistema
O MAPI atua como um hub integrador de múltiplas fontes de dados críticos:
- **Marés:** Integração com TabuaMare API e fallback via Open-Meteo.
- **Clima:** Dados em tempo real e históricos via Open-Meteo.
- **Sensores (Pluviômetros/Nível):** Coleta via MQTT e APIs da ANA, APAC e CEMADEN.
- **Ocorrências Urbanas:** Ingestão de dados abertos (CKAN) da Prefeitura do Recife.

## 🤖 Estratégia de Agentes
Este projeto utiliza agentes especializados para garantir a integridade técnica. Consulte o arquivo [agents.md](./agents.md) para delegar tarefas específicas.

## 🛠️ Convenções e Padrões de Código
- **Java 21 & Spring Boot 3.4:** Utilizar as últimas features da linguagem (Records, Pattern Matching).
- **Lombok:** Obrigatório (`@Data`, `@Builder`, `@Slf4j`). Evite `getter/setter` manuais.
- **DTOs:** Obrigatórios. NUNCA exponha entidades JPA diretamente nos Controllers.
- **Sincronização Temporal:** 
  - Armazenamento em UTC.
  - Conversão para Local (UTC-3) na camada de serviço/exibição.
  - Garantir que dados da ANA/CEMADEN sejam normalizados para o fuso local.
- **Tratamento de Erros:** Seguir o padrão `GlobalExceptionHandler` e `ErrorMessage`.

## 🗄️ Persistência e Performance
- **TimescaleDB:** O PostgreSQL utiliza a extensão TimescaleDB para gerenciar `sensor_data` e `weather_data` como **Hypertables**.
- **Agregados:** Utilize Continuous Aggregates (Views Materializadas) para consultas de média/máximo de chuva por hora/dia.

## 🚀 Fluxos Principais
1. **Ingestão Histórica:** O `HistoricalDataServiceImpl` sincroniza séries temporais de 5 anos para treinamento de modelos.
2. **Tempo Real (MQTT):** Fluxo contínuo via `MqttConfig` para dados de sensores críticos.
3. **Geoprocessamento:** Ocorrências sem coordenadas usam o fallback Nominatim (`NominatimGeocodingServiceImpl`).
4. **Exportação de Dataset:** O `DataExportServiceImpl` consolida Sensores + Clima + Maré + Labels em CSV para IA.

## 🧪 Qualidade e Testes
- **Testes Unitários:** Obrigatórios para lógicas de parsing e cálculo (JUnit 5 + Mockito).
- **Testes de Integração:** Utilizar H2 (em memória) para validar repositórios e fluxos JPA.
- **Mocking:** Sempre mockar APIs externas (ANA, Open-Meteo) para garantir testes determinísticos.

---
*Nota: A arquitetura prioriza composição sobre herança e segue os princípios SOLID para facilitar a inclusão de novas fontes de dados.*
