package com.projeto.mapi.service.geocoding;

/**
 * Coordenada devolvida pelo geocodificador e o nível de precisão dela. {@code level} é nulo quando o
 * geocodificador não informa o nível (implementações que só conhecem {@code geocode}).
 */
public record GeocodeResult(double lat, double lon, GeocodeLevel level) {}
