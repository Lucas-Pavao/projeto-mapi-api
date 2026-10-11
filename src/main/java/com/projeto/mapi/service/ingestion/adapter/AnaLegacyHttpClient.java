package com.projeto.mapi.service.ingestion.adapter;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** HTTP do serviço legado da ANA (timeouts da fonte "ana-historical"). */
@Component
@RequiredArgsConstructor
public class AnaLegacyHttpClient implements AnaLegacyClient {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExternalRestClientFactory http;
    private final IngestionProperties props;

    @Override
    public Response inventory() {
        return get(props.getAnaLegacyUrl() + "/ListaEstacoesTelemetricas?statusEstacoes=0&origem=0");
    }

    @Override
    public Response series(String stationCode, LocalDate start, LocalDate end) {
        return get(props.getAnaLegacyUrl() + "/DadosHidrometeorologicos?CodEstacao=" + stationCode
                + "&DataInicio=" + BR.format(start) + "&DataFim=" + BR.format(end));
    }

    private Response get(String url) {
        try {
            String body = http.client("ana-historical", props.getAnaLegacyUrl(), Map.of())
                    .get().uri(URI.create(url)).retrieve().body(String.class);
            if (body == null || body.isBlank()) throw new SourceUnavailableException("ANA legado: corpo vazio em " + url);
            return new Response(url, body);
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SourceUnavailableException("ANA legado indisponível (" + url + "): " + e.getMessage(), e);
        }
    }
}
