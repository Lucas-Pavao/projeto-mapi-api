package com.projeto.mapi.service.radar.impl;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.RadarProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.radar.ApacRadarClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Listagem Apache do diretório e download do PNG. Timeout pelo cliente "apac-radar". */
@Component
@RequiredArgsConstructor
public class ApacRadarHttpClient implements ApacRadarClient {

    static final Pattern FILE = Pattern.compile("href=\"(\\d{12}\\.[A-Za-z0-9]+_trans\\.png)\"");

    private final ExternalRestClientFactory restFactory;
    private final RadarProperties props;

    @Override
    public List<String> listFiles() {
        try {
            String html = restFactory.client("apac-radar", props.getBaseUrl(), Map.of()).get().uri("").retrieve().body(String.class);
            if (html == null || html.isBlank()) throw new SourceUnavailableException("Listagem do radar APAC vazia");
            return parseListing(html);
        } catch (RestClientException e) {
            throw new SourceUnavailableException("Falha ao listar o radar APAC: " + e.getMessage(), e);
        }
    }

    static List<String> parseListing(String html) {
        Matcher m = FILE.matcher(html);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        while (m.find()) out.add(m.group(1));
        return new ArrayList<>(out);
    }

    @Override
    public Optional<byte[]> fetch(String fileName) {
        try {
            return Optional.ofNullable(restFactory.client("apac-radar", props.getBaseUrl(), Map.of())
                    .get().uri(fileName).retrieve().body(byte[].class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))) return Optional.empty();
            throw new SourceUnavailableException("Radar APAC " + fileName + ": HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new SourceUnavailableException("Radar APAC " + fileName + ": " + e.getMessage(), e);
        }
    }
}
