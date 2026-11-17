package com.projeto.mapi.service.flood;

import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Chama {@code POST /v1/forecast/rain} da MAPI AI. Vazio = sem previsão (IA fora, dado insuficiente); nunca lança. */
public interface RainForecastClient {

    record Horizon(int horizonHours, Instant validUntil, double q50, double q80, double q95, double probMedia,
                   double probForte, boolean forteAlert, String model) {}

    record Result(String modelVersion, Instant issuedAt, List<Horizon> horizons, List<String> sources,
                  List<String> missingInputs, Instant latestReadingUtc, Double latestReadingAgeMinutes) {}

    Optional<Result> forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo);

    /** Resultado com o motivo da falha: {@code refused} = HTTP 422 (falta entrada); {@code unavailable} = IA fora. */
    record Outcome(Result result, boolean refused, boolean unavailable, Double forteThreshold1h, Integer stationsValidNear) {}

    default Outcome forecastOutcome(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo) {
        return forecast(lat, lon, readings, openMeteo).map(r -> new Outcome(r, false, false, null, null))
                .orElse(new Outcome(null, false, true, null, null));
    }
}
