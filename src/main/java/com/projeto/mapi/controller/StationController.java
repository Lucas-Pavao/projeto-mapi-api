package com.projeto.mapi.controller;

import com.projeto.mapi.dto.CanonicalStationDTO;
import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;
import com.projeto.mapi.dto.StationCardDTO;
import com.projeto.mapi.dto.StationHourlyDTO;
import com.projeto.mapi.service.station.StationCardService;
import com.projeto.mapi.service.station.StationHourlyService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final StationHourlyService hourly;
    private final StationCardService card;

    @GetMapping("/canonical")
    @Operation(summary = "Lista as estações canônicas com os apelidos de origem",
            description = "A mesma estação física da coleta ao vivo (CEMADEN, via APAC) e da PED (CEMADEN_PED) aparece uma "
                    + "vez; aliases traz SOURCE:station_id de cada origem. Unificação por coordenada, até 150 m. Traz também as fontes, "
                    + "a checagem do último mês (monthCheck, null se nunca checada), a quarentena aberta por motivo e a "
                    + "contagem de horas divergentes.")
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

    @GetMapping("/{canonicalId}/hourly")
    @Operation(summary = "Chuva horária (mm) de uma estação canônica, com a fonte usada e o pico isolado marcado",
            description = "Uma linha por hora de (from, to], inclusive sem dado (valueMm nulo, nunca zero). usedSource é CEMADEN_PED "
                    + "se houver PED na hora, senão CEMADEN. Pico isolado (>= 10 mm com >= 2 vizinhas a até 6 km abaixo de 1 mm) "
                    + "vira isolatedPeakRemoved com valueMm nulo. Janela máxima de 7 dias; padrão: últimas 24 h. Instantes em UTC.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Janela inválida ou maior que 7 dias")
    @ApiResponse(responseCode = "404", description = "Estação canônica não encontrada")
    public ResponseEntity<StationHourlyDTO> hourly(@PathVariable String canonicalId,
            @Parameter(description = "Início (exclusivo), ISO-8601 UTC") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fim (inclusivo), ISO-8601 UTC; padrão: agora") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to != null ? to : Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant start = from != null ? from : end.minus(24, ChronoUnit.HOURS);
        return ResponseEntity.ok(hourly.hourly(canonicalId, start, end));
    }

    @GetMapping("/{canonicalId}/card")
    @Operation(summary = "Card do pluviômetro: blocos de 10 min das 3 h, acumulados, estado, aviso e pontos atendidos",
            description = "Uma chamada com tudo da tela da estação. 'at' = instante de referência (padrão: agora). blocks traz 18 "
                    + "blocos de 10 min (PED se houver, senão ao vivo; mm nulo sem dado ou pico isolado removido). Acumulados "
                    + "nulos se qualquer bloco ou hora da janela faltar, nunca zero. state, na prioridade: NO_RESPONSE (sem leitura "
                    + "há mais de 60 min), GAP_NOW, FAIL_MONTH, DIVERGENT (mais de 0,5 mm nas 2 últimas horas), PEAK_REMOVED (3 h), "
                    + "UNCHECKED, NORMAL; note traz os números do estado. quarantineToday: linhas abertas no dia civil UTC de 'at'. "
                    + "uses: pontos a até 5 km e o papel da estação na chuva de cada um. Instantes em UTC.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "'at' inválido")
    @ApiResponse(responseCode = "404", description = "Estação canônica não encontrada")
    public ResponseEntity<StationCardDTO> card(@PathVariable String canonicalId,
            @Parameter(description = "Instante de referência, ISO-8601 UTC; padrão: agora") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ResponseEntity.ok(card.card(canonicalId, at));
    }
}
