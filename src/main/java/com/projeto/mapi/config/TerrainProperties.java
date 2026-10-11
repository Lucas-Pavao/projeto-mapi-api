package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuração da carga do terreno do PE3D (ADR 0008). Prefixo {@code mapi.terrain}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.terrain")
public class TerrainProperties {

    /** Diretório com ponto_terreno.csv, ponto_terreno.meta.json e curva_cota_volume.csv (saída de tools/terreno/run.sh). */
    private String dir = "../terreno";

    /** Referência vertical declarada (ADR 0009): sem comparação com a maré. */
    private String verticalReference = "datum do MDT não confirmado (hipótese: altitude normal, Imbituba/REALT 2018)";

    private String verticalReferenceAdr = "0009";

    /** Raio (m) da cota da rua que vira a coluna única (ADR 0008, proposta 3 m). */
    private int elevationRadiusM = 3;

    /** Janela (m) da declividade que vira a coluna única (ADR 0008, proposta 100 m). */
    private int slopeWindowM = 100;
}
