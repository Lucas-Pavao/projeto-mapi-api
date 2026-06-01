package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanResourceDTO {
    private String id;
    private String name;
    private String format;
    private String url;
    @JsonProperty("datastore_active")
    private Boolean datastoreActive;
    private String description;
}
