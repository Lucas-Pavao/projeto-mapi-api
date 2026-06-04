package com.projeto.mapi.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

/**
 * Registro estático de estações APAC para fallback de coordenadas.
 * Útil quando o relatório histórico não fornece lat/lon mas conhecemos a estação.
 */
public class ApacStationRegistry {

    @Getter
    @AllArgsConstructor
    public static class StationMetadata {
        private String code;
        private String name;
        private Double latitude;
        private Double longitude;
    }

    private static final Map<String, StationMetadata> registry = new HashMap<>();

    static {
        // Recife e RMR
        register("261160615A", "Ibura", -8.122, -34.955);
        register("261160601A", "Várzea 1", -8.036, -34.98);
        register("261160621A", "Areias", -8.102, -34.929);
        register("261160609A", "Imbiribeira", -8.120975, -34.913983);
        register("261160617A", "Pina", -8.099, -34.887);
        register("261160612A", "Guabiraba", -7.994, -34.936);
        register("261160614A", "Campina do Barreto", -8.013, -34.881);
        register("261160603G", "Brega E Chique", -8.038048, -34.979298);
        register("261160618A", "Torreão", -8.037, -34.884);
        register("261160616G", "Compaz - Alto Sta. Terezinha", -8.00919, -34.902819);
        register("261160613A", "Morro da conceição", -8.019, -34.915);
        register("261160619A", "San Martin", -8.073, -34.925);
        register("261160608A", "Córrego do Jenipapo", -8.007, -34.936);
        register("261160605A", "Nova Descoberta", -8.001917, -34.919278);
        register("261160623A", "Sede", -8.05, -34.90); // Estimativa central
        
        // Jaboatão
        register("260790119H", "Engenho Velho", -8.1065, -35.0132);
        register("260790106A", "Piedade", -8.154, -34.914);
        register("260290205A", "Pontes dos Carvalhos", -8.233, -34.979);
        
        // Olinda / Paulista
        register("260960004A", "Ouro Preto", -7.999103, -34.859839);
        register("260960003A", "Bonsucesso", -8.008, -34.851);
        register("261070705A", "Janga 2", -7.941571, -34.825915);
        
        // Outros
        register("260345406A", "Aldeia", -7.956, -35.009);
        register("260345404A", "Jardim Primavera", -8.021425, -34.987566);
        register("260680403A", "Alto do Céu", -7.807, -34.933);
        register("260720801A", "IFPE Ipojuca", -8.4, -35.064);
        register("260720804A", "Rurópolis", -8.411, -35.07);
    }

    private static void register(String code, String name, Double lat, Double lon) {
        registry.put(code, new StationMetadata(code, name, lat, lon));
    }

    public static StationMetadata getMetadata(String code) {
        if (code == null) return null;
        // Limpa sufixos comuns se necessário
        return registry.get(code.toUpperCase());
    }
    
    public static StationMetadata findByName(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        return registry.values().stream()
                .filter(m -> upper.contains(m.getName().toUpperCase()))
                .findFirst()
                .orElse(null);
    }
}
