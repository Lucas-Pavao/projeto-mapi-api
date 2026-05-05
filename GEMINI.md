# 🌊 Projeto MAPI - Instruções para Gemini

Bem-vindo ao repositório da API do Projeto MAPI. Este projeto é fundamental para a inteligência urbana do Recife, focando na previsão de alagamentos.

## 📋 Visão Geral
- **Objetivo:** Integração de dados de marés (Marinha), clima e sensores IoT para previsão de enchentes.
- **Tecnologias:** Java 21, Spring Boot, PostgreSQL, MQTT, Playwright, PDFBox.

## 🤖 Uso de Agentes
Este projeto utiliza uma estratégia de agentes especializados para lidar com diferentes domínios de conhecimento. Consulte o arquivo [agents.md](./agents.md) para detalhes sobre as responsabilidades de cada perfil.

- **Para mudanças na lógica de maré:** Siga as diretrizes do `TideExpert`.
- **Para integração com sensores:** Siga as diretrizes do `IoTMaster`.
- **Para segurança e autenticação:** Siga as diretrizes do `SecurityGuard`.

## 🛠️ Convenções de Código
- **Lombok:** Utilize anotações do Lombok (`@Data`, `@Builder`, `@Slf4j`) para reduzir boilerplate.
- **Exceções:** Use o `GlobalExceptionHandler` e crie exceções específicas em `com.projeto.mapi.exception`.
- **DTOs:** Sempre use DTOs para entrada/saída nos controllers, nunca exponha entidades JPA diretamente.
- **Testes:** Novos serviços devem vir acompanhados de testes unitários em `src/test/java`.

## 🚀 Fluxos Importantes
1. **Scraping de Marés:** A lógica principal reside em `NavyScraperServiceImpl`.
2. **Ingestão de Dados:** O processamento de dados brutos para o banco de dados é feito em `TideIngestionServiceImpl`.
3. **Sensores:** Dados via MQTT são processados assincronamente e salvos no `SensorDataRepository`.

---
*Nota: Estas instruções são fundamentais e devem ser seguidas rigorosamente por todos os agentes que operam neste repositório.*
