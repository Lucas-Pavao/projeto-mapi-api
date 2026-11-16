package com.projeto.mapi.service.geocoding;

import com.projeto.mapi.repository.ingestion.GeocodeCacheRepository;
import com.projeto.mapi.service.ingestion.occurrence.CkanAddress;
import com.projeto.mapi.util.RmrFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Geocodificação reprodutível (modelo v3, etapa A4): o endereço já visto devolve sempre a mesma coordenada, lida do
 * cache versionado (V28); só endereço novo vai ao geocodificador, e o resultado é gravado com fonte e data.
 *
 * <p>Na subida da aplicação com o cache vazio, ele é preenchido com as coordenadas das ocorrências já gravadas
 * ({@code flood_occurrence}), que são as que produziram os casamentos vigentes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeocodeCacheService {

    public static final String PROVIDER = "NOMINATIM";

    private final GeocodeCacheRepository repo;

    @Value("${mapi.ingestion.geocode-cache.enabled:true}")
    private boolean enabled;

    @Value("${mapi.ingestion.geocode-cache.seed-on-startup:true}")
    private boolean seedOnStartup;

    /** Com o cache vazio na subida, grava as coordenadas das ocorrências já geocodificadas (uma vez; idempotente). */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void seedAtStartup() {
        if (!enabled || !seedOnStartup) return;
        try {
            if (repo.count() == 0) seedFromOccurrences();
        } catch (RuntimeException e) {
            log.warn("Cache de geocodificação: carga inicial falhou: {}", e.getMessage());
        }
    }

    public static String key(String s) {
        return s == null ? "" : RmrFilter.normalize(s).replaceAll("\\s+", " ").trim();
    }

    /** Coordenada do cache; sem ela, chama {@code geocoder} e grava o que ele devolver. */
    public Optional<GeocodeResult> resolve(String cleanAddress, String neighborhood, String city,
                                           Supplier<Optional<GeocodeResult>> geocoder) {
        if (!enabled) return geocoder.get();
        String a = key(cleanAddress), n = key(neighborhood), c = key(city);
        Optional<GeocodeCacheRepository.Entry> hit = repo.findActive(a, n, c);
        if (hit.isPresent()) {
            GeocodeCacheRepository.Entry e = hit.get();
            return Optional.of(new GeocodeResult(e.lat(), e.lon(), level(e.level())));
        }
        Optional<GeocodeResult> fresh = geocoder.get();
        fresh.ifPresent(g -> repo.insertIfAbsent(a, n, c, g.lat(), g.lon(), g.level() == null ? null : g.level().name(),
                PROVIDER, "geocodificado na carga", Instant.now()));
        return fresh;
    }

    /** Preenche o cache com as ocorrências já geocodificadas. Idempotente; devolve quantas chaves gravou. */
    public synchronized int seedFromOccurrences() {
        int n = 0;
        for (GeocodeCacheRepository.Geocoded o : repo.geocodedOccurrences()) {
            if (repo.insertIfAbsent(key(CkanAddress.sanitize(o.addressText())), key(o.neighborhood()), key("Recife"), o.lat(),
                    o.lon(), o.level(), PROVIDER, "flood_occurrence (carga de " + o.at().toString().substring(0, 10) + ")", o.at())) {
                n++;
            }
        }
        if (n > 0) log.info("Cache de geocodificação: {} endereços gravados a partir de flood_occurrence", n);
        return n;
    }

    private static GeocodeLevel level(String s) {
        try {
            return s == null ? null : GeocodeLevel.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
