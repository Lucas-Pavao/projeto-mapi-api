package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Gravação das imagens do radar da APAC (V33, docs/extratos/radar-apac.md). */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.radar")
public class RadarProperties {
    private boolean enabled = true;
    private String baseUrl = "https://barramento2.apac.pe.gov.br/imagens_radar/";
    /** Volume próprio, montado no contêiner. */
    private String directory = "/data/radar";
    /** CAPPI 1 km (pedido) e acumulado de 1 h (estimativa de chuva, 13 KB). O de 24 h fica fora: deriva do de 1 h. */
    private List<String> products = List.of("cappi1km", "Acum1h");
    private long fixedRateMs = 600_000L;
    private long initialDelayMs = 30_000L;
    /** Quanto para trás procurar na listagem; o servidor guarda de 1 a 4 dias. */
    private int lookbackHours = 120;
    /** Intervalo esperado entre imagens; instante esperado que não está na listagem é registrado como ABSENT. */
    private int stepMinutes = 10;
    /** Só marca ABSENT o instante esperado com pelo menos esta idade (a imagem sai ~10 min depois). */
    private int absentAfterMinutes = 40;
    private long minIntervalMs = 300L;
}
