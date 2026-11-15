package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClusterer.Occurrence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OccurrenceClustererTest {

    private static Occurrence o(long id, String src, String from, Double lat, Double lon, String tp, String sp, String conf) {
        return new Occurrence(id, src, "ALAGAMENTO", Instant.parse(from), lat, lon, tp, sp, conf);
    }

    @Test
    void mesmoDiaMesmoLugarDeFontesDiferentesViraUmComAMelhorPrecisao() {
        var a = o(1, "CKAN_RECIFE", "2022-05-28T03:00:00Z", -8.0500, -34.9000, "DAY", "LOGRADOURO", "MEDIA");
        var b = o(2, "DEFESA_CIVIL_LAI", "2022-05-28T14:30:00Z", -8.0505, -34.9005, "MINUTE", "COORDENADA", "ALTA");
        var c = o(3, "CKAN_RECIFE", "2022-05-28T03:00:00Z", -8.0600, -34.9000, "DAY", "LOGRADOURO", "MEDIA"); // ~1,1 km
        var cl = OccurrenceClusterer.cluster(List.of(a, b, c), 200);
        assertThat(cl).hasSize(2);
        var merged = cl.stream().filter(x -> x.memberIds().size() == 2).findFirst().orElseThrow();
        assertThat(merged.memberIds()).containsExactly(1L, 2L);
        assertThat(merged.temporalPrecision()).isEqualTo("MINUTE");
        assertThat(merged.spatialPrecision()).isEqualTo("COORDENADA");
        assertThat(merged.confidence()).isEqualTo("ALTA");
        assertThat(merged.lat()).isEqualTo(-8.0505);
        assertThat(merged.sources()).containsExactly("CKAN_RECIFE", "DEFESA_CIVIL_LAI");
        assertThat(merged.localDay()).hasToString("2022-05-28");
    }

    @Test
    void diaLocalDeRecifeSeparaEventosDaMeiaNoite() {
        // 2022-05-29T02:00Z = 28/05 23:00 em Recife; 2022-05-29T03:30Z = 29/05 00:30
        var a = o(1, "CKAN_RECIFE", "2022-05-29T02:00:00Z", -8.05, -34.90, "HOUR", "LOGRADOURO", "ALTA");
        var b = o(2, "BOMBEIROS_PE", "2022-05-29T03:30:00Z", -8.05, -34.90, "HOUR", "LOGRADOURO", "ALTA");
        assertThat(OccurrenceClusterer.cluster(List.of(a, b), 200)).hasSize(2);
    }

    @Test
    void semCoordenadaFicaSozinhoELigacaoSimplesEncadeia() {
        var a = o(1, "CKAN_RECIFE", "2022-05-28T12:00:00Z", -8.0500, -34.9000, "DAY", "LOGRADOURO", "MEDIA");
        var b = o(2, "CKAN_RECIFE", "2022-05-28T12:00:00Z", -8.0515, -34.9000, "DAY", "LOGRADOURO", "MEDIA"); // 167 m de a
        var c = o(3, "CKAN_RECIFE", "2022-05-28T12:00:00Z", -8.0530, -34.9000, "DAY", "LOGRADOURO", "MEDIA"); // 167 m de b
        var d = o(4, "IMPRENSA", "2022-05-28T12:00:00Z", null, null, "DAY", null, "BAIXA");
        var cl = OccurrenceClusterer.cluster(List.of(a, b, c, d), 200);
        assertThat(cl).hasSize(2);
        assertThat(cl.get(0).memberIds()).containsExactly(1L, 2L, 3L);
        assertThat(cl.get(1).memberIds()).containsExactly(4L);
    }
}
