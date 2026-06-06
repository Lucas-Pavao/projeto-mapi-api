package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "flood_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;
    
    private String municipality;
    private String description;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;

    private Double altitudeM;
    private Double distanceToChannelM;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flood_point_pluviometers", joinColumns = @JoinColumn(name = "flood_point_id"))
    @Column(name = "station_id")
    @Builder.Default
    private java.util.Set<String> pluviometerStationIds = new java.util.HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flood_point_river_levels", joinColumns = @JoinColumn(name = "flood_point_id"))
    @Column(name = "station_id")
    @Builder.Default
    private java.util.Set<String> riverLevelStationIds = new java.util.HashSet<>();
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flood_point_weather_stations", joinColumns = @JoinColumn(name = "flood_point_id"))
    @Column(name = "station_id")
    @Builder.Default
    private java.util.Set<String> weatherStationIds = new java.util.HashSet<>();

    private String basinName;

    @Column(name = "alert_threshold_mm")
    private Double alertThresholdMm;

    @Builder.Default
    private Boolean active = true;
}
