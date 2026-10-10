package com.projeto.mapi.config;

import com.projeto.mapi.service.ingestion.validation.ValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionConfig {

    @Bean
    public ValidationService validationService(IngestionProperties props) {
        return new ValidationService(props.getSentinels());
    }
}
