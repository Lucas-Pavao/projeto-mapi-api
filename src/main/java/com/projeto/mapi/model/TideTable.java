package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "tide_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TideTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "\"year\"")
    private Integer year;

    @Column(name = "harbor_name")
    private String harborName;

    private String state;

    private String timezone;

    private String card;

    @Column(name = "data_collection_institution")
    private String dataCollectionInstitution;

    @Column(name = "mean_level")
    private Float meanLevel;

    @OneToMany(mappedBy = "tideTable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeoLocation> geoLocations;

    @OneToMany(mappedBy = "tideTable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonthData> months;
}
