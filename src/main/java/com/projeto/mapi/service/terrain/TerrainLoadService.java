package com.projeto.mapi.service.terrain;

import com.projeto.mapi.dto.TerrainLoadResultDTO;

import java.nio.file.Path;

public interface TerrainLoadService {

    /** Carga a partir do diretório configurado ({@code mapi.terrain.dir}). */
    TerrainLoadResultDTO loadConfigured();

    /**
     * Idempotente: lê ponto_terreno.csv, ponto_terreno.meta.json e curva_cota_volume.csv de {@code dir}. Mesmo
     * conteúdo (SHA-256 dos três arquivos) devolve o run existente; conteúdo novo cria um terrain_run vigente.
     */
    TerrainLoadResultDTO load(Path dir);
}
