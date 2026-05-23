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
        if (mapiService.getAllFloodPoints().isEmpty()) {
            log.info("Semeando pontos piloto de monitoramento...");

            List<FloodPointRequestDTO> pilots = List.of(
                FloodPointRequestDTO.builder()
                    .id_ponto("AV_RECIFE_IBURA")
                    .nome("Av. Recife - Entrada do Ibura")
                    .latitude(-8.107910)
                    .longitude(-34.927138)
                    .build(),
                FloodPointRequestDTO.builder()
                    .id_ponto("CIN_UFPE")
                    .nome("CIn - UFPE")
                    .latitude(-8.055310)
                    .longitude(-34.951160)
                    .build(),
                FloodPointRequestDTO.builder()
                    .id_ponto("AGAMENON_DERBY")
                    .nome("Av. Agamenon Magalhães (Derby)")
                    .latitude(-8.052554)
                    .longitude(-34.894371)
                    .build(),
                FloodPointRequestDTO.builder()
                    .id_ponto("JABOATAO_CENTRO")
                    .nome("Jaboatão Centro (Rio Duas Unas)")
                    .latitude(-8.106520)
                    .longitude(-35.013210)
                    .build(),
                FloodPointRequestDTO.builder()
                    .id_ponto("MASCARENHAS_IMBIRIBEIRA")
                    .nome("Av. Mascarenhas de Morais")
                    .latitude(-8.118123)
                    .longitude(-34.904945)
                    .build()
            );

            pilots.forEach(mapiService::createFloodPoint);
            log.info("5 pontos piloto cadastrados com sucesso.");
        }
    }
}
