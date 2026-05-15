package com.projeto.mapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class MapiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MapiApplication.class, args);
    }
}
