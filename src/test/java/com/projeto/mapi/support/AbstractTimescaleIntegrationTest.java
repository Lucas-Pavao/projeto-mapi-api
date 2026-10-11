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

    /**
     * Mesma imagem fixa do docker-compose (ADR 0005): PG 16.15 + TimescaleDB 2.30.2 + PostGIS 3.6.4.
     * Precisa ter PostGIS para a migração V7 rodar nos testes. É grande (4,38 GB): no CI, cachear a imagem.
     */
    public static final String DEFAULT_DB_IMAGE = "timescale/timescaledb-ha:pg16.15-ts2.30.2";

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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate baseJdbc;

    /**
     * Apaga todas as linhas que referenciam flood_points (descobertas no catálogo, não por lista fixa),
     * para que um teste que limpa pontos não dependa da ordem de execução: os testes compartilham o
     * mesmo container e outras suítes (CKAN, terreno, predições) deixam linhas filhas.
     */
    protected void limparDependentesDePontos() {
        java.util.List<String> filhas = baseJdbc.queryForList(
                "SELECT DISTINCT conrelid::regclass::text FROM pg_constraint "
                        + "WHERE contype = 'f' AND confrelid = 'flood_points'::regclass", String.class);
        for (String tabela : filhas) {
            baseJdbc.execute("DELETE FROM " + tabela);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
