package com.projeto.mapi.service.ingestion.adapter;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;

/**
 * HTTP do histórico da APAC. O servidor só devolve dado na exportação depois da consulta em {@code diario.php} com os
 * mesmos filtros, e recusa cliente sem {@code User-Agent} de navegador e {@code Referer} da própria página
 * (verificado em 2026-11-08). Uma requisição por vez, com pausa ({@code mapi.ingestion.apac-hist-pause-ms}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApacHistoricoHttpClient implements ApacHistoricoClient {

    static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0 Safari/537.36 MAPi-pesquisa";

    private final ExternalRestClientFactory http;
    private final IngestionProperties props;

    @Override
    public synchronized Response daily(LocalDate first, LocalDate last) {
        String base = props.getApacHistUrl();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mesorregiao", props.getApacHistMesorregiao());
        form.add("microrregiao", "Todas");
        form.add("municipio", "Todos");
        form.add("bacia", "Todas");
        form.add("dataInicial", first.toString());
        form.add("dataFinal", last.toString());
        String filters = form.toSingleValueMap().toString();
        try {
            // cliente próprio (não o cacheado por fonte): cabeçalhos de navegador só aqui; timeouts de "apac-historical"
            var client = http.builder("apac-historical").defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
            MultiValueMap<String, String> consulta = new LinkedMultiValueMap<>(form);
            consulta.add("tipoBoletim", "Diário");
            client.post().uri(base + "diario.php").header(HttpHeaders.REFERER, base)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(consulta).retrieve().body(String.class);
            pause();
            MultiValueMap<String, String> export = new LinkedMultiValueMap<>(form);
            export.add("download_excel", "");
            byte[] body = client.post().uri(base + "export_diario.php").header(HttpHeaders.REFERER, base + "diario.php")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(export).retrieve().body(byte[].class);
            pause();
            if (body == null || body.length == 0) throw new SourceUnavailableException("APAC histórico: corpo vazio");
            return new Response(base + "export_diario.php", filters, body);
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SourceUnavailableException("APAC histórico indisponível (" + filters + "): " + e.getMessage(), e);
        }
    }

    private void pause() {
        try {
            Thread.sleep(props.getApacHistPauseMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("APAC histórico: interrompido", e);
        }
    }
}
