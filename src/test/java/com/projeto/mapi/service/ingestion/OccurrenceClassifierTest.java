package com.projeto.mapi.service.ingestion;

import com.projeto.mapi.service.ingestion.occurrence.LabelFilterConfig;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification.Status;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras puras do rótulo CKAN (ADR 0006 seções 4 e 6). */
class OccurrenceClassifierTest {

    private static final LabelFilterConfig FILTER = new LabelFilterConfig(1, "cobrade-alagamento", List.of(), List.of("alagamento"));
    private static final String TZ = "America/Recife";

    private static OccurrenceClassification run(String tipo, String data) {
        return OccurrenceClassifier.classify(Map.of("Ocorrencia", tipo, "Data", data, "Endereco", "Rua X, 10", "Bairro", "Boa Viagem"), FILTER, TZ);
    }

    @Test
    void alagamentosComDataSemHoraEhDiaCivilLocal() {
        OccurrenceClassification c = run("Alagamentos", "2024-05-28T00:00:00");
        assertThat(c.status()).isEqualTo(Status.ACCEPTED);
        assertThat(c.precision()).isEqualTo("DAY");
        assertThat(c.occurredFrom()).isEqualTo(Instant.parse("2024-05-28T03:00:00Z"));
        assertThat(c.occurredTo()).isEqualTo(Instant.parse("2024-05-29T03:00:00Z"));
    }

    @Test
    void dataBrComBarrasSemHoraTambemEhDay() {
        OccurrenceClassification c = run("alagamento", "10/10/2026");
        assertThat(c.precision()).isEqualTo("DAY");
        assertThat(c.occurredFrom()).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
    }

    @Test
    void comHoraEhInstanteHourEConverteDeRecifeParaUtc() {
        OccurrenceClassification c = run("Alagamentos", "10/10/2026 14:30:00");
        assertThat(c.precision()).isEqualTo("HOUR");
        assertThat(c.occurredFrom()).isEqualTo(Instant.parse("2026-10-10T17:30:00Z"));
        assertThat(c.occurredTo()).isAfter(c.occurredFrom()).isBefore(c.occurredFrom().plusSeconds(1));
    }

    @Test
    void outrosTiposFicamForaDoRotulo() {
        for (String tipo : List.of("Deslizamento de barreira", "Enxurrada", "Inundação", "Imóveis com Danos / Risco", "")) {
            assertThat(run(tipo, "2024-05-28T00:00:00").status()).as(tipo).isEqualTo(Status.EXCLUDED_BY_FILTER);
        }
    }

    @Test
    void termoExcluidoVenceTermoAceito() {
        assertThat(run("Alagamento por enxurrada", "2024-05-28T00:00:00").status()).isEqualTo(Status.EXCLUDED_BY_FILTER);
    }

    @Test
    void dataIlegivelVaiParaQuarentena() {
        assertThat(run("Alagamentos", "ontem").status()).isEqualTo(Status.TS_UNPARSEABLE);
    }
}
