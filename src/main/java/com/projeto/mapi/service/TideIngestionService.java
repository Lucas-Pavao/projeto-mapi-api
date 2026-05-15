package com.projeto.mapi.service;

import com.projeto.mapi.dto.TideTableResponseDTO;

public interface TideIngestionService {
    TideTableResponseDTO ingestRecifeTide(Integer year) throws Exception;
}
