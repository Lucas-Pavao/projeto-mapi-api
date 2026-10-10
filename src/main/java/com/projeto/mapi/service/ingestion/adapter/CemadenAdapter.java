package com.projeto.mapi.service.ingestion.adapter;

import org.springframework.stereotype.Component;

/** Fonte ao vivo CEMADEN: fuso UNVERIFIED (quarentena TZ_UNVERIFIED até F2-DADOS-07). */
@Component
public class CemadenAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "CEMADEN";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "apac-cemaden/1";
    }
}
