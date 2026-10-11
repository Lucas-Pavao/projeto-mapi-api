package com.projeto.mapi.service.ingestion.tzverify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Roda a verificação de fuso (F2-DADOS-07) uma vez e grava o resultado em JSON. Só existe no perfil
 * {@code tzverify}: {@code java -jar app.jar --spring.profiles.active=tzverify --mapi.tz-verify.output=/caminho/saida.json}.
 * Não grava nada no banco; a aplicação do resultado (source_registry) é uma migração Flyway separada.
 */
@Component
@Profile("tzverify")
@RequiredArgsConstructor
@Slf4j
public class TzVerificationRunner implements CommandLineRunner {

    private final TimezoneVerificationService service;
    private final ObjectMapper mapper;

    @Value("${mapi.tz-verify.output:tz-verification.json}")
    private String output;

    @Value("${mapi.tz-verify.sources:ANA_SOAP,CEMADEN,APAC_METEO24H}")
    private String sources;

    @Override
    public void run(String... args) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String s : sources.split(",")) {
            switch (s.trim()) {
                case "ANA_SOAP" -> out.put("ANA_SOAP", service.verifyWithHistory("ANA_SOAP", "Chuva_Adotada", 3));
                case "CEMADEN" -> out.put("CEMADEN", service.verifyLiveOnly("CEMADEN", "chuva_acumulada", 6));
                case "APAC_METEO24H" -> out.put("APAC_METEO24H",
                        service.verifyLiveOnly("APAC_METEO24H", "chuva_acumulada", 3));
                default -> log.warn("Fonte desconhecida na verificação: {}", s);
            }
        }
        mapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValue(Path.of(output).toFile(), out);
        log.info("Verificação de fuso gravada em {}", Files.size(Path.of(output)) + " bytes: " + output);
        System.exit(0);
    }
}
