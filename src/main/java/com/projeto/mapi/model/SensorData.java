package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"sensor_id", "timestamp"})},
       indexes = {@Index(name = "idx_sensor_timestamp", columnList = "sensor_id, timestamp")})
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

    @Column(name = "\"timestamp\"")
    private LocalDateTime timestamp;

    @Column(name = "station_name")
    private String stationName;

    private Double latitude;
    private Double longitude;
    private String municipality;
    private String type;
    private String source;
}
