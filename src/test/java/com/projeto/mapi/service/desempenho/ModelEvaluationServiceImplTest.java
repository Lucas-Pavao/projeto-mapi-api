package com.projeto.mapi.service.desempenho;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ModelEvaluationProperties;
import com.projeto.mapi.dto.ModelEvaluationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.desempenho.impl.ModelEvaluationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ModelEvaluationServiceImplTest {

    @TempDir Path dir;

    private ModelEvaluationServiceImpl service(Path file, String expected) {
        ModelEvaluationProperties p = new ModelEvaluationProperties();
        p.setPath(file.toString());
        p.setExpectedSha256(expected);
        return new ModelEvaluationServiceImpl(p, new ObjectMapper());
    }

    @Test
    void hashConfere_devolveTextoLiteralSemReformatarNumeros() throws Exception {
        String json = "{\"auc\": 0.5, \"p\": 1e-05, \"x\": [1.50, 2]}";
        Path f = Files.writeString(dir.resolve("r.json"), json);
        ModelEvaluationDTO dto = service(f, Hashing.sha256(json.getBytes(StandardCharsets.UTF_8))).read();
        assertThat(dto.result()).isEqualTo(json);
        assertThat(dto.sha256()).isEqualTo(Hashing.sha256(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void hashDivergente_503() throws Exception {
        Path f = Files.writeString(dir.resolve("r.json"), "{\"a\":1}");
        assertThatThrownBy(() -> service(f, "0".repeat(64)).read())
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("SHA-256");
    }

    @Test
    void arquivoAusente_503() {
        assertThatThrownBy(() -> service(dir.resolve("nao-existe.json"), "0".repeat(64)).read())
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void naoEhJson_503() throws Exception {
        String txt = "isto nao e json {";
        Path f = Files.writeString(dir.resolve("r.json"), txt);
        assertThatThrownBy(() -> service(f, Hashing.sha256(txt.getBytes(StandardCharsets.UTF_8))).read())
                .isInstanceOf(ServiceUnavailableException.class);
    }

    /** O hash padrão da propriedade é o registrado em HASHES.sha256 da IA; confere contra o arquivo real, se existir. */
    @Test
    void arquivoRealDaAvaliacaoBateComOHashPadrao() {
        Path real = Path.of("../../../../../avaliacao-f1/resultado_etapa2.json").toAbsolutePath().normalize();
        Path alt = Path.of("/home/lucas/Documents/Projetos/MAPI/avaliacao-f1/resultado_etapa2.json");
        Path f = Files.exists(alt) ? alt : real;
        assumeTrue(Files.exists(f), "avaliacao-f1 não está montado neste ambiente");
        ModelEvaluationProperties defaults = new ModelEvaluationProperties();
        assertThat(service(f, defaults.getExpectedSha256()).read().sha256()).isEqualTo(defaults.getExpectedSha256());
    }
}
