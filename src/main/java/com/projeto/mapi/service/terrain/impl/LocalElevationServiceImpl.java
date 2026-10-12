package com.projeto.mapi.service.terrain.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.TerrainProperties;
import com.projeto.mapi.service.terrain.LocalElevationService;
import com.projeto.mapi.util.UtmConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalDouble;

/**
 * Lê {@code altitude/mdt_5m_cm.i16} (int16 em cm, linha a linha de norte para sul) mapeado em memória e devolve a célula
 * que contém o ponto. A grade e o cabeçalho {@code mdt_5m.json} vêm de {@code mapi.terrain.dir}; sem eles, o serviço
 * responde vazio e o chamador cai no Open-Meteo.
 */
@Service
@Slf4j
public class LocalElevationServiceImpl implements LocalElevationService {

    private final Path dir;
    private final ObjectMapper mapper;
    private volatile Grid grid;
    private volatile boolean unavailable;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalElevationServiceImpl(TerrainProperties props, ObjectMapper mapper) {
        this(Path.of(props.getDir()).resolve("altitude"), mapper);
    }

    /** Usado pelos testes, com uma grade sintética. */
    public LocalElevationServiceImpl(Path altitudeDir, ObjectMapper mapper) {
        this.dir = altitudeDir;
        this.mapper = mapper;
    }

    @Override
    public OptionalDouble mdtElevationAt(double latitude, double longitude) {
        Grid g = grid();
        if (g == null) return OptionalDouble.empty();
        double[] en = UtmConverter.toUtm25S(latitude, longitude);
        long col = (long) Math.floor((en[0] - g.xMin) / g.pixelX);
        long row = (long) Math.floor((g.yMax - en[1]) / g.pixelY);
        if (col < 0 || row < 0 || col >= g.cols || row >= g.rows) return OptionalDouble.empty();
        short cm = g.data.getShort((int) ((row * g.cols + col) * 2));
        return cm == g.nodata ? OptionalDouble.empty() : OptionalDouble.of(cm / 100.0);
    }

    private Grid grid() {
        if (grid != null || unavailable) return grid;
        synchronized (this) {
            if (grid != null || unavailable) return grid;
            try {
                JsonNode meta = mapper.readTree(dir.resolve("mdt_5m.json").toFile());
                try (FileChannel ch = FileChannel.open(dir.resolve(meta.path("arquivo").asText("mdt_5m_cm.i16")), StandardOpenOption.READ)) {
                    MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    grid = new Grid(meta.path("x_min").asDouble(), meta.path("y_max").asDouble(),
                            meta.path("pixel_x").asDouble(), Math.abs(meta.path("pixel_y").asDouble()),
                            meta.path("colunas").asLong(), meta.path("linhas").asLong(),
                            (short) meta.path("nodata_cm").asInt(-32768), buf);
                    log.info("Grade de altitude do MDT carregada: {} x {} células ({})", grid.cols, grid.rows, dir);
                }
            } catch (IOException | RuntimeException e) {
                unavailable = true;
                log.warn("Grade de altitude do MDT indisponível em {} ({}); altitude do local cai no Open-Meteo", dir, e.getMessage());
            }
            return grid;
        }
    }

    private record Grid(double xMin, double yMax, double pixelX, double pixelY, long cols, long rows, short nodata,
                        MappedByteBuffer data) {}
}
