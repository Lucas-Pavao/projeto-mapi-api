package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanRecordDTO {
    @JsonProperty("_id")
    private Integer id;
    
    @JsonProperty("Regional")
    private String regional;
    
    @JsonProperty("Ano")
    private String ano;
    
    @JsonProperty("Mês")
    private String mes;
    
    @JsonProperty("Data")
    private String data;
    
    @JsonProperty("Ocorrencia")
    private String ocorrencia;
    
    @JsonProperty("Solicitacao")
    private String solicitacao;
    
    @JsonProperty("Endereco")
    private String endereco;
    
    @JsonProperty("Bairro")
    private String bairro;
    
    @JsonProperty("Localidade")
    private String localidade;
}
