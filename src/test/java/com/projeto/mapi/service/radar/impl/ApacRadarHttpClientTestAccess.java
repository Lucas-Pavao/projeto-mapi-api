package com.projeto.mapi.service.radar.impl;

import java.util.List;

/** Acesso de teste ao parser da listagem (pacote-privado). */
public final class ApacRadarHttpClientTestAccess {
    private ApacRadarHttpClientTestAccess() {}

    public static List<String> parse(String html) {
        return ApacRadarHttpClient.parseListing(html);
    }
}
