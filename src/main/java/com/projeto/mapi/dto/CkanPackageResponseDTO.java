package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanPackageResponseDTO {
    private Boolean success;
    private CkanPackageResultDTO result;
}
