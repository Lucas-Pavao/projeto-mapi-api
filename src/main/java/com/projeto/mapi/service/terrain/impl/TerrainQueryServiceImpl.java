package com.projeto.mapi.service.terrain.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.TerrainResponseDTO;
import com.projeto.mapi.dto.TerrainResponseDTO.CurvePoint;
import com.projeto.mapi.dto.TerrainResponseDTO.Provenance;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.terrain.TerrainRepository;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerrainQueryServiceImpl implements TerrainQueryService {

    private final FloodPointRepository floodPoints;
    private final TerrainRepository terrain;
    private final ObjectMapper mapper;

    @Override
    public TerrainResponseDTO findBySlug(String slug) {
        floodPoints.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        var run = terrain.currentRun();
        Provenance provenance = run.map(r -> new Provenance(r.vrtSha256(), r.scriptSha256(), r.flightYear(),
                r.verticalReference(), r.runAt().toInstant())).orElse(null);
        Long runId = run.map(TerrainRepository.RunRow::id).orElse(null);

        var point = terrain.findCurrentPoint(slug);
        if (point.isEmpty() || !"OK".equals(point.get().status())) {
            return new TerrainResponseDTO(slug, "NO_DATA", runId, null, null, null, null, null, null, null, List.of(),
                    null, provenance, TerrainResponseDTO.TIDE_WARNING);
        }
        var p = point.get();
        List<CurvePoint> curve = terrain.curve(p.id()).stream()
                .map(c -> new CurvePoint(c.aboveBottomCm(), c.elevationM(), c.cumulativeVolumeM3(), c.m3PerCm(),
                        c.wetAreaM2())).toList();
        return new TerrainResponseDTO(slug, "OK", p.runId(), p.streetElevationM(), p.meanSlopePct(), p.maxSlopePct(),
                p.depressionDepthM(), p.contributingAreaM2(), p.bottomElevationM(), p.bottomDistanceM(), curve,
                json(p.diagnosticsJson()), provenance, TerrainResponseDTO.TIDE_WARNING);
    }

    private JsonNode json(String s) {
        if (s == null) return null;
        try {
            return mapper.readTree(s);
        } catch (IOException e) {
            return null;
        }
    }
}
