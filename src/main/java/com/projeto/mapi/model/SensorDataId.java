package com.projeto.mapi.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataId implements Serializable {
    private Long id;
    private LocalDateTime timestamp;
}
