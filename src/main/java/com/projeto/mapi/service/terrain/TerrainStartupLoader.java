package com.projeto.mapi.service.terrain;

import com.projeto.mapi.config.TerrainProperties;
import com.projeto.mapi.repository.terrain.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Na subida, carrega o terreno do diretório configurado se não houver terrain_run vigente (banco recriado). A carga
 * é a mesma do POST /api/admin/terrain/runs, idempotente pelo hash dos arquivos. Falha só é registrada: a API sobe e o
 * simulador segue respondendo 422 até a carga manual.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainStartupLoader {

    private final TerrainLoadService loadService;
    private final TerrainRepository repository;
    private final TerrainProperties props;

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfMissing() {
        if (!props.isLoadOnStartup()) return;
        try {
            if (repository.currentRun().isPresent()) return;
            var result = loadService.loadConfigured();
            log.info("Terreno carregado na subida: terrain_run {} ({} pontos com dado, {} sem dado, slugs ausentes {})",
                    result.runId(), result.pointsOk(), result.pointsNoData(), result.slugsNotFound());
        } catch (Exception e) {
            log.warn("Terreno não carregado na subida ({}): {}", props.getDir(), e.getMessage());
        }
    }
}
