package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hour_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HourData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_data_id")
    @com.fasterxml.jackson.annotation.JsonBackReference("day-hour")
    private DayData dayData;

    @Column(name = "\"hour\"")
    private String hour;

    @Column(name = "\"level\"")
    private Float level;
}
