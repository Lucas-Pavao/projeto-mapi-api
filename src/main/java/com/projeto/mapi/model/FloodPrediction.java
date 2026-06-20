package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flood_predictions",
       indexes = {@Index(name = "idx_prediction_timestamp", columnList = "timestamp DESC")})
@IdClass(FloodPredictionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
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
    
    @Column(columnDefinition = "TEXT")
    private String message;
}
