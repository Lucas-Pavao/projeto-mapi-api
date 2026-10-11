package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionResponseDTO.Contributor;
import com.projeto.mapi.dto.FloodPredictionResponseDTO.ImputedFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Catálogo fixo {@code featureId -> rótulo em português} (ADR 0007 §5), carregado de
 * {@code feature-catalog.properties}. O nome interno da feature nunca vai sozinho para a tela:
 * id fora do catálogo vira "Outro fator" e gera WARN, sinal de que o catálogo está desatualizado.
 */
@Component
@Slf4j
public class FeatureCatalog {

    public static final String UNKNOWN_LABEL = "Outro fator";
    private static final String RESOURCE = "/feature-catalog.properties";

    private final Properties labels = new Properties();

    public FeatureCatalog() {
        try (InputStream in = FeatureCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Catálogo de features ausente: " + RESOURCE);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                labels.load(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o catálogo de features", e);
        }
    }

    public boolean contains(String featureId) {
        return featureId != null && labels.containsKey(featureId);
    }

    public String label(String featureId) {
        String label = featureId == null ? null : labels.getProperty(featureId);
        if (label == null) {
            log.warn("featureId fora do catálogo: '{}' (catálogo desatualizado); exibindo '{}'", featureId, UNKNOWN_LABEL);
            return UNKNOWN_LABEL;
        }
        return label;
    }

    public Contributor contributor(String featureId, Double contribution) {
        return new Contributor(featureId, label(featureId), contribution);
    }

    public List<ImputedFeature> imputed(Collection<String> featureIds) {
        if (featureIds == null) return List.of();
        return featureIds.stream().distinct().map(id -> new ImputedFeature(id, label(id))).toList();
    }
}
