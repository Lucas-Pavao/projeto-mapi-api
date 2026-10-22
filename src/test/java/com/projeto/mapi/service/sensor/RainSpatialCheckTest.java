package com.projeto.mapi.service.sensor;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyRainRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainSpatialCheckTest {

    private static final Instant H = Instant.parse("2026-10-21T16:00:00Z");

    private static HourlyRainRow row(String id, double mm, double lat, double lon) {
        return new HourlyRainRow("CEMADEN", id, H, mm, lat, lon);
    }

    @Test
    void picoIsoladoSemChuvaNosVizinhosSai() {
        // caso real: Bonsucesso 40,7 mm; Ouro Preto (1,4 km) e Campina do Barreto (3,3 km) com 0
        List<HourlyRainRow> rows = List.of(row("260960003A", 40.7, -8.008, -34.851),
                row("260960010A", 0.0, -8.0, -34.86), row("261160614A", 0.0, -8.03, -34.87));
        assertThat(RainSpatialCheck.dropIsolatedPeaks(rows)).extracting(HourlyRainRow::stationId)
                .containsExactly("260960010A", "261160614A");
    }

    @Test
    void chuvaForteConfirmadaPorUmVizinhoFica() {
        List<HourlyRainRow> rows = List.of(row("A", 40.0, -8.008, -34.851), row("B", 0.0, -8.0, -34.86),
                row("C", 12.0, -8.03, -34.87));
        assertThat(RainSpatialCheck.dropIsolatedPeaks(rows)).hasSize(3);
    }

    @Test
    void semVizinhosSuficientesNaoJulga() {
        List<HourlyRainRow> rows = List.of(row("A", 40.0, -8.008, -34.851), row("B", 0.0, -8.0, -34.86),
                // a 30 km: não é vizinha
                row("C", 0.0, -8.28, -34.95), new HourlyRainRow("CEMADEN", "D", H, 0.0, null, null));
        assertThat(RainSpatialCheck.dropIsolatedPeaks(rows)).hasSize(4);
    }

    @Test
    void vizinhoDeOutraHoraNaoConta() {
        List<HourlyRainRow> rows = List.of(row("A", 40.0, -8.008, -34.851),
                new HourlyRainRow("CEMADEN", "B", H.minusSeconds(3600), 0.0, -8.0, -34.86),
                new HourlyRainRow("CEMADEN", "C", H.minusSeconds(3600), 0.0, -8.03, -34.87));
        assertThat(RainSpatialCheck.dropIsolatedPeaks(rows)).hasSize(3);
    }
}
