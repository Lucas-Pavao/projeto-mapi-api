package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flood_scenario_labels",
       indexes = {@Index(name = "idx_scenario_timestamp", columnList = "timestamp DESC")})
@IdClass(FloodScenarioLabelId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodScenarioLabel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "\"timestamp\"")
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "is_flooded", nullable = false)
    private Boolean isFlooded;

    // Environmental metrics captured at the time of label creation
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

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_direction")
    private String windDirection;

    private Double temperature;

    @Column(name = "apparent_temperature")
    private Double apparentTemperature;

    private Double humidity;
    private Double pressure;

    @Column(name = "wave_height")
    private Double waveHeight;

    @Column(name = "wave_period")
    private Double wavePeriod;

    @Column(name = "wave_direction")
    private Double waveDirection;

    @Column(name = "solar_radiation")
    private Double solarRadiation;
}
