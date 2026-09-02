package com.projeto.mapi.service;

import com.projeto.mapi.dto.CollectionSummaryDTO;

public interface ApacCollectorService {
    CollectionSummaryDTO collectCemaden();
    CollectionSummaryDTO collectMeteorologia24h();
}
