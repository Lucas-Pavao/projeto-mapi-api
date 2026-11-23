package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Obras que mudam a física de um ponto (prefixo {@code mapi.obras}; onda 16, Fase 3.4). Cada previsão gravada leva o
 * período da obra ({@code ANTES}, {@code DEPOIS} ou {@code SEM_OBRA}) em {@code flood_forecast_point.payload.api_context}, e
 * nenhuma validação mistura antes e depois (projeto-mapi-ai/mapi_ai/obras.py). Data de início de operação vazia = a obra
 * ainda não opera: tudo é ANTES.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.obras")
public class ObrasProperties {

    private Map<String, List<Obra>> pontos = defaults();

    @Data
    public static class Obra {
        private String nome;
        private String descricao;
        private String fonte;
        /** Primeiro dia de operação (America/Recife). Nulo = ainda não opera. */
        private LocalDate inicioOperacao;
    }

    /** ANTES, DEPOIS ou SEM_OBRA para o ponto no instante {@code t}. Com mais de uma obra, vale a mais recente que já opera. */
    public String periodo(String slug, Instant t) {
        List<Obra> obras = pontos.get(slug);
        if (obras == null || obras.isEmpty()) return "SEM_OBRA";
        LocalDate dia = t.atZone(ZoneId.of("America/Recife")).toLocalDate();
        for (Obra o : obras) if (o.getInicioOperacao() != null && !dia.isBefore(o.getInicioOperacao())) return "DEPOIS";
        return "ANTES";
    }

    private static Map<String, List<Obra>> defaults() {
        Map<String, List<Obra>> m = new LinkedHashMap<>();
        Obra o = new Obra();
        o.setNome("Reservatório ProMorar da Rua Muritiba");
        o.setDescricao("reservatório de 6.400 m³ com bombeamento para o Canal da Mauricéia");
        o.setFonte("Prefeitura do Recife, 10/09/2026 (prompt da onda 16, Fase 3.4)");
        o.setInicioOperacao(null);
        m.put("MASCARENHAS_IMBIRIBEIRA", List.of(o));
        return m;
    }
}
