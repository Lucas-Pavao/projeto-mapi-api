# Projeto MAPI API 🌊🚀

API Spring Boot desenvolvida para o **Projeto MAPI (Smart City)**, focada na **previsão de alagamentos e enchentes** na região metropolitana do Recife. O sistema integra dados hidrológicos (ANA/APAC), climáticos e marítimos (Marinha) para fornecer uma base sólida de inteligência urbana.

---

## 🏗️ Estrutura do Projeto

```text
projeto-mapi-api/
├── src/main/java/com/projeto/mapi/
│   ├── config/                     # Configurações de infraestrutura (MQTT, Swagger, Security)
│   ├── controller/                 # Camada de exposição REST (Endpoints de Maré e Auth)
│   ├── dto/                        # Objetos de Transferência de Dados (Requests/Responses)
│   ├── model/                      # Entidades JPA (TideTable, SensorData, User, RefreshToken)
│   ├── repository/                 # Camada de persistência (Spring Data JPA)
│   ├── security/                   # Segurança JWT, Filtros e Configurações
│   └── service/                    # Regras de Negócio e Serviços (PDF, Ingestão, Auth)
├── src/main/resources/
│   └── application.yml             # Configurações do framework Spring e JWT
├── .gitignore                      # Proteção contra envio de arquivos sensíveis (.env)
├── pom.xml                         # Gerenciamento de dependências Maven
└── README.md                       # Documentação técnica
```

---

## 🛠️ Guia de Instalação e Configuração

### 1. Pré-requisitos (Linux - Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-21-jdk postgresql postgresql-contrib mosquitto mosquitto-clients
```

### 2. Banco de Dados (PostgreSQL)
A API utiliza o banco `tide_db`. Configure o acesso:
```bash
sudo -u postgres psql -c "CREATE DATABASE tide_db;"
sudo -u postgres psql -c "CREATE USER mapi_user WITH PASSWORD 'mapi123';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE tide_db TO mapi_user;"
```

### 3. Variáveis de Ambiente (.env)
Crie um arquivo `.env` na raiz do projeto para carregar as configurações locais:
```env
# Banco de Dados
POSTGRES_URL=jdbc:postgresql://localhost:5432/tide_db
POSTGRES_USER=mapi_user
POSTGRES_PASSWORD=mapi123

# MQTT
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_TOPIC=sensors/tide/#

# Segurança (JWT)
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_REFRESH_EXPIRATION=2592000000  # 30 dias em ms
```

---

## 🔒 Sistema de Autenticação (JWT + Refresh Token)

O sistema de segurança foi aprimorado para suportar autenticação completa baseada em banco de dados.

### 1. Fluxo de Acesso
1.  **Registro**: Crie um usuário no sistema.
2.  **Login**: Obtenha seu `accessToken` (válido por 1 hora) e `refreshToken` (válido por 30 dias).
3.  **Uso**: Envie o `accessToken` no header `Authorization: Bearer <TOKEN>`.
4.  **Renovação**: Quando o access token expirar, use o refresh token para obter um novo par sem logar novamente.

### 2. Endpoints de Autenticação

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Cadastro de novos usuários (Role padrão: USER). |
| `POST` | `/api/auth/login` | Autenticação com username/password. |
| `POST` | `/api/auth/refresh` | Gera novos tokens usando um Refresh Token válido. |

---

## 📡 Endpoints da API (Protegidos)

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/api/tide/{harbor}` | Consulta tábua de maré por porto (ex: `recife`). |
| `POST` | `/api/tide/upload` | Upload e conversão de PDF da Marinha. |
| `POST` | `/api/tide/ingest/recife` | Dispara ingestão automática via Crawler. |

---

## 🚀 Como Executar

```bash
# Compilar e rodar
./mvnw spring-boot:run
```
*Acesse o Swagger em: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)*

---

## 📊 Integração IoT (MQTT)
A API escuta dados de sensores no tópico configurado. Para simular um envio:
```bash
mosquitto_pub -h localhost -t sensors/tide/data -m "sensor_recife,2.5,m"
```
