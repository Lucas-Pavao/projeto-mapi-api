package com.projeto.mapi.service.station;

import com.projeto.mapi.dto.StationCardDTO;

import java.time.Instant;

/** Card do pluviômetro de uma estação canônica (ADR 0022 e 0023). */
public interface StationCardService {

    /** {@code at} nulo = agora. ResourceNotFoundException se a canônica não existe. */
    StationCardDTO card(String canonicalId, Instant at);
}
