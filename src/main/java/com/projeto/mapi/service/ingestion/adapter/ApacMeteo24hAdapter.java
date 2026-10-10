package com.projeto.mapi.service.ingestion.adapter;

import org.springframework.stereotype.Component;

/** Fonte ao vivo APAC_METEO24H: fuso UNVERIFIED (quarentena TZ_UNVERIFIED até F2-DADOS-07). */
@Component
public class ApacMeteo24hAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "APAC_METEO24H";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "apac-meteo24h/1";
    }
}
