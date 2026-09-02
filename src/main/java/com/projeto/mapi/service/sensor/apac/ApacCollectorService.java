package com.projeto.mapi.service.sensor.apac;

import com.projeto.mapi.dto.CollectionSummaryDTO;

public interface ApacCollectorService {
    CollectionSummaryDTO collectCemaden();
    CollectionSummaryDTO collectMeteorologia24h();
}
