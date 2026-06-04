# 🌊 Projeto MAPI - Instruções para Gemini

Bem-vindo ao repositório da API do Projeto MAPI. Este projeto é fundamental para a inteligência urbana da Região Metropolitana do Recife (RMR), focando na previsão de alagamentos.

## 📋 Visão Geral
- **Objetivo:** Integração 100% automatizada de dados de marés (APIs), clima, sensores (ANA/CEMADEN) e ocorrências urbanas (Dados Abertos) para treinamento de IA.
- **Tecnologias:** Java 21, Spring Boot, PostgreSQL, MQTT, API CKAN, OpenStreetMap (Nominatim).

## 🤖 Uso de Agentes
Este projeto utiliza uma estratégia de agentes especializados. Consulte [agents.md](./agents.md).

## 🛠️ Convenções de Código
- **Lombok:** Obrigatório (`@Data`, `@Builder`, `@Slf4j`).
- **DTOs:** Obrigatórios para entrada/saída em Controllers.
- **Sincronização:** Dados de sensores ANA/CEMADEN devem sempre ser convertidos de UTC para Local (UTC-3).
- **Geoprocessamento:** Ocorrências sem coordenadas devem ser geocodificadas (Placeholder para futuro serviço de geocodificação) usando o fallback Nominatim.

## 🚀 Fluxos Automatizados
1. **Ingestão Histórica:** O `HistoricalDataServiceImpl` realiza buscas profundas na ANA, APAC e Open-Meteo sincronizando séries temporais de 5 anos.
2. **Coleta em Tempo Real:** Integração 100% via **MQTT (ANA/CEMADEN/APAC)** garantindo baixa latência.
3. **Mapeamento Geográfico:** O mapeamento de estações pluviométricas para pontos de monitoramento é **dinâmico e baseado em proximidade (Haversine)**, gerenciado pelo `repairStationMappings`.
4. **Dataset Unificado:** O `DataExportServiceImpl` consolida Sensores + Clima + Maré + Labels em um único CSV para treinamento de IA.

---
*Nota: A ingestão via PDF foi descontinuada em favor da integração multi-fonte via API (TabuaMare/Open-Meteo).*
