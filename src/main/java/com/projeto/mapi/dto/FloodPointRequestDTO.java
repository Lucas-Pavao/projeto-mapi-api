package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPointRequestDTO {
    @NotBlank(message = "O ID do ponto (slug) é obrigatório")
    private String id_ponto;

    @NotBlank(message = "O nome do local é obrigatório")
    private String nome;
    
    private String municipio;
    private String descricao;
    
    @NotNull(message = "A latitude é obrigatória")
    private Double latitude;
    
    @NotNull(message = "A longitude é obrigatória")
    private Double longitude;

    private Double altitude_m;
    private Double dist_canal_m;
    
    private SensorConfigDTO config_sensores;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorConfigDTO {
        private String estacao_pluviometrica_id;
        private String estacao_nivel_rio_id;
    }
}
