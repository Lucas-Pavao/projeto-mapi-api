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
    private TideTable tideTable;

    @Column(name = "month_name")
    private String monthName;

    @Column(name = "\"month\"")
    private Integer month;

    @OneToMany(mappedBy = "monthData", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DayData> days;
}
