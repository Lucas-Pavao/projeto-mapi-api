package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import com.projeto.mapi.dto.RainForecastDTO;

import java.util.List;
import java.util.Optional;

/** Previsão de chuva por ponto (plano de 16/11, fase 3): emite no ciclo, grava e confere contra o medido. */
public interface RainForecastService {

    /** Emite e grava a previsão do ponto. Vazio se desligada, sem leitura recente ou IA indisponível. Não lança. */
    Optional<RainForecastDTO> forecastAndStore(long floodPointId, String slug, double lat, double lon);

    /** Última previsão gravada de cada ponto. Leitura pura. */
    List<RainForecastDTO> latest();

    /** Confere as previsões vencidas contra a chuva medida. Devolve quantas linhas conferiu. */
    int verifyPending();

    RainForecastAccuracyDTO accuracy(int days);
}
