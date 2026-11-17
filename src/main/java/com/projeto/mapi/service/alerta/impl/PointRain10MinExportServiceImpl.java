package com.projeto.mapi.service.alerta.impl;

import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.PointRain10MinRepository;
import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import com.projeto.mapi.service.alerta.PointRain10MinExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PointRain10MinExportServiceImpl implements PointRain10MinExportService {

    private final FloodPointRepository points;
    private final PointRain10MinRepository repo;

    @Override
    public void writeCsv(String slug, Instant from, Instant to, Writer out) throws IOException {
        FloodPoint p = points.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        out.write("slot_end_utc,mm,station,n_valid,long_block,block_minutes\n");
        Instant a = from;
        while (a.isBefore(to)) {
            Instant b = a.atOffset(ZoneOffset.UTC).plusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay()
                    .toInstant(ZoneOffset.UTC);
            if (b.isAfter(to)) b = to;
            List<PointRain10MinRepository.Block> blocks = repo.blocks(p.getId(), a, b, 11.0, false);
            Map<String, String> month = repo.monthStatus(blocks.stream().map(PointRain10MinRepository.Block::canonicalId)
                    .distinct().toList(), a, b);
            for (PointRain10MinComposer.Slot s : PointRain10MinComposer.compose(blocks, month, a, b,
                    PointRain10MinComposer.LiveFilter.NONE).slots()) {
                out.write(s.end() + "," + (s.mm() == null ? "" : s.mm()) + "," + (s.stationId() == null ? "" : s.stationId()) + ","
                        + s.nValid() + "," + (s.longBlock() ? 1 : 0) + "," + (s.blockMinutes() == null ? "" : s.blockMinutes()) + "\n");
            }
            out.flush();
            a = b;
        }
    }
}
