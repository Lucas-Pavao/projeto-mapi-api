package com.projeto.mapi.service.ingestion.adapter;

import java.time.LocalDate;

/** Acesso HTTP ao Open-Meteo archive com models=era5_land; interface para ser substituída nos testes. */
public interface Era5LandClient {

    /** URL efetiva e corpo bruto da resposta. */
    record Response(String url, String body) {}

    Response fetch(double lat, double lon, LocalDate start, LocalDate end);
}
