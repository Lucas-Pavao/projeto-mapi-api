package com.projeto.mapi.service.desempenho;

import com.projeto.mapi.dto.BucketSimulationDTO;

import java.util.List;

/** Cliente de POST /v1/simulate/bucket da IA (ADR 0014). */
public interface BucketSimulationClient {

    record Point(String slug, double areaM2, List<Double> zCurvaM, List<Double> vCurvaM3, double ruaRelM) {
    }

    /**
     * {@code ServiceUnavailableException} se a IA estiver fora do ar ou responder erro; {@code UnprocessableEntityException}
     * se ela recusar a entrada (422).
     */
    BucketSimulationDTO simulate(List<Double> rainfallMmH, Point point, double f, double curveScale);
}
