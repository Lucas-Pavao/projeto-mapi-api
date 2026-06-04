package com.projeto.mapi.config;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.service.MapiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class PilotDataSeeder implements CommandLineRunner {

    private final MapiService mapiService;

    @Override
    public void run(String... args) {
        mapiService.seedPilotData();
    }
}
