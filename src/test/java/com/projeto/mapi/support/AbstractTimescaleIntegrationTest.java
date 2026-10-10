package com.projeto.mapi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integração contra um TimescaleDB real (ADR 0004). Sobe UM contêiner por JVM
 * (singleton estático, reaproveitado por todas as subclasses; o Ryuk o remove ao fim), aplica as
 * migrations Flyway reais e deixa o Hibernate em ddl-auto=validate.
 *
 * A imagem vem de UMA só fonte: a propriedade de sistema/env {@code mapi.test.db-image}
 * (ex.: -Dmapi.test.db-image=postgis/postgis:16-3.4 ou MAPI_TEST_DB_IMAGE), com padrão em
 * {@link #DEFAULT_DB_IMAGE}. Trocar de banco (ex.: imagem com PostGIS) é mudar só esse valor.
 */
@SpringBootTest
@ActiveProfiles({"test", "testcontainers"})
public abstract class AbstractTimescaleIntegrationTest {

    /** Tag fixa (não "latest") para o teste ser reproduzível; mesma família pg16 do docker-compose. */
    public static final String DEFAULT_DB_IMAGE = "timescale/timescaledb:2.17.2-pg16";

    private static final String IMAGE = resolveImage();

    protected static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tide_db")
            .withUsername("mapi_user")
            .withPassword("mapi123");

    static {
        DB.start();
    }

    private static String resolveImage() {
        String fromProp = System.getProperty("mapi.test.db-image");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        String fromEnv = System.getenv("MAPI_TEST_DB_IMAGE");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        return DEFAULT_DB_IMAGE;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
