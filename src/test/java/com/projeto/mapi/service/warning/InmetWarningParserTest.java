package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.warning.InmetWarningParser.ParseResult;
import com.projeto.mapi.service.warning.InmetWarningParser.ParsedWarning;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InmetWarningParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/inmet/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parseRealSnapshot_converteBrasiliaParaUtcEDeduplicaHojeFuturo() throws Exception {
        ParseResult res = InmetWarningParser.parse(fixture("avisos-ativos-real.json"), mapper);

        // 3 em hoje + 2 em futuro, com o aviso 28713 repetido nos dois arrays -> 4 únicos
        assertThat(res.totalRawItems()).isEqualTo(5);
        assertThat(res.warnings()).hasSize(4);

        ParsedWarning primeiro = res.warnings().get(0);
        assertThat(primeiro.warningId()).isEqualTo(28711);
        assertThat(primeiro.sequenceId()).isEqualTo(1);
        assertThat(primeiro.severity()).isEqualTo("Perigo Potencial");
        assertThat(primeiro.colorHex()).isEqualTo("#FFFE00");
        // 2026-11-16 08:55 America/Sao_Paulo (UTC-3) == 2026-11-16T11:55:00Z
        assertThat(primeiro.validFrom()).isEqualTo(Instant.parse("2026-11-16T11:55:00Z"));
        // 2026-11-16 23:59 America/Sao_Paulo (UTC-3) == 2026-11-17T02:59:00Z
        assertThat(primeiro.validTo()).isEqualTo(Instant.parse("2026-11-17T02:59:00Z"));
        assertThat(primeiro.risks()).isNotEmpty();
        assertThat(primeiro.instructions()).isNotEmpty();
        assertThat(primeiro.rawPayload().has("icone")).isFalse();
        assertThat(primeiro.rawHash()).hasSize(64);

        // Na amostra real de 2026-11-16 nenhum aviso tocou Recife/Jaboatão/Olinda
        Set<String> rmr = Set.of("2611606", "2607901", "2609600");
        assertThat(res.warnings().stream().filter(w -> w.touchesAny(rmr))).isEmpty();
    }

    @Test
    void parseRecifeFixture_filtraMunicipiosDaRmrEResolveIbge() throws Exception {
        ParseResult res = InmetWarningParser.parse(fixture("avisos-ativos-recife.json"), mapper);
        assertThat(res.warnings()).hasSize(2);

        Set<String> rmr = Set.of("2611606", "2607901", "2609600");
        var matched = res.warnings().stream().filter(w -> w.touchesAny(rmr)).toList();
        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).geocodes()).containsExactly("2611606", "2607901", "2609600");

        assertThat(InmetWarningParser.resolveIbge("Recife")).contains("2611606");
        assertThat(InmetWarningParser.resolveIbge("Jaboatão dos Guararapes")).contains("2607901");
        assertThat(InmetWarningParser.resolveIbge("2609600")).contains("2609600");
        assertThat(InmetWarningParser.resolveIbge("Petrolina")).isEmpty();
    }

    @Test
    void jsonInvalidoOuSemBuckets_lancaIllegalArgument() {
        assertThatThrownBy(() -> InmetWarningParser.parse("{", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InmetWarningParser.parse("{\"foo\": []}", mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
