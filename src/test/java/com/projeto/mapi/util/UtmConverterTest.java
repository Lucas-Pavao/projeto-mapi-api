package com.projeto.mapi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UtmConverterTest {

    @Test
    void rn1DaFichaF41BateComOGdal() {
        // RN1-DHN (F-41 do Porto do Recife): 08°03'23,0490" S, 034°52'06,9804" W. Referência: GDAL/PROJ EPSG:4674 -> EPSG:31985
        // = E 294 074,3 m, N 9 108 996,0 m (docs/extratos/f41-porto-recife.md §5).
        double lat = -(8 + 3 / 60.0 + 23.0490 / 3600);
        double lon = -(34 + 52 / 60.0 + 6.9804 / 3600);
        double[] en = UtmConverter.toUtm25S(lat, lon);
        assertThat(en[0]).isCloseTo(294_074.3, within(0.1));
        assertThat(en[1]).isCloseTo(9_108_996.0, within(0.1));
    }

    @Test
    void meridianoCentralDaZona25NoEquadorCaiNoFalsoLeste() {
        double[] en = UtmConverter.toUtm25S(0.0, -33.0);
        assertThat(en[0]).isCloseTo(500_000.0, within(1e-6));
        assertThat(en[1]).isCloseTo(10_000_000.0, within(1e-6));
    }
}
