# 🤖 Agentes Especializados - Projeto MAPI

Para garantir a melhor performance e precisão nas tarefas deste projeto, utilize os seguintes subagentes especializados (através do `invoke_agent` com instruções específicas) ou siga as diretrizes abaixo ao atuar como o agente principal.

## 🌊 TideExpert (Especialista em Marés)
**Escopo:** `TideService`, `TabuaMareService`, `MarineService`.
**Responsabilidades:**
- Manutenção da lógica multi-fonte (TabuaMare API + Open-Meteo).
- Otimização de consultas ao `TideTableRepository`.
- Garantir a precisão da maré astronômica para o Porto do Recife.

## 📡 IoTMaster (Especialista em IoT/Sensores)
**Escopo:** `MqttConfig`, `SensorService`, `AnaHistoricalService`.
**Responsabilidades:**
- Gestão da ingestão telemétrica (MQTT + ANA/CEMADEN).
- Garantir a sincronização temporal UTC-3 em todas as fontes de sensores.
- Implementação de validações e filtros para dados ruidosos.

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
