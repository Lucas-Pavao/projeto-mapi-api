# Estágio 1: Build da aplicação com Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar apenas o POM e baixar dependências (melhora o cache do Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar o código fonte e gerar o JAR
COPY src ./src

# Rastreabilidade (RC-03): o .git não entra no build (.dockerignore), então o commit chega por build-arg.
# Sem o arg o fallback é explícito (aparece em /actuator/info e no manifesto do export), nunca silencioso.
# Uso: GIT_COMMIT=$(git rev-parse HEAD) GIT_DIRTY=$(test -n "$(git status --porcelain)" && echo true || echo false) docker compose build
ARG GIT_COMMIT=unknown-build-without-git
ARG GIT_DIRTY=unknown
ARG GIT_BRANCH=unknown
RUN printf 'git.commit.id=%s\ngit.commit.id.abbrev=%s\ngit.dirty=%s\ngit.branch=%s\ngit.build.time=%s\n' \
      "$GIT_COMMIT" "$(echo "$GIT_COMMIT" | cut -c1-7)" "$GIT_DIRTY" "$GIT_BRANCH" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      > src/main/resources/git.properties
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
