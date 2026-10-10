package com.projeto.mapi.service.geocoding.impl;

import com.projeto.mapi.service.geocoding.GeocodeLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** F2-BACK-20: nível de precisão derivado do tipo de objeto que o Nominatim devolve. */
class NominatimGeocodeLevelTest {

    @Test
    void viaEEdificioSaoRua() {
        assertThat(NominatimGeocodingServiceImpl.levelOf("road", "residential", "highway", GeocodeLevel.NEIGHBORHOOD))
                .isEqualTo(GeocodeLevel.STREET);
        assertThat(NominatimGeocodingServiceImpl.levelOf(null, "house", "building", GeocodeLevel.NEIGHBORHOOD))
                .isEqualTo(GeocodeLevel.STREET);
    }

    @Test
    void bairroECentroideDeBairro() {
        // consulta de nível 1 (rua) que o Nominatim respondeu com o bairro: o resultado manda, não a consulta
        assertThat(NominatimGeocodingServiceImpl.levelOf("suburb", "suburb", "place", GeocodeLevel.STREET))
                .isEqualTo(GeocodeLevel.NEIGHBORHOOD);
        assertThat(NominatimGeocodingServiceImpl.levelOf("city_district", "administrative", "boundary", GeocodeLevel.STREET))
                .isEqualTo(GeocodeLevel.NEIGHBORHOOD);
    }

    @Test
    void cidadeEMunicipioSaoCity() {
        assertThat(NominatimGeocodingServiceImpl.levelOf("city", "administrative", "boundary", GeocodeLevel.STREET))
                .isEqualTo(GeocodeLevel.CITY);
    }

    @Test
    void semTipoValeONivelDaConsulta() {
        assertThat(NominatimGeocodingServiceImpl.levelOf(null, null, null, GeocodeLevel.NEIGHBORHOOD))
                .isEqualTo(GeocodeLevel.NEIGHBORHOOD);
        assertThat(NominatimGeocodingServiceImpl.levelOf("", null, null, GeocodeLevel.STREET))
                .isEqualTo(GeocodeLevel.STREET);
    }
}
