package com.projeto.mapi.service.ingestion.adapter.tide;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Maré prevista por análise harmônica (operação O3), só como RESERVA para quando não há tábua oficial (2027 em diante).
 * Mesmo formato do CSV da DHN: {@code tide/harmonic-pe02-<ano>.csv}, gerado por projeto-mapi-ai/mapi_ai/tide/harmonic_pe02.py
 * (UTide sobre a tábua DHN 2021-10..2024-12; a ficha F-41 não traz constantes). Validação contra 2025: altura p95
 * 1,8 cm, horário p95 8 min. A leitura dos extremos só usa esta fonte onde não há extremo oficial (ObservationReadRepository).
 */
@Component
public class HarmonicTideCsvAdapter extends DhnTabuaCsvAdapter {

    public static final String SOURCE = "TIDE_HARMONIC_F41";

    public HarmonicTideCsvAdapter(ObjectMapper mapper, Clock clock) {
        super(mapper, clock);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "harmonic-tide-csv/1";
    }

    @Override
    protected String resourcePattern() {
        return "tide/harmonic-pe02-%d.csv";
    }
}
