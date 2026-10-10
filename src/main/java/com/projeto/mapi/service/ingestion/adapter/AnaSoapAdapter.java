package com.projeto.mapi.service.ingestion.adapter;

import org.springframework.stereotype.Component;

/** Fonte ao vivo ANA_SOAP: fuso UNVERIFIED (quarentena TZ_UNVERIFIED até F2-DADOS-07). */
@Component
public class AnaSoapAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "ANA_SOAP";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "ana-soap/1";
    }
}
