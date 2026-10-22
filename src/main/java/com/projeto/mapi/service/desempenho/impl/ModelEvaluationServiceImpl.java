package com.projeto.mapi.service.desempenho.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ModelEvaluationProperties;
import com.projeto.mapi.dto.ModelEvaluationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.desempenho.ModelEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelEvaluationServiceImpl implements ModelEvaluationService {

    private final ModelEvaluationProperties props;
    private final ObjectMapper objectMapper;

    /** Confere na subida. Divergência só é registrada: a API continua de pé e o endpoint responde 503. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        try {
            read();
            log.info("Avaliação F1 conferida: {} (sha256 {})", props.getPath(), props.getExpectedSha256());
        } catch (ServiceUnavailableException e) {
            log.warn("Avaliação F1 indisponível na subida (o endpoint responde 503): {}", e.getMessage());
        }
    }

    @Override
    public ModelEvaluationDTO read() {
        Path path = Path.of(props.getPath());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ServiceUnavailableException("Arquivo da avaliação ausente ou ilegível", e);
        }
        // O arquivo tem 19 KB: recalcular o hash a cada leitura custa microssegundos e dispensa cache a invalidar.
        String actual = Hashing.sha256(bytes);
        if (!actual.equalsIgnoreCase(props.getExpectedSha256())) {
            log.error("SHA-256 de {} diverge: esperado {}, lido {}", path, props.getExpectedSha256(), actual);
            throw new ServiceUnavailableException("SHA-256 da avaliação não confere com o registrado");
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        try {
            objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ServiceUnavailableException("Arquivo da avaliação não é JSON válido", e);
        }
        return new ModelEvaluationDTO(actual, props.getSourceLabel(), null, json);
    }
}
