package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"sensor_id", "timestamp"})},
       indexes = {@Index(name = "idx_sensor_timestamp", columnList = "sensor_id, timestamp")})
@IdClass(SensorDataId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "\"value\"")
    private Double value;

    private String unit;

    @Column(name = "battery_status")
    private String batteryStatus;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Id
    @Column(name = "\"timestamp\"")
    private LocalDateTime timestamp;

    @Column(name = "station_name")
    private String stationName;

    private Double latitude;
    private Double longitude;
    private String municipality;
    private String type;
    private String source;

    // Novos campos adaptados
    @Column(name = "fog_value_reference")
    private Double fogValueReference;

    @Column(name = "code")
    private String code;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "pressure")
    private Double pressure;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_direction")
    private String windDirection;

    @Column(name = "solar_radiation")
    private Double solarRadiation;

    @Column(name = "accumulated_precipitation")
    private Double accumulatedPrecipitation;

    @Column(name = "soil_humidity")
    private String soilHumidity;

    @Column(name = "water_level")
    private Double waterLevel;

    @Column(name = "flow_rate")
    private Double flowRate;

    @Column(name = "basin_name")
    private String basinName;

    @Column(name = "tide_height")
    private Double tideHeight;
}
