# Projeto MAPI - API 🌊🚀

A **MAPI API** é o núcleo de processamento e inteligência urbana do Projeto MAPI (Monitoramento de Águas e Pluviometria Inteligente). Desenvolvida com **Spring Boot 3**, esta API é responsável por centralizar dados hidrológicos, climáticos e marítimos para auxiliar na previsão de alagamentos e enchentes na Região Metropolitana do Recife.

## 📋 O que é o projeto?

O projeto faz parte de uma solução de **Smart City** que integra dados de fontes governamentais (ANA, APAC e Marinha do Brasil) com dados de sensores IoT distribuídos pela cidade. A API atua como o backend central, fornecendo persistência de dados, lógica de análise e endpoints para o dashboard frontend.

## 🏗️ Arquitetura

A API segue uma arquitetura **Multicamadas (Layered Architecture)**, garantindo separação de responsabilidades e facilidade de manutenção:

1.  **Camada de Controladores (REST API):** Endpoints para consumo do frontend e integração externa.
2.  **Camada de Serviços (Negócio):** Onde reside a inteligência do sistema, incluindo scrapers de maré, ingestores de dados e lógica de autenticação.
3.  **Camada de Repositórios (Persistência):** Interface com o banco de dados PostgreSQL via Spring Data JPA.
4.  **Integração IoT (MQTT):** Um módulo especializado que escuta tópicos MQTT para processar dados vindos da camada de Fog Computing (projeto-mapi Python).
5.  **Segurança:** Implementação de JWT (JSON Web Token) com Refresh Tokens para controle de acesso robusto.

## 📂 Estrutura do Projeto

```text
src/main/java/com/projeto/mapi/
├── config/                     # Configurações globais (MQTT, Security, Swagger)
├── controller/                 # Controladores REST (Auth, Sensores, Marés, Clima)
├── dto/                        # Objetos de transferência (Requests e Responses)
├── exception/                  # Tratamento global de erros e exceções customizadas
├── mapper/                     # Conversores entre Entidades e DTOs
├── model/                      # Entidades de domínio mapeadas para o banco
├── repository/                 # Interfaces de acesso ao banco (JPA)
├── security/                   # Filtros e lógica de autenticação JWT
└── service/                    # Regras de negócio e integração com APIs/Scrapers
```

## ⚙️ Como o projeto funciona?

1.  **Coleta de Dados de Maré:** A API possui um serviço que realiza scraping do site da Marinha ou processa PDFs oficiais para extrair a tábua de marés do Porto do Recife e arredores.
2.  **Processamento de Sensores:** Através do broker MQTT, a API recebe em tempo real dados de sensores virtuais (chuva e nível de rio) processados pela camada Python.
3.  **Autenticação:** Usuários se registram e autenticam via JWT. O sistema suporta Refresh Tokens para manter sessões seguras e duradouras.
4.  **Exposição de Dados:** O frontend consome os endpoints REST para exibir mapas, gráficos de sensores e alertas de maré alta.

## 🚀 Tecnologias Utilizadas

- **Java 21**
- **Spring Boot 3.3.x**
- **PostgreSQL** (Banco de Dados Relacional)
- **Eclipse Paho** (Cliente MQTT)
- **Spring Security + JWT**
- **Playwright** (Para scraping dinâmico)
- **Lombok** (Produtividade)
- **SpringDoc OpenAPI** (Swagger/Documentação)

---
**Desenvolvido para resiliência urbana e segurança da população.**
