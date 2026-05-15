package com.projeto.mapi.controller;

import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@Tag(name = "Weather", description = "Endpoints para consulta de clima via Open-Meteo")
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    @Operation(summary = "Obter dados climáticos atuais por latitude e longitude")
    public ResponseEntity<WeatherResponseDTO> getWeather(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        
        WeatherResponseDTO data = weatherService.getWeatherData(latitude, longitude);
        return ResponseEntity.ok(data);
    }
}
