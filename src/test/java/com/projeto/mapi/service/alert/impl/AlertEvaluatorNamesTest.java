package com.projeto.mapi.service.alert.impl;

import com.projeto.mapi.dto.SensorResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Título dos alertas de estação com o nome, não só o código (revisão das telas de 21/11). */
class AlertEvaluatorNamesTest {

    private static SensorResponseDTO sensor(String id, String name) {
        SensorResponseDTO s = new SensorResponseDTO();
        s.setSensorId(id);
        s.setStationName(name);
        return s;
    }

    @Test
    void tiraOPrefixoDaFonteDoNome() {
        assertThat(AlertEvaluatorImpl.cleanName("[CEMADEN] Pina")).isEqualTo("Pina");
        assertThat(AlertEvaluatorImpl.cleanName("Campina do Barreto")).isEqualTo("Campina do Barreto");
    }

    @Test
    void usaONomeDaEstacaoQuandoOFeedTem_senaoOCodigo() {
        List<SensorResponseDTO> feed = List.of(sensor("APAC-PLUVIO-261160617A", "[CEMADEN] Pina"));
        assertThat(AlertEvaluatorImpl.stationLabel(feed, "APAC-PLUVIO-261160617A")).isEqualTo("Pina");
        assertThat(AlertEvaluatorImpl.stationLabel(feed, "XYZ")).isEqualTo("pluviômetro XYZ");
    }
}
