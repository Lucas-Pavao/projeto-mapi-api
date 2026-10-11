package com.projeto.mapi.service.ingestion.validation;

/** Fuso da fonte UNVERIFIED (ou inválido): o registro vai para a quarentena TZ_UNVERIFIED. */
public class TzUnverifiedException extends RuntimeException {
    public TzUnverifiedException(String message) {
        super(message);
    }
}
