package com.projeto.mapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O application.yml de teste sombreia o de produção, então nada nos outros testes exercita os
 * timeouts reais. Este lê o application.yml principal (src/main/resources) direto do disco.
 */
class HttpTimeoutPropertiesYamlTest {

    @Test
    void timeoutsDoApplicationYmlDeProducaoSaoCarregados() throws Exception {
        Resource main = new org.springframework.core.io.FileSystemResource("src/main/resources/application.yml");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("main", main);
        StandardEnvironment env = new StandardEnvironment();
        sources.forEach(env.getPropertySources()::addLast);

        HttpTimeoutProperties props = Binder.get(env).bind("app.http", HttpTimeoutProperties.class).get();

        assertEquals(Duration.ofSeconds(5), props.connectTimeout("apac"));
        assertEquals(Duration.ofSeconds(20), props.readTimeout("apac"));
        assertEquals(Duration.ofSeconds(2), props.connectTimeout("ai"));
        assertEquals(Duration.ofSeconds(8), props.readTimeout("ai"));
        assertEquals(Duration.ofSeconds(15), props.readTimeout("open-meteo")); // herda o padrão
        for (String source : List.of("apac", "apac-historical", "ana", "ana-historical", "tabua-mare",
                "open-meteo-archive", "nominatim", "ckan", "ai")) {
            assertEquals(true, props.getClients().containsKey(source), "sem entrada para " + source);
        }
    }
}
