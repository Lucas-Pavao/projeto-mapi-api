package com.projeto.mapi.integration;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Export do dataset (base do treino da IA) com dados semeados num TimescaleDB real: confere o
 * cabeçalho do CSV, o número de linhas (uma por hora da janela) e o rótulo isFlooded.
 */
class DataExportIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final String HEADER = "floodPointSlug,timestamp,sensorPrecipitation,sensorWaterLevel,"
            + "sensorSoilHumidity,weatherPrecipitation,weatherTemperature,weatherPressure,weatherCode,tideHeight,"
            + "accumulated3h,accumulated6h,accumulated12h,accumulated24h,accumulated48h,isFlooded,severity";

    @Autowired private DataExportService exportService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private SensorDataRepository sensorRepository;
    @Autowired private WeatherDataRepository weatherRepository;
    @Autowired private FloodEventRepository eventRepository;

    // A maré vem de API externa; aqui só queremos o export, então um valor fixo.
    @MockitoBean private TideService tideService;

    @BeforeEach
    void semeia() {
        when(tideService.getTideHeightAt(anyDouble(), anyDouble(), any(LocalDateTime.class))).thenReturn(1.23);
        eventRepository.deleteAll();
        sensorRepository.deleteAll();
        weatherRepository.deleteAll();
        pointRepository.deleteAll();

        FloodPoint ponto = pointRepository.save(FloodPoint.builder()
                .slug("ponto-teste").name("Ponto Teste").latitude(-8.05).longitude(-34.88).build());

        LocalDateTime agora = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        sensorRepository.save(SensorData.builder().sensorId("S1").latitude(-8.05).longitude(-34.88)
                .timestamp(agora.minusHours(2)).accumulatedPrecipitation(7.5).build());
        weatherRepository.save(WeatherData.builder().latitude(-8.05).longitude(-34.88)
                .timestamp(agora.minusHours(1)).precipitation(3.0).temperature(27.0).pressure(1010.0).weatherCode(61).build());
        eventRepository.save(FloodEvent.builder().floodPoint(ponto)
                .startTime(agora.minusHours(2)).endTime(agora.minusHours(2)).build());
    }

    @Test
    void csvTemColunasELinhasEsperadas() {
        List<UnifiedDataDTO> dados = exportService.exportUnifiedDataWithAccumulated("ponto-teste", 1);
        // janela de 1 dia, hora a hora, extremos inclusos
        assertThat(dados).hasSize(25);

        String csv = exportService.generateCsv(dados);
        String[] linhas = csv.split("\n");
        assertThat(linhas[0]).isEqualTo(HEADER);
        assertThat(linhas).hasSize(26); // cabeçalho + 25
        assertThat(linhas[1].split(",", -1)).hasSameSizeAs(HEADER.split(","));

        assertThat(dados).filteredOn(d -> Boolean.TRUE.equals(d.getIsFlooded())).hasSize(1);
        assertThat(dados).filteredOn(d -> d.getSensorPrecipitation() == 7.5).hasSize(1);
        assertThat(dados).allSatisfy(d -> assertThat(d.getTideHeight()).isEqualTo(1.23));
    }
}
