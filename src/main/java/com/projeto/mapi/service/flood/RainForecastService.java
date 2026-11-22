package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import com.projeto.mapi.dto.RainForecastDTO;

import java.util.List;
import java.util.Optional;

/** Previsão de chuva por ponto (plano de 16/11, fase 3): emite no ciclo, grava e confere contra o medido. */
public interface RainForecastService {

    /** Emite e grava a previsão do ponto. Vazio se desligada, sem leitura recente ou IA indisponível. Não lança. */
    Optional<RainForecastDTO> forecastAndStore(long floodPointId, String slug, double lat, double lon);

    /**
     * Para o alerta (ADR 0026): emite e grava como {@link #forecastAndStore}, e devolve a probabilidade de FORTE em 1 h,
     * os quantis de 1 h, o limiar da IA e o motivo da falha, se houver. Não lança.
     */
    default com.projeto.mapi.service.alerta.RainAlertRules.Forecast forecastForAlert(long floodPointId, String slug,
                                                                                   double lat, double lon) {
        return forecastAndStore(floodPointId, slug, lat, lon)
                .map(d -> d.horizons().stream().filter(h -> h.horizonHours() == 1).findFirst()
                        .map(h -> new com.projeto.mapi.service.alerta.RainAlertRules.Forecast(h.probForte(), h.q50Mm(), h.q80Mm(),
                                null, d.modelVersion(), null))
                        .orElse(com.projeto.mapi.service.alerta.RainAlertRules.Forecast.failed(
                                com.projeto.mapi.service.alerta.SemDadoMotivo.PREVISAO_RECUSADA)))
                .orElse(com.projeto.mapi.service.alerta.RainAlertRules.Forecast.failed(
                        com.projeto.mapi.service.alerta.SemDadoMotivo.IA_INDISPONIVEL));
    }

    /** Leituras de 10 min das últimas 6 h (estações a até 40 km) e horas do Open-Meteo, as mesmas que o rain_v1 recebe. */
    record Inputs(List<com.projeto.mapi.repository.analise.RainForecastRepository.Reading> readings,
                  List<com.projeto.mapi.repository.analise.RainForecastRepository.OmHour> openMeteo) {}

    /** Entradas da chuva prevista para o alerta por regressão (POST /v1/flood/forecast). Vazias se não houver leitura. */
    default Inputs inputs(long floodPointId, String slug, double lat, double lon) {
        return new Inputs(List.of(), List.of());
    }

    /** Última previsão gravada de cada ponto. Leitura pura. */
    List<RainForecastDTO> latest();

    /** Confere as previsões vencidas contra a chuva medida. Devolve quantas linhas conferiu. */
    int verifyPending();

    RainForecastAccuracyDTO accuracy(int days);
}
