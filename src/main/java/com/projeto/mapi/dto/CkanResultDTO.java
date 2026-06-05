package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanResultDTO {
    @JsonProperty("resource_id")
    private String resourceId;
    
    @JsonProperty("records")
    private List<Map<String, Object>> rawRecords;
    
    private Integer total;
}
