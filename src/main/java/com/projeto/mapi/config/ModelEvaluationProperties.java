package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Arquivo congelado da avaliação F1 (ADR 0014 / F0 achado 7). Prefixo {@code mapi.model.evaluation}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.model.evaluation")
public class ModelEvaluationProperties {

    /** No contêiner, ../avaliacao-f1 é montado somente leitura em /data/avaliacao-f1 (docker-compose.yml). */
    private String path = "/data/avaliacao-f1/resultado_etapa2.json";

    /**
     * SHA-256 esperado de resultado_etapa2.json (fonte: sha256sum, 2026-10-21; igual a
     * projeto-mapi-ai/avaliacao/HASHES.sha256).
     */
    private String expectedSha256 = "dcf020cb57a3d1cce5e4a7c0efc36db0fc47d4841759dad5fb58be3e6c9fac14";

    /** Nome publicado no campo {@code source} da resposta. */
    private String sourceLabel = "avaliacao-f1/resultado_etapa2.json";
}
