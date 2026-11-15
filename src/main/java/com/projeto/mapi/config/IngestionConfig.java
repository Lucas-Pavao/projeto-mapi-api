package com.projeto.mapi.config;

import com.projeto.mapi.service.ingestion.validation.ValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionConfig {

    @Bean
    public ValidationService validationService(IngestionProperties props) {
        return new ValidationService(props.getSentinels(), ValidationService.DEFAULT_MAX_BLOCK,
                java.util.Map.of(com.projeto.mapi.service.ingestion.adapter.ped.CemadenPedAdapter.SOURCE,
                        java.util.Set.copyOf(props.getCemadenPed().getSuspectQualifiers())));
    }
}
