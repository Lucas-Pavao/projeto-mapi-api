package com.projeto.mapi.service.geocoding;

/** Precisão espacial do resultado do geocodificador (F2-BACK-20, ADR 0025). Persistido em flood_occurrence.geocode_level. */
public enum GeocodeLevel {
    /** Endereço com número ou esquina confirmada pelo geocodificador (house_number presente). */
    ADDRESS,
    /** Rua, endereço sem número, edificação ou ponto de interesse. */
    STREET,
    /** Centróide de bairro/distrito: não distingue ruas dentro do bairro. */
    NEIGHBORHOOD,
    /** Só a cidade ou área maior. */
    CITY,
    /** Sem coordenada. */
    NONE
}
