package com.projeto.mapi.service.flood;

import java.time.Instant;

/** Monta as entradas de chuva da previsão ao vivo a partir da série medida (PED + coleta ao vivo, ADR 0023). */
public interface PointRainInputService {

    /** Entradas na coordenada, na última hora fechada até {@code now} (ou na anterior, dentro da tolerância de latência). */
    PointRainInputs inputsAt(double lat, double lon, Instant now);
}
