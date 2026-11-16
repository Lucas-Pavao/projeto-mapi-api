package com.projeto.mapi.service.flood;

/** Monta as entradas físicas novas da previsão ao vivo, com as regras de {@code point_physical_inputs_v} (V27). */
public interface PointPhysicalInputService {

    PointPhysicalInputs inputsAt(double lat, double lon, PointRainInputs rain);
}
