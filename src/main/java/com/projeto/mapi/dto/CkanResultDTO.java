package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanResultDTO {
    @JsonProperty("resource_id")
    private String resourceId;
    private List<CkanRecordDTO> records;
    private Integer total;
}
