# MAPI API - Núcleo Central e Orquestração 🌊🚀

A **MAPI API** é o núcleo analítico e operacional do ecossistema de resiliência urbana da Região Metropolitana do Recife (RMR). Ela atua como o **orquestrador central**, integrando fontes telemétricas, previsões climáticas e dados oceanográficos para alimentar o motor de IA e fornecer inteligência para a prevenção de alagamentos.

## 🌐 Ecossistema MAPI

Este projeto centraliza e gerencia o fluxo de dados do ecossistema:

```text
  [ Agências (ANA / APAC / CEMADEN) ] 📡
        │   (APIs REST/HTTP públicas de estações hidrometeorológicas reais)
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

> **Nota de arquitetura:** o antigo componente "MAPI Edge" (processo Python separado que buscava dados da ANA/APAC e os publicava via MQTT num broker público) foi descontinuado. A própria API agora busca esses dados diretamente, em jobs agendados (`SensorCollectionTask`), eliminando uma camada de infraestrutura e um ponto de falha externo. O repositório do MAPI Edge continua existindo como referência histórica, mas não faz mais parte do caminho crítico do sistema.

### Componentes do Ecossistema:
*   🌊 **[MAPI API (Backend)](https://github.com/Lucas-Pavao/projeto-mapi-api):** Orquestrador central — coleta os dados de sensores diretamente das agências e persiste na base temporal.
*   🧠 **[MAPI AI (Inteligência)](https://github.com/Lucas-Pavao/projeto-mapi-ai):** Microserviço de inferência para predição de riscos.
*   💻 **[MAPI Front (Dashboard)](https://github.com/Lucas-Pavao/projeto-mapi-front):** Interface geoespacial para monitoramento em tempo real.

### Conexões Estruturais:
- **Entrada (Ingestão):** Jobs agendados internos (`SensorCollectionTask`) buscam periodicamente dados das APIs de agências (ANA, APAC, CEMADEN) e os processam pelo mesmo pipeline de negócio de sempre.
- **Processamento Síncrono:** Dispara requisições HTTP POST síncronas para o MAPI AI para receber métricas preditivas de risco de alagamento.
- **Saída (Exposição):** Persiste dados no banco temporal (TimescaleDB) e expõe endpoints REST protegidos por JWT para o MAPI Front.

## 🛠️ Tecnologias Escolhidas

| Categoria | Tecnologia | Justificativa Técnica |
| :--- | :--- | :--- |
| **Linguagem** | Java 21 (LTS) | Virtual Threads para alta concorrência e Records para imutabilidade. |
| **Framework** | Spring Boot 3.4.0 | Injeção de dependências robusta e gerenciamento de tarefas agendadas. |
| **Banco de Dados** | PostgreSQL 16 + TimescaleDB | Extensões de séries temporais (Hypertables) para indexação analítica. |
| **Agendamento** | Spring `@Scheduled` + Resilience4j | Coleta periódica direta das APIs de sensores (ANA/APAC), com retry e circuit breaker. |
| **Segurança** | Spring Security + JWT | Controle estrito de acesso e ciclo de vida de tokens. |
| **Documentação** | OpenAPI 3.0 (Swagger) | Contrato claro de endpoints para integração facilitada. |
| **Observabilidade** | Prometheus + Grafana | Métricas via Actuator/Micrometer, dashboards de HTTP, JVM, filas e resiliência. |
| **Teste de Carga** | k6 (com output remote-write) | Simula tráfego real e envia métricas ao vivo direto para o Prometheus/Grafana. |

## 🏗️ Arquitetura e Especialização

O desenvolvimento é orientado por agentes especializados para garantir a integridade dos domínios:

- 🌊 **TideExpert:** Domínio analítico de marés astronômicas (Porto do Recife/Marinha).
- 📡 **IoTMaster:** Coleta agendada de dados telemétricos (ANA/APAC) e tratamento de fuso horário UTC-3.
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
├── observability/               # Stack de monitoramento (Prometheus + Grafana)
│   ├── prometheus/prometheus.yml        # Scrape config (alvo: mapi-api:9404/actuator/prometheus)
│   └── grafana/
│       ├── provisioning/                # Datasource e auto-load de dashboards
│       └── dashboards/                  # 5 dashboards prontos (HTTP, JVM, filas, coletores, k6)
├── loadtest/
│   └── stress-test.js           # Script k6 de teste de carga (ramping-vus)
└── src/
    ├── main/
    │   ├── java/com/projeto/mapi/
    │   │   ├── config/          # Beans de Configuração (Security, Scheduling, AppProperties)
    │   │   ├── controller/      # Camada REST (Endpoints Públicos e Administrativos)
    │   │   ├── dto/             # Data Transfer Objects (Imutabilidade)
    │   │   ├── exception/       # Handlers globais de erro
    │   │   ├── mapper/          # Conversores de Entidade/DTO
    │   │   ├── model/           # Entidades JPA (Mapeamento TimescaleDB)
    │   │   ├── repository/      # Interfaces Spring Data (JPA/Timescale)
    │   │   ├── security/        # Lógica de Filtros e JWT
    │   │   ├── service/         # Subpacotes por domínio, cada um com sua própria impl/:
    │   │   │   ├── sensor/      #   SensorService + coletores ana/ e apac/
    │   │   │   ├── tide/        #   TideService, TabuaMareService
    │   │   │   ├── weather/     #   WeatherService, MarineService
    │   │   │   ├── flood/       #   FloodEvent/FloodPrediction/CivilDefense
    │   │   │   ├── export/      #   DataExportService
    │   │   │   ├── auth/        #   Authentication, RefreshToken
    │   │   │   └── geocoding/   #   GeocodingService (Nominatim)
    │   │   └── util/            # Helpers (GeoUtils, RmrFilter, SensorValueExtractor)
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
└── projeto-mapi-front/ <-- (Repositório do Frontend)
```
> **Nota:** O antigo repositório de sensores (Edge/MQTT) não é mais necessário — a API coleta os dados diretamente. Para rodar apenas a API e o Banco, comente as seções `mapi-ai` e `mapi-front` no `docker-compose.yml` usando `#`.

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
- **Prometheus:** [http://localhost:9090](http://localhost:9090)
- **Grafana:** [http://localhost:3001](http://localhost:3001) (login padrão: `admin` / `mapi123`, apenas para uso local)

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

## 📊 Observabilidade (Prometheus + Grafana)

A stack sobe automaticamente com o `docker compose up` (junto com a API e o banco) e já vem com dashboards provisionados:

- 🔥 **Prometheus** ([localhost:9090](http://localhost:9090)): faz *scrape* do endpoint `/actuator/prometheus` da mapi-api a cada 15s (exposto internamente na porta `9404` via Micrometer). Restrito a `127.0.0.1` — não tem autenticação própria.
- 📈 **Grafana** ([localhost:3001](http://localhost:3001)): datasource e dashboards já provisionados via arquivos em `observability/grafana/provisioning` e `observability/grafana/dashboards`, sem nenhum setup manual:
  1. **MAPI - Teoria das Filas** — utilização, tempo de espera e throughput do pool de threads (`taskExecutor`).
  2. **MAPI - Visão Geral HTTP** — taxa de requisições, latência e erros por endpoint.
  3. **MAPI - JVM e Recursos** — heap, GC, threads e uso de CPU da aplicação.
  4. **MAPI - Coletores e Resiliência** — métricas dos coletores ANA/APAC e estado dos circuit breakers (Resilience4j).
  5. **MAPI - Teste de Carga (k6)** — métricas ao vivo do teste de carga descrito abaixo.

### Teste de Carga (k6)

O serviço `k6` não sobe com o stack padrão — ele roda sob demanda, sob o profile `loadtest`, e envia as métricas via remote-write direto para o Prometheus (visíveis ao vivo no dashboard "MAPI - Teste de Carga"):

```bash
docker compose --profile loadtest run --rm k6 run /scripts/stress-test.js
```

O script (`loadtest/stress-test.js`) simula carga crescente (*ramping-vus*: 0 → 30 usuários virtuais) contra endpoints reais da API, reusando um pool fixo de coordenadas para aproveitar o cache de clima/maré.

## 🚀 Melhorias Arquiteturais Implementadas

Para aumentar a robustez do orquestrador do ecossistema, as seguintes soluções foram incorporadas:
* **Coleta de Sensores Assíncrona:** Os jobs agendados de `SensorCollectionTask` (ANA/APAC) delegam o processamento pesado de telemetria a um pool de threads dedicado (`taskExecutor`), liberando a execução do agendador e eliminando riscos de perda de dados por lentidão de persistência.
* **Cache Inteligente de Pontos Críticos:** Implementação de caching automático com `@Cacheable` e `@CacheEvict` do Spring Framework para evitar sobrecarga de consultas no Postgres e assegurar atualização instantânea sob novos cadastros.
* **Integridade JPA & TimescaleDB:** Ajuste do mapeamento JPA de chaves compostas (`id` + `timestamp`) para alinhar com o comportamento estrutural e de particionamento das *Hypertables* do banco temporal.
* **Auditoria de Predições:** Gravação automática de logs de inferência no banco de dados (`flood_predictions`), servindo como histórico operacional e garantindo rastreabilidade de alertas.

### ⚠️ Solução de Problemas Comuns
1. **"Porta 8080 já está em uso":** Outro programa está usando a porta. Feche-o ou altere a porta no `docker-compose.yml`.
2. **Erro ao compilar Java:** Certifique-se de que o código compila localmente antes de rodar no Docker.
3. **Banco de Dados Vazio:** O script `TimescaleSetup.sql` roda apenas na primeira criação. Use `docker compose down -v` para forçar a recriação se necessário.

## 📄 Licença
Este projeto está sob a licença **MIT**.
