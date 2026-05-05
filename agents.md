# 🤖 Agentes Especializados - Projeto MAPI

Para garantir a melhor performance e precisão nas tarefas deste projeto, utilize os seguintes subagentes especializados (através do `invoke_agent` com instruções específicas) ou siga as diretrizes abaixo ao atuar como o agente principal.

## 🌊 TideExpert (Especialista em Marés)
**Escopo:** `NavyScraperService`, `PdfConversionService`, `TideIngestionService`, `TideController`.
**Responsabilidades:**
- Manutenção da lógica de scraping da Marinha (Playwright/Jsoup).
- Ajustes no parsing de PDFs (PDFBox) para extração de tabelas de maré.
- Otimização de consultas ao `TideTableRepository`.
- Garantir que a ingestão de dados trate corretamente fusos horários e formatos de data.

## 📡 IoTMaster (Especialista em IoT/MQTT)
**Escopo:** `MqttConfig`, `SensorService`, `SensorDataRepository`.
**Responsabilidades:**
- Configuração de conectores MQTT (Spring Integration).
- Tratamento de mensagens de sensores e persistência de `SensorData`.
- Implementação de validações para dados recebidos via telemetria.
- Simulação de dispositivos IoT para testes de integração.

## 🔒 SecurityGuard (Guardião da Segurança)
**Escopo:** `SecurityConfig`, `JwtService`, `AuthController`, `AuthenticationService`.
**Responsabilidades:**
- Gestão do ciclo de vida de JWT e Refresh Tokens.
- Implementação de regras de autorização granulares.
- Proteção contra vulnerabilidades comuns (OWASP).
- Garantir que segredos e chaves nunca sejam logados ou expostos.

## 🏗️ ProjectArchitect (Arquiteto do Projeto)
**Escopo:** `pom.xml`, `ApplicationConfig`, estrutura de pacotes.
**Responsabilidades:**
- Manter a consistência arquitetural entre os serviços.
- Gestão de dependências e versões no Maven.
- Garantir que novos controllers e serviços sigam o padrão do projeto.
- Revisar a cobertura de testes e padrões de documentação (OpenAPI).
