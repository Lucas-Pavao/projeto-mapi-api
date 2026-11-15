package com.projeto.mapi.service.station;

import com.projeto.mapi.dto.CanonicalStationDTO;
import com.projeto.mapi.dto.StationUnificationReportDTO;

import java.util.List;

/** Estação canônica: a mesma estação física vinda da coleta ao vivo e da PED vira uma só (ADR 0023). */
public interface StationUnificationService {

    /** Refaz as tabelas derivadas (canônicas, apelidos, divergências) e devolve a tabela da unificação. */
    StationUnificationReportDTO unify();

    List<CanonicalStationDTO> canonicalStations();
}
