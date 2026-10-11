package com.projeto.mapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** RC-03: codeVersion vem do arquivo de build com o commit; o fallback é explícito. */
class CodeVersionTest {

    @Test
    void semArquivoDeBuildUsaFallbackExplicito() {
        assertThat(new IngestionProperties().getCodeVersion()).isEqualTo("unknown-build-without-git");
    }

    @Test
    void usaCommitCompletoEMarcaSujo() {
        IngestionProperties props = new IngestionProperties();
        props.injectGitProperties(build("abc123full", "false"));
        assertThat(props.getCodeVersion()).isEqualTo("abc123full");
        props.injectGitProperties(build("abc123full", "true"));
        assertThat(props.getCodeVersion()).isEqualTo("abc123full-dirty");
    }

    @Test
    void overrideExplicitoTemPrioridade() {
        IngestionProperties props = new IngestionProperties();
        props.injectGitProperties(build("abc", "false"));
        props.setCodeVersion("manual-1");
        assertThat(props.getCodeVersion()).isEqualTo("manual-1");
    }

    /** O arquivo gerado pelo plugin no build (ou pelo Dockerfile) chega ao classpath e não resulta em "unknown". */
    @Test
    void arquivoDeBuildNaoDeixaCodeVersionUnknown() throws IOException {
        Properties p = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            assertThat(in).as("git.properties deve existir em target/classes").isNotNull();
            p.load(in);
        }
        // O Spring Boot remove o prefixo "git." ao criar o bean GitProperties.
        Properties semPrefixo = new Properties();
        p.stringPropertyNames().stream().filter(k -> k.startsWith("git."))
                .forEach(k -> semPrefixo.setProperty(k.substring(4), p.getProperty(k)));
        IngestionProperties props = new IngestionProperties();
        props.injectGitProperties(new GitProperties(semPrefixo));
        assertThat(props.getCodeVersion()).isNotBlank().doesNotStartWith("unknown");
    }

    private static GitProperties build(String id, String dirty) {
        Properties p = new Properties();
        p.setProperty("commit.id", id);
        p.setProperty("dirty", dirty);
        return new GitProperties(p);
    }
}
