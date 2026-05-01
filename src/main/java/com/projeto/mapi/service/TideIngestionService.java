package com.projeto.mapi.service;

import com.projeto.mapi.model.TideTable;

public interface TideIngestionService {
    TideTable ingestRecifeTide(Integer year) throws Exception;
}
