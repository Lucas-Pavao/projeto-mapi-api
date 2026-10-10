package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flood_predictions",
       indexes = {@Index(name = "idx_prediction_timestamp", columnList = "timestamp DESC")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "\"timestamp\"")
    private LocalDateTime timestamp;

    @Column(name = "station_id")
    private String stationId;

    private Double latitude;
    private Double longitude;

    @Column(name = "current_rainfall")
    private Double currentRainfall;

    @Column(name = "rainfall_3h_accumulated")
    private Double rainfall3hAccumulated;

    @Column(name = "rainfall_6h_accumulated")
    private Double rainfall6hAccumulated;

    @Column(name = "rainfall_12h_accumulated")
    private Double rainfall12hAccumulated;

    @Column(name = "rainfall_24h_accumulated")
    private Double rainfall24hAccumulated;

    @Column(name = "tide_level")
    private Double tideLevel;

    @Column(name = "river_level")
    private Double riverLevel;

    @Column(name = "flood_probability")
    private Double floodProbability;

    @Column(name = "risk_level")
    private String riskLevel;

    private String status; // "SUCCESS", "FAILED", "UNKNOWN"
    
    /** Texto fixo para o operador (nunca JSON; a auditoria está nas colunas abaixo). */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "model_version", columnDefinition = "TEXT")
    private String modelVersion;

    /** Versão completa, com hash git. */
    @Column(name = "model_version_full", columnDefinition = "TEXT")
    private String modelVersionFull;

    @Column(name = "horizon_hours")
    private Integer horizonHours;

    /** Nome de {@code DegradationReason}; NULL quando a previsão é válida ou a linha é antiga. */
    @Column(name = "degradation_reason", columnDefinition = "TEXT")
    private String degradationReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "imputed_features", columnDefinition = "jsonb")
    private List<String> imputedFeatures;

    /** Contribuintes do modelo: {@code [{featureId, contribution}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_contributors", columnDefinition = "jsonb")
    private List<Contributor> topContributors;

    /** NULL em linhas antigas e no POST de cenário sem ponto. */
    @Column(name = "flood_point_id")
    private Long floodPointId;

    /** Instante UTC da previsão; NULL nas linhas anteriores à V8 (fuso incerto). */
    @Column(name = "issued_at")
    private Instant issuedAt;

    public record Contributor(String featureId, Double contribution) {
    }
}
