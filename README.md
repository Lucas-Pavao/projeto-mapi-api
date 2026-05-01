# Projeto MAPI API 🌊🚀

API Spring Boot desenvolvida para o **Projeto MAPI (Smart City)**, focada na **previsão de alagamentos e enchentes** na região metropolitana do Recife. O sistema integra dados hidrológicos (ANA/APAC), climáticos e marítimos (Marinha) para fornecer uma base sólida de inteligência urbana.

---

## 🏗️ Estrutura do Projeto

```text
projeto-mapi-api/
├── src/main/java/com/projeto/mapi/
│   ├── MapiApplication.java        # Classe principal com carregamento automático de .env
│   ├── config/                     # Configurações de infraestrutura (MQTT, Swagger)
│   ├── controller/                 # Camada de exposição REST (Endpoints)
│   ├── model/                      # Entidades JPA (TideTable, SensorData, etc.)
│   ├── repository/                 # Camada de persistência (Spring Data JPA)
│   ├── security/                   # Camada de segurança JWT
│   └── service/                    # Camada de Negócio (Interfaces e Impl)
├── src/main/resources/
│   └── application.yml             # Configurações do framework Spring
├── .env                            # Variáveis de ambiente e segredos
├── pom.xml                         # Dependências do Maven
└── README.md                       # Documentação do projeto
```

---

## 🛠️ Guia de Instalação e Configuração

Siga os passos abaixo para preparar o ambiente no Linux (Ubuntu/Debian):

### 1. Instalação das Dependências
Abra o terminal e instale os componentes necessários:
```bash
sudo apt update
sudo apt install openjdk-21-jdk postgresql postgresql-contrib mosquitto mosquitto-clients
```

### 2. Configuração do Banco de Dados
A API necessita de um banco chamado `tide_db` e um usuário com permissões. **Execute estes comandos para evitar erros de autenticação**:

```bash
# Iniciar o serviço do Postgres
sudo systemctl start postgresql

# Criar banco e usuário (Copie e cole os comandos abaixo)
sudo -u postgres psql -c "CREATE DATABASE tide_db;"
sudo -u postgres psql -c "CREATE USER mapi_user WITH PASSWORD 'mapi123';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE tide_db TO mapi_user;"
```

### 3. Configuração do Arquivo `.env`
Crie um arquivo chamado `.env` na raiz do projeto:
```env
# Banco de Dados
POSTGRES_URL=jdbc:postgresql://localhost:5432/tide_db
POSTGRES_USER=mapi_user
POSTGRES_PASSWORD=mapi123

# MQTT
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_CLIENT_ID=mapi-api-inbound
MQTT_TOPIC=sensors/tide/#

# Segurança
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
```

---

## 🚀 Como Executar e Testar

### 1. Iniciar a API
Agora o projeto carrega o `.env` automaticamente. Basta rodar:
```bash
./mvnw spring-boot:run
```
*A API estará disponível em: `http://localhost:8080`*

### 2. Acessar Swagger (Interface Gráfica)
Para testar os endpoints sem usar o terminal:
🔗 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

---

## 🔒 Autenticação (JWT)

A API é protegida. Siga estes passos para obter acesso:

1.  **Obter o Token (Login)**:
    Envie um POST para `/api/auth/login` com as credenciais padrão:
    - **User**: `admin`
    - **Password**: `mapi123`
    
    Exemplo via `curl`:
    ```bash
    curl -X POST http://localhost:8080/api/auth/login \
         -H "Content-Type: application/json" \
         -d '{"username": "admin", "password": "mapi123"}'
    ```

2.  **Usar o Token**:
    Copie o token recebido e use-o no header de todas as próximas requisições:
    `Authorization: Bearer <SEU_TOKEN_AQUI>`

---

## 📡 Endpoints de Controle

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Gera o token de acesso. |
| `GET` | `/api/tide/{porto}` | Busca previsões de maré no banco. |
| `POST` | `/api/tide/ingest/recife` | Aciona o robô que busca dados na Marinha. |

---

## 📊 Simulação IoT (MQTT)
```bash
mosquitto_pub -h localhost -t sensors/tide/data -m "sensor_apac_recife,3.15,m"
```
