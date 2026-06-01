package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_history", 
       indexes = {@Index(name = "idx_weather_timestamp", columnList = "timestamp, latitude, longitude")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Double latitude;
    private Double longitude;

    private Double temperature;
    
    @Column(name = "apparent_temperature")
    private Double apparentTemperature;

    private Double humidity;

    private Double pressure;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "is_day")
    private Boolean isDay;

    private Double precipitation;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
