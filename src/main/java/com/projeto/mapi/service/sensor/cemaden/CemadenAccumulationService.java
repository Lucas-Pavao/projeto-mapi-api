package com.projeto.mapi.service.sensor.cemaden;

import com.projeto.mapi.dto.CemadenAccumulationDTO;

import java.util.List;

/** Acumulados oficiais da CEMADEN na RMR, com a conferência da soma de blocos gravada pelo MAPi. */
public interface CemadenAccumulationService {

    List<CemadenAccumulationDTO> rmr();
}
