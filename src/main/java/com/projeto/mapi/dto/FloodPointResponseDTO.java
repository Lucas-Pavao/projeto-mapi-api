package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPointResponseDTO {
    private Long id;
    private String id_ponto;
    private String nome;
    private String municipio;
    private String descricao;
    private Double latitude;
    private Double longitude;
    private Double altitude_m;
    private Double dist_canal_m;
    private String bacia_hidrografica;
    private FloodPointRequestDTO.SensorConfigDTO config_sensores;
    private Boolean active;
    private Double tideHeight;
    private String tideUnit;
}
