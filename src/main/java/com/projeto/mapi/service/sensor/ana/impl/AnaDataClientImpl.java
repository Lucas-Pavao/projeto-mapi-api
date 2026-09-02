package com.projeto.mapi.service.sensor.ana.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.AnaStationDTO;
import com.projeto.mapi.service.sensor.ana.AnaDataClient;
import com.projeto.mapi.util.GeoUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Porta do coletor REST da ANA (projeto-mapi/src/collectors/ana_rest_collector.py +
 * services/auth_manager.py). Ao contrário do Python original, NÃO envia o cookie estático que
 * existia lá — era um artefato de sessão de navegador hardcoded, dispensável para o fluxo OAuth
 * (correção de segurança pedida na migração para dentro da API).
 */
@Service
@Slf4j
public class AnaDataClientImpl implements AnaDataClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.ana.gov.br/hidrowebservice/swagger-ui/index.html";

    private final RestClient restClient;
    private final AppProperties.Ana config;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final Map<String, JsonNode> inventoryCache = new ConcurrentHashMap<>();

    private volatile List<AnaStationDTO> discoveredStationsCache = List.of();
    private volatile Instant discoveryCacheExpiresAt = Instant.EPOCH;

    public AnaDataClientImpl(RestClient.Builder restClientBuilder, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.config = appProperties.getAna();
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "anaData")
    @CircuitBreaker(name = "anaData", fallbackMethod = "fetchStationMeasurementsFallback")
    public List<JsonNode> fetchStationMeasurements(String stationCode, String searchDate) {
        String token = ensureToken();
        if (token == null) {
            log.warn("Sem token ANA válido; abortando coleta da estação {}", stationCode);
            return List.of();
        }

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("Código da Estação", stationCode);
            params.add("Tipo Filtro Data", config.getTipoFiltroData());
            params.add("Data de Busca (yyyy-MM-dd)", searchDate);
            params.add("Range Intervalo de busca", config.getIntervaloBusca());

            JsonNode medicao = getJson(config.getBaseUrl(), params, token);
            JsonNode items = medicao.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return List.of();
            }

            JsonNode inventario = fetchInventory(stationCode, token);
            List<JsonNode> resultado = new ArrayList<>();
            for (JsonNode item : items) {
                if (item.isObject() && inventario != null) {
                    ObjectNode enriched = (ObjectNode) item;
                    enriched.set("Latitude", inventario.path("Latitude"));
                    enriched.set("Longitude", inventario.path("Longitude"));
                    enriched.set("Estacao_Nome", inventario.path("Estacao_Nome"));
                    enriched.set("Bacia_Nome", inventario.path("Bacia_Nome"));
                    enriched.set("Municipio_Nome", inventario.path("Municipio_Nome"));
                }
                resultado.add(item);
            }
            return resultado;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Token ANA rejeitado (401) para estação {}; invalidando cache para nova tentativa.", stationCode);
            cachedToken = null;
            tokenExpiresAt = Instant.EPOCH;
            throw e;
        }
    }

    private List<JsonNode> fetchStationMeasurementsFallback(String stationCode, String searchDate, Throwable t) {
        log.warn("Falha ao coletar dados ANA da estação {}: {} — {}", stationCode, t.getClass().getSimpleName(), t.getMessage());
        return List.of();
    }

    private JsonNode fetchInventory(String stationCode, String token) {
        JsonNode cached = inventoryCache.get(stationCode);
        if (cached != null) {
            return cached;
        }
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("Código da Estação", stationCode);
            JsonNode data = getJson(config.getInventoryUrl(), params, token);
            JsonNode items = data.path("items");
            if (items.isArray() && !items.isEmpty()) {
                JsonNode metadata = items.get(0);
                inventoryCache.put(stationCode, metadata);
                return metadata;
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar inventário ANA para {}: {}", stationCode, e.getMessage());
        }
        return null;
    }

    private synchronized String ensureToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        if (config.getIdentifier() == null || config.getIdentifier().isBlank()) {
            log.warn("ANA_IDENTIFICADOR/ANA_SENHA não configurados; coleta ANA desativada até serem definidos.");
            return null;
        }

        // Validado ao vivo: o endpoint de autenticação da ANA responde em ~30s quando funciona e
        // devolve 503/504 com frequência sob instabilidade da infra deles — o Python de referência
        // já tratava isso com retry dedicado (AuthManager usava urllib3 Retry nesse exato request).
        // Como esse método engole a exceção (não deixa a coleta inteira falhar por causa do login),
        // o @Retry do fetchStationMeasurements nunca alcançaria essa falha — por isso o retry é
        // feito aqui dentro, não lá fora.
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String body = restClient.get()
                        .uri(URI.create(config.getAuthUrl()))
                        .header("identificador", config.getIdentifier())
                        .header("senha", config.getPassword())
                        .header("accept", "application/json")
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(body);
                String token = root.path("items").path("tokenautenticacao").asText(null);
                if (token != null && !token.isBlank()) {
                    cachedToken = token;
                    tokenExpiresAt = Instant.now().plus(59, ChronoUnit.MINUTES);
                    log.info("Token ANA renovado com sucesso (válido por 59min).");
                    return cachedToken;
                }
                log.warn("Resposta de autenticação ANA sem token válido.");
                return null;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("Erro ao obter token ANA (tentativa {}/{}): {}. Retentando...", attempt, maxAttempts, e.getMessage());
                    sleep(2000L * attempt);
                } else {
                    log.error("Erro ao obter token ANA após {} tentativas: {}", maxAttempts, e.getMessage());
                }
            }
        }
        return null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @Retry(name = "anaData")
    @CircuitBreaker(name = "anaData", fallbackMethod = "discoverStationsFallback")
    public synchronized List<AnaStationDTO> discoverStations() {
        if (!discoveredStationsCache.isEmpty() && Instant.now().isBefore(discoveryCacheExpiresAt)) {
            return discoveredStationsCache;
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("codEstacao", "");
        params.add("codBacia", "");
        params.add("codRio", "");
        params.add("codEstado", "");
        params.add("codMunicipio", "");
        params.add("codResp", "");
        params.add("codOrigem", "");
        params.add("Escala", "");
        params.add("StatusEstacoes", "");
        params.add("tpEstacao", "");
        params.add("origem", "");

        URI uri = UriComponentsBuilder.fromHttpUrl(config.getDiscoveryUrl())
                .queryParams(params)
                .build()
                .encode()
                .toUri();

        String xml = restClient.get().uri(uri).retrieve().body(String.class);
        List<AnaStationDTO> descobertas = parseDiscoveryXml(xml);

        discoveredStationsCache = descobertas;
        discoveryCacheExpiresAt = Instant.now().plus(config.getDiscoveryCacheTtlHours(), ChronoUnit.HOURS);
        log.info("Descoberta ANA: {} estações ativas dentro de {}km do centro configurado (cache por {}h).",
                descobertas.size(), config.getDiscoveryRadiusKm(), config.getDiscoveryCacheTtlHours());
        return descobertas;
    }

    private List<AnaStationDTO> discoverStationsFallback(Throwable t) {
        log.warn("Falha na descoberta de estações ANA: {} — {}. Usando última lista conhecida ({} estações).",
                t.getClass().getSimpleName(), t.getMessage(), discoveredStationsCache.size());
        return discoveredStationsCache;
    }

    private List<AnaStationDTO> parseDiscoveryXml(String xml) {
        Map<String, AnaStationDTO> porCodigo = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList tables = doc.getElementsByTagName("Table");
            String ufAlvo = "-" + config.getDiscoveryUf().toUpperCase();

            for (int i = 0; i < tables.getLength(); i++) {
                Element el = (Element) tables.item(i);
                String municipioUf = tag(el, "Municipio-UF");
                if (municipioUf == null || !municipioUf.toUpperCase().endsWith(ufAlvo)) continue;
                if (!"Ativo".equalsIgnoreCase(tag(el, "StatusEstacao"))) continue;

                String codigo = tag(el, "CodEstacao");
                String latStr = tag(el, "Latitude");
                String lonStr = tag(el, "Longitude");
                if (codigo == null || latStr == null || lonStr == null) continue;

                double lat, lon;
                try {
                    lat = Double.parseDouble(latStr);
                    lon = Double.parseDouble(lonStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                double distancia = GeoUtils.calculateDistance(config.getDiscoveryCenterLat(), config.getDiscoveryCenterLon(), lat, lon);
                if (distancia > config.getDiscoveryRadiusKm()) continue;

                // A mesma estação aparece repetida com "Origem" diferente (RHN, CotaOnline, etc.);
                // mantém só a primeira ocorrência de cada código.
                porCodigo.putIfAbsent(codigo, new AnaStationDTO(codigo, tag(el, "NomeEstacao"), tag(el, "NomeRio"), lat, lon));
            }
        } catch (Exception e) {
            log.error("Erro ao parsear XML de descoberta de estações ANA: {}", e.getMessage());
            return List.of();
        }
        return new ArrayList<>(porCodigo.values());
    }

    private String tag(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() > 0 && nl.item(0).getFirstChild() != null) {
            return nl.item(0).getFirstChild().getNodeValue();
        }
        return null;
    }

    private JsonNode getJson(String url, MultiValueMap<String, String> params, String token) {
        URI uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParams(params)
                .build()
                .encode()
                .toUri();

        String body = restClient.get()
                .uri(uri)
                .header("accept", "*/*")
                .header("authorization", "Bearer " + token)
                .header("referer", REFERER)
                .header("user-agent", USER_AGENT)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta inválida da ANA em " + url + ": " + e.getMessage(), e);
        }
    }
}
