package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "day_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "month_data_id")
    private MonthData monthData;

    @Column(name = "weekday_name")
    private String weekdayName;

    @Column(name = "\"day\"")
    private Integer day;

    @OneToMany(mappedBy = "dayData", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HourData> hours;
}
