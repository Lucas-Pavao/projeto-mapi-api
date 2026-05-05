package com.projeto.mapi;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class MapiApplication {
    public static void main(String[] args) {
        // Carrega as variáveis do .env antes de iniciar o Spring
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        
        dotenv.entries().forEach(entry -> 
            System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(MapiApplication.class, args);
    }
}
