package com.projeto.mapi.service.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Open-Meteo por modelo (prompt dos horizontes, bloco B): rodada ao vivo, instante da rodada pelo meta.json e histórico
 * de rodadas (previous-runs-api). Sempre em UTC; resposta fora de UTC é recusada (ADR 0001).
 */
public interface OpenMeteoModelClient {

    /** Hora prevista: fim da hora (a precipitação é a soma da hora que termina no carimbo). */
    record Hour(Instant hourEndUtc, Double precipMm, Double cape) {}

    record Batch(String rawBody, String rawHash, Double gridLat, Double gridLon, List<Hour> hours) {}

    /** Rodada mais nova publicada: início e instante em que ficou disponível. */
    record RunMeta(Instant initUtc, Instant availableUtc) {}

    /** Meta da rodada; vazio para modelos sem rodada própria (best_match). */
    java.util.Optional<RunMeta> runMeta(String model);

    /** Rodada ao vivo, com as horas que terminam até 72 h à frente (o serviço guarda até 48 h). */
    Batch liveRun(double lat, double lon, String model);

    /** Histórico: precipitation_previous_dayN (e cape_previous_dayN se {@code withCape}) entre as datas, por lead. */
    java.util.Map<Integer, Batch> previousRuns(double lat, double lon, String model, LocalDate start, LocalDate end,
                                               List<Integer> leads, boolean withCape);
}
