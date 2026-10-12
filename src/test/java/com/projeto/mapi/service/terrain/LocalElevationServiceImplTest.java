package com.projeto.mapi.service.terrain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.terrain.impl.LocalElevationServiceImpl;
import com.projeto.mapi.util.UtmConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalElevationServiceImplTest {

    // Ponto de teste (RN1-DHN) e a grade 2 x 2 de 5 m montada em volta dele.
    private static final double LAT = -(8 + 3 / 60.0 + 23.0490 / 3600);
    private static final double LON = -(34 + 52 / 60.0 + 6.9804 / 3600);

    @Test
    void devolveACelulaQueContemOPontoEmMetros(@TempDir Path dir) throws Exception {
        double[] en = UtmConverter.toUtm25S(LAT, LON);
        // O ponto fica na célula (linha 0, coluna 1): x_min 7,5 m a oeste, y_max 2,5 m ao norte.
        grade(dir, en[0] - 7.5, en[1] + 2.5, new short[] {100, 266, -32768, 300});
        var svc = new LocalElevationServiceImpl(dir, new ObjectMapper());
        assertThat(svc.mdtElevationAt(LAT, LON)).hasValue(2.66);
    }

    @Test
    void nodataForaDaGradeESemArquivoDaoVazio(@TempDir Path dir) throws Exception {
        double[] en = UtmConverter.toUtm25S(LAT, LON);
        grade(dir, en[0] - 2.5, en[1] + 2.5, new short[] {-32768, 1, 2, 3});
        var svc = new LocalElevationServiceImpl(dir, new ObjectMapper());
        assertThat(svc.mdtElevationAt(LAT, LON)).isEmpty();          // célula nodata
        assertThat(svc.mdtElevationAt(-7.0, -35.5)).isEmpty();       // fora da grade
        assertThat(new LocalElevationServiceImpl(dir.resolve("nao-existe"), new ObjectMapper())
                .mdtElevationAt(LAT, LON)).isEmpty();               // sem grade instalada
    }

    private static void grade(Path dir, double xMin, double yMax, short[] cm) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(cm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short v : cm) b.putShort(v);
        Files.write(dir.resolve("mdt_5m_cm.i16"), b.array());
        Files.writeString(dir.resolve("mdt_5m.json"), String.format(java.util.Locale.ROOT,
                "{\"arquivo\":\"mdt_5m_cm.i16\",\"x_min\":%.4f,\"y_max\":%.4f,\"pixel_x\":5,\"pixel_y\":-5,"
                        + "\"colunas\":2,\"linhas\":2,\"nodata_cm\":-32768}", xMin, yMax));
    }
}
