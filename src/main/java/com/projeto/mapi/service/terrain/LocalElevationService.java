package com.projeto.mapi.service.terrain;

import java.util.OptionalDouble;

/** Cota do MDT do PE3D num ponto qualquer, pela grade de 5 m gerada por {@code tools/terreno/run.sh grade_altitude}. */
public interface LocalElevationService {

    /** Cota em metros (datum do MDT, relativa); vazio fora da área levantada, em nodata ou sem a grade instalada. */
    OptionalDouble mdtElevationAt(double latitude, double longitude);
}
