package com.projeto.mapi.service.weather;

import com.projeto.mapi.dto.WeatherResponseDTO;

public interface WeatherService {
    // Leitura pura (cacheada) — nunca grava em weather_data. Usada por qualquer consumidor ad-hoc
    // (controller, MapiServiceImpl) que só precisa do clima atual, sem virar amostra histórica.
    WeatherResponseDTO getWeatherData(double latitude, double longitude);

    // Grava explicitamente uma amostra em weather_data. Chamado só pelo coletor agendado
    // (DataCollectionTask), que é o único responsável por decidir quando um dado de clima vira
    // histórico para o dataset da IA — não é mais um efeito colateral escondido de toda leitura.
    void recordWeatherSample(WeatherResponseDTO data);
}
