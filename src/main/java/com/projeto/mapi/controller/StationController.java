package com.projeto.mapi.controller;

import com.projeto.mapi.dto.CanonicalStationDTO;
import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;
import com.projeto.mapi.service.sensor.cemaden.MeasuredRainAccumulationService;
import com.projeto.mapi.service.station.StationUnificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Estações canônicas e chuva medida por estação (ADR 0023). DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC. */
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Tag(name = "Estações", description = "Estação canônica (uma por estação física) e acumulados medidos do Cemaden")
public class StationController {

    private final StationUnificationService unification;
    private final MeasuredRainAccumulationService accumulations;

    @GetMapping("/canonical")
    @Operation(summary = "Lista as estações canônicas com os apelidos de origem",
            description = "A mesma estação física da coleta ao vivo (CEMADEN, via APAC) e da PED (CEMADEN_PED) aparece uma "
                    + "vez; aliases traz SOURCE:station_id de cada origem. Unificação por coordenada, até 150 m.")
    @ApiResponse(responseCode = "200", description = "OK (lista vazia se a unificação ainda não rodou)")
    public ResponseEntity<List<CanonicalStationDTO>> canonical() {
        return ResponseEntity.ok(unification.canonicalStations());
    }

    @GetMapping("/rain-accumulations")
    @Operation(summary = "Acumulados de chuva MEDIDA por estação canônica (1 a 120 h), da PED do Cemaden",
            description = "Pluviômetro, não reanálise (rainOrigin = MEASURED). Valores em mm; instantes em UTC. "
                    + "Acumulado nulo = sem leitura na janela, nunca zero; status NO_DATA = estação sem nenhum acumulado. "
                    + "Atualizado a cada 10 min fora da requisição (limite da PED: 12 requisições por minuto); stale = "
                    + "última busca com mais de 30 min. Citação obrigatória no campo attribution.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "503", description = "Primeira leitura da PED ainda não concluída")
    public ResponseEntity<MeasuredRainAccumulationsDTO> rainAccumulations() {
        return ResponseEntity.ok(accumulations.current());
    }
}
