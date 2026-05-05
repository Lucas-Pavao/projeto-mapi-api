package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "month_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tide_table_id")
    @com.fasterxml.jackson.annotation.JsonBackReference("tide-month")
    private TideTable tideTable;

    @Column(name = "month_name")
    private String monthName;

    @Column(name = "\"month\"")
    private Integer month;

    @OneToMany(mappedBy = "monthData", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonManagedReference("month-day")
    private List<DayData> days;
}
