package com.projeto.mapi.service.ingestion.validation;

/** Texto de data/hora que não pôde ser interpretado: quarentena TS_UNPARSEABLE (nunca now() de fallback). */
public class TsUnparseableException extends RuntimeException {
    public TsUnparseableException(String message) {
        super(message);
    }
}
