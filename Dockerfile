# Estágio 1: Build da aplicação com Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar apenas o POM e baixar dependências (melhora o cache do Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar o código fonte e gerar o JAR
COPY src ./src
RUN mvn package -DskipTests

# Estágio 2: Ambiente de Execução leve
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copiar o JAR gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar

# Porta padrão do Spring Boot
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
