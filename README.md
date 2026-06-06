# MAPI API - Núcleo Central e Orquestração 🌊🚀

A **MAPI API** é o núcleo analítico e operacional do ecossistema de resiliência urbana da Região Metropolitana do Recife (RMR). Ela atua como o **orquestrador central**, integrando fontes telemétricas, previsões climáticas e dados oceanográficos para alimentar o motor de IA e fornecer inteligência para a prevenção de alagamentos.

## 🌐 Ecossistema MAPI

Este projeto centraliza e gerencia o fluxo de dados do ecossistema:

```text
  [ MAPI Edge ] (Python / MQTT) 📡
        │   (Pulsações Telemétricas e Inteligência de Borda)
        ▼
  [  MAPI API  ] (Java 21 / Spring Boot / TimescaleDB) 🌊🚀 <-- (Este Serviço)
        │ ▲
        │ │ (Dados em Tempo Real via HTTP POST / Resposta com Probabilidade e Risco)
        ▼ │
  [  MAPI AI  ] (Python / FastAPI / XGBoost & LSTM) 🧠
        │
        │ (Consumo da REST API e Exibição Geoespacial)
        ▼
  [ MAPI Front ] (React 19 / MapLibre GL) 💻✨
```

### Conexões Estruturais:
- **Entrada (Ingestão):** Assinante MQTT conectado ao Broker, coletando dados gerados pelo MAPI Edge e APIs de agências (ANA, APAC, CEMADEN).
- **Processamento Síncrono:** Dispara requisições HTTP POST síncronas para o MAPI AI para receber métricas preditivas de risco de alagamento.
- **Saída (Exposição):** Persiste dados no banco temporal (TimescaleDB) e expõe endpoints REST protegidos por JWT para o MAPI Front.

## 🛠️ Tecnologias Escolhidas

| Categoria | Tecnologia | Justificativa Técnica |
| :--- | :--- | :--- |
| **Linguagem** | Java 21 (LTS) | Virtual Threads para alta concorrência e Records para imutabilidade. |
| **Framework** | Spring Boot 3.4.0 | Injeção de dependências robusta e gerenciamento de tarefas agendadas. |
| **Banco de Dados** | PostgreSQL 16 + TimescaleDB | Extensões de séries temporais (Hypertables) para indexação analítica. |
| **Mensageria** | MQTT (Paho Client) | Captura reativa orientada a eventos de baixa latência para sensores. |
| **Segurança** | Spring Security + JWT | Controle estrito de acesso e ciclo de vida de tokens. |
| **Documentação** | OpenAPI 3.0 (Swagger) | Contrato claro de endpoints para integração facilitada. |

## 🏗️ Arquitetura e Especialização

O desenvolvimento é orientado por agentes especializados para garantir a integridade dos domínios:

- 🌊 **TideExpert:** Domínio analítico de marés astronômicas (Porto do Recife/Marinha).
- 📡 **IoTMaster:** Ingestão de streams telemétricos via MQTT e tratamento de fuso horário UTC-3.
- 🔒 **SecurityGuard:** Controlador do ciclo de vida de tokens JWT e perfis de acesso.
- 🏗️ **ProjectArchitect:** Guardião dos padrões Clean Architecture e coesão do sistema.

## 📂 Estrutura de Pastas Detalhada

A organização segue o padrão Clean Architecture adaptado para Spring Boot:

```text
projeto-mapi-api/
├── agents.md                    # Estratégia de agentes especializados
├── docker-compose.yml           # Orquestração da Stack (API + DB + AI)
├── Dockerfile                   # Build multi-stage otimizado para Java 21
├── GEMINI.md                    # Dicionário de convenções e regras de ouro
├── pom.xml                      # Gestão de dependências Maven
├── TimescaleSetup.sql           # Script crítico de inicialização de Hipertabelas
└── src/
    ├── main/
    │   ├── java/com/projeto/mapi/
    │   │   ├── config/          # Beans de Configuração (MQTT, Security, Scheduling)
    │   │   ├── controller/      # Camada REST (Endpoints Públicos e Administrativos)
    │   │   ├── dto/             # Data Transfer Objects (Imutabilidade)
    │   │   ├── exception/       # Handlers globais de erro
    │   │   ├── mapper/          # Conversores de Entidade/DTO
    │   │   ├── model/           # Entidades JPA (Mapeamento TimescaleDB)
    │   │   ├── repository/      # Interfaces Spring Data (JPA/Timescale)
    │   │   ├── security/        # Lógica de Filtros e JWT
    │   │   ├── service/         # Interfaces de Negócio
    │   │   │   └── impl/        # Implementações (ANA, APAC, Civil Defense)
    │   │   └── util/            # Helpers (GeoUtils, MqttValidator)
    │   └── resources/
    │       └── application.yml  # Configurações de Ambiente
    └── test/                    # Suite de testes unitários e de integração
```

## 🐳 Infraestrutura e Docker: Pontos Críticos de Orquestração

1. **Build Multi-Stage:** O `Dockerfile` isola o ambiente de compilação (Maven) do runtime (JRE 21), garantindo imagens leves e seguras.
2. **Sincronia do TimescaleDB:** O script `TimescaleSetup.sql` inicializa as **Hypertables** necessárias para a performance de séries temporais.
3. **Healthchecks de Rede:** A API aguarda a prontidão do banco de dados (`pg_isready`) antes de iniciar o contexto do Spring.

## 🚀 Como instalar e rodar

### Opção 1: Docker Compose (Recomendado)
Para subir a stack completa (API + Banco + IA + Front):
```bash
docker compose up -d --build
```

### Opção 2: Bootstrapping Manual (Desenvolvedor)
Após subir o banco via Docker, realize a carga inicial de dados via Swagger (`http://localhost:8080/swagger-ui.html`):

1. **Mapeamento:** `POST /api/admin/ingestion/repair-stations` (Vincula sensores por proximidade).
2. **Histórico:** `POST /api/admin/ingestion/historical-full-sync?years=5` (Sincroniza 5 anos de dados).
3. **Ocorrências:** `POST /api/admin/ingestion/historical-civil-defense` (Importa dados da Defesa Civil).

## 📄 Licença
Este projeto está sob a licença **MIT**.
