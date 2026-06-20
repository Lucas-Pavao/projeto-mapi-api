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

### Componentes do Ecossistema:
*   📡 **[MAPI Edge (Sensores)](https://github.com/Lucas-Pavao/projeto-mapi-sensores):** Produtor de dados primários e inteligência de borda.
*   🌊 **[MAPI API (Backend)](https://github.com/Lucas-Pavao/projeto-mapi-api):** Orquestrador central, ingestão MQTT e persistência temporal.
*   🧠 **[MAPI AI (Inteligência)](https://github.com/Lucas-Pavao/projeto-mapi-ai):** Microserviço de inferência para predição de riscos.
*   💻 **[MAPI Front (Dashboard)](https://github.com/Lucas-Pavao/projeto-mapi-front):** Interface geoespacial para monitoramento em tempo real.

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
├── docker-compose.yml           # Orquestração da Stack (API + DB + AI + Front)
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

## 🚀 Como instalar e rodar (Guia Passo a Passo)

### Opção 1: Via Docker (Tutorial para Iniciantes) 🐳

Este método sobe toda a stack do MAPI de forma automatizada.

#### Passo 1: Instalação do Docker
1. **Windows e Mac:** Baixe e instale o [Docker Desktop](https://www.docker.com/products/docker-desktop/).
   - *Dica no Windows:* Durante a instalação, aceite o uso do "WSL 2". Após instalar, reinicie o computador.
2. **Linux:** Siga as instruções oficiais para sua distribuição (ex: `sudo apt install docker.io docker-compose-v2`).
3. **Verificação:** Abra o terminal e digite:
   ```bash
   docker --version
   docker compose version
   ```
   Se as versões aparecerem, o Docker está pronto!

#### Passo 2: Preparando as Pastas
O ecossistema MAPI exige que os repositórios estejam em uma pasta comum:
```text
MinhaPastaMapi/
├── projeto-mapi-api/   <-- (Este repositório)
├── projeto-mapi-ai/    <-- (Repositório da IA)
├── projeto-mapi-sensores/ <-- (Repositório do Edge/Sensores)
└── projeto-mapi-front/ <-- (Repositório do Frontend)
```
> **Nota:** Para rodar apenas a API e o Banco, comente as seções `mapi-ai` e `mapi-front` no `docker-compose.yml` usando `#`.

#### Passo 3: Rodando o Projeto
1. Abra o terminal na pasta `projeto-mapi-api`.
2. Execute o comando:
   ```bash
   docker compose up -d --build
   ```
   - `up`: Sobe os serviços.
   - `-d`: Roda em segundo plano.
   - `--build`: Garante que o código novo seja compilado na imagem.

#### Passo 4: Verificando se deu certo
Execute `docker ps` para ver os containers ativos: `mapi-api`, `mapi-db`, `mapi-ai` e `mapi-front`.

**Acessando as ferramentas:**
- **Dashboard (Front):** [http://localhost:3000](http://localhost:3000)
- **Documentação API (Swagger):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Banco de Dados:** Porta `5433` da sua máquina local.

#### Passo 5: Como ver os logs
Se algo não funcionar, verifique as mensagens do sistema:
```bash
docker logs -f mapi-api
```
*(Use `Ctrl + C` para sair).*

#### Passo 6: Comandos Úteis
- **Parar:** `docker compose stop`
- **Ligar:** `docker compose start`
- **Remover tudo:** `docker compose down`
- **Limpeza Profunda (Apagar Banco):** `docker compose down -v`

---

### Opção 2: Bootstrapping Manual (Desenvolvedor) 💻
Após subir a stack, é necessário realizar a carga inicial de dados via Swagger:

1. **Mapeamento:** `POST /api/admin/ingestion/repair-stations` (Vincula sensores por proximidade).
2. **Histórico:** `POST /api/admin/ingestion/historical-full-sync?years=5` (Sincroniza 5 anos de dados).
3. **Ocorrências:** `POST /api/admin/ingestion/historical-civil-defense` (Importa dados da Defesa Civil).
4. **Registro de Rótulos de Cenários:** `POST /api/pontos/scenarios` (Registra observações de cenários de alagamentos reais ou simulados unificando telemetria de sensores, clima e marés para gerar dados de treino de alta fidelidade para a IA).

## 🚀 Melhorias Arquiteturais Implementadas

Para aumentar a robustez do orquestrador do ecossistema, as seguintes soluções foram incorporadas:
* **Ingestão MQTT Assíncrona:** A escuta do Broker MQTT delega o processamento pesado de telemetria a um pool de threads dedicado (`taskExecutor`), liberando a thread principal e eliminando riscos de perda de pacotes.
* **Cache Inteligente de Pontos Críticos:** Implementação de caching automático com `@Cacheable` e `@CacheEvict` do Spring Framework para evitar sobrecarga de consultas no Postgres e assegurar atualização instantânea sob novos cadastros.
* **Integridade JPA & TimescaleDB:** Ajuste do mapeamento JPA de chaves compostas (`id` + `timestamp`) para alinhar com o comportamento estrutural e de particionamento das *Hypertables* do banco temporal.
* **Auditoria de Predições:** Gravação automática de logs de inferência no banco de dados (`flood_predictions`), servindo como histórico operacional e garantindo rastreabilidade de alertas.

### ⚠️ Solução de Problemas Comuns
1. **"Porta 8080 já está em uso":** Outro programa está usando a porta. Feche-o ou altere a porta no `docker-compose.yml`.
2. **Erro ao compilar Java:** Certifique-se de que o código compila localmente antes de rodar no Docker.
3. **Banco de Dados Vazio:** O script `TimescaleSetup.sql` roda apenas na primeira criação. Use `docker compose down -v` para forçar a recriação se necessário.

## 📄 Licença
Este projeto está sob a licença **MIT**.
