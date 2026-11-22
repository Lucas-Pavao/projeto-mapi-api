package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.warning.InmetForecastParser.ParsedForecast;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parser da previsão do INMET por município sobre a resposta real de Recife (22/11/2026, ícones encurtados). */
class InmetForecastParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private String fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/inmet/previsao-2611606-real.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void doisDiasPorTurnoETresDiasInteiros() throws Exception {
        List<ParsedForecast> out = InmetForecastParser.parse(fixture(), "2611606", mapper);
        assertThat(out).hasSize(9);
        assertThat(out.subList(0, 3)).extracting(ParsedForecast::period).containsExactly("MANHA", "TARDE", "NOITE");
        assertThat(out.get(0).forecastDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(out.get(6).period()).isEqualTo("DIA");
        assertThat(out.get(6).forecastDate()).isEqualTo(LocalDate.of(2026, 11, 24));
        ParsedForecast m = out.get(0);
        assertThat(m.municipality()).isEqualTo("Recife");
        assertThat(m.summary()).isEqualTo("Poucas nuvens");
        assertThat(m.tempMax()).isEqualByComparingTo(new BigDecimal("34"));
        assertThat(m.weather()).isNull();                      // "tempo" vem vazio: ausente, não ""
        assertThat(m.issueHourText()).isEqualTo("13");
    }

    @Test
    void semCampoDeChuvaNaOrigemERainFieldsVazio() throws Exception {
        assertThat(InmetForecastParser.parse(fixture(), "2611606", mapper))
                .allSatisfy(f -> assertThat(f.rainFieldsJson()).isEqualTo("{}"));
    }

    @Test
    void hashIgnoraIconesEHoraDaConsultaMasMudaComOTexto() throws Exception {
        String base = fixture();
        String a = InmetForecastParser.parse(base, "2611606", mapper).get(0).entryHash();
        String outroIcone = base.replaceFirst("data:image/png;base64,AAAA", "data:image/png;base64,BBBB");
        assertThat(InmetForecastParser.parse(outroIcone, "2611606", mapper).get(0).entryHash()).isEqualTo(a);
        String outraHora = base.replace("\"hora\": \"13\"", "\"hora\": \"14\"");
        assertThat(outraHora).isNotEqualTo(base);
        assertThat(InmetForecastParser.parse(outraHora, "2611606", mapper).get(0).entryHash()).isEqualTo(a);
        String outroTexto = base.replaceFirst("Poucas nuvens", "Pancadas de chuva");
        assertThat(InmetForecastParser.parse(outroTexto, "2611606", mapper).get(0).entryHash()).isNotEqualTo(a);
    }

    @Test
    void campoDeChuvaSeAparecerEGuardadoComoVeio() throws Exception {
        String comChuva = fixture().replaceFirst("\"tempo\": \"\"", "\"tempo\": \"\", \"prob_chuva\": \"60\"");
        assertThat(InmetForecastParser.parse(comChuva, "2611606", mapper).get(0).rainFieldsJson())
                .isEqualTo("{\"prob_chuva\":\"60\"}");
    }

    @Test
    void municipioAusenteOuFormatoInesperadoFalhaAlto() throws Exception {
        assertThatThrownBy(() -> InmetForecastParser.parse(fixture(), "2607901", mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2607901");
        assertThatThrownBy(() -> InmetForecastParser.parse("{\"2611606\": {\"2026-11-22\": {}}}", "2611606", mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dd/MM/yyyy");
    }
}
