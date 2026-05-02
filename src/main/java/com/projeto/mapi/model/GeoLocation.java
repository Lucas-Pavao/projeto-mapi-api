package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "geo_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tide_table_id")
    @com.fasterxml.jackson.annotation.JsonBackReference("tide-geo")
    private TideTable tideTable;

    private String lat;
    private String lng;

    @Column(name = "decimal_lat")
    private String decimalLat;

    @Column(name = "decimal_lng")
    private String decimalLng;

    @Column(name = "lat_direction")
    private String latDirection;

    @Column(name = "lng_direction")
    private String lngDirection;
}
