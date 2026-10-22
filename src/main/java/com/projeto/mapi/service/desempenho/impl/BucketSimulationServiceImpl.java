package com.projeto.mapi.service.desempenho.impl;

import com.projeto.mapi.dto.BucketSimulationDTO;
import com.projeto.mapi.dto.BucketSimulationRequestDTO;
import com.projeto.mapi.dto.TerrainResponseDTO;
import com.projeto.mapi.exception.UnprocessableEntityException;
import com.projeto.mapi.service.desempenho.BucketSimulationClient;
import com.projeto.mapi.service.desempenho.BucketSimulationService;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BucketSimulationServiceImpl implements BucketSimulationService {

    /** Níveis de f de docs/PARAMETROS-MODELO-FISICO.md:66 e faixa de curve_scale de :69. */
    static final List<Double> F_LEVELS = List.of(0.0, 0.2, 0.4, 0.6, 0.8, 1.0);
    static final double SCALE_MIN = 0.7, SCALE_MAX = 1.3;

    private final TerrainQueryService terrain;
    private final BucketSimulationClient client;

    @Override
    public BucketSimulationDTO simulate(BucketSimulationRequestDTO req) {
        double f = req.f() == null ? 1.0 : req.f();
        double scale = req.curveScale() == null ? 1.0 : req.curveScale();
        if (F_LEVELS.stream().noneMatch(l -> Math.abs(l - f) < 1e-9)) {
            throw new UnprocessableEntityException("f deve ser 0, 0.2, 0.4, 0.6, 0.8 ou 1.0");
        }
        if (scale < SCALE_MIN || scale > SCALE_MAX) {
            throw new UnprocessableEntityException("curveScale deve estar entre 0.7 e 1.3");
        }

        TerrainResponseDTO t = terrain.findBySlug(req.slug()); // 404 se o ponto não existe
        if (!"OK".equals(t.status()) || t.volumeCurve() == null || t.volumeCurve().size() < 2
                || t.contributingAreaM2() == null || t.contributingAreaM2() <= 0
                || t.streetElevationM() == null || t.bottomElevationM() == null) {
            throw new UnprocessableEntityException("O ponto não tem terreno carregado para a simulação");
        }
        // Mesma montagem de avaliacao/entradas.py: z = altura acima do fundo (m), V acumulado (m3),
        // rua_rel = cota da rua (raio de 3 m) - cota do fundo.
        List<Double> z = t.volumeCurve().stream().map(c -> c.aboveBottomCm() / 100.0).toList();
        List<Double> v = t.volumeCurve().stream().map(TerrainResponseDTO.CurvePoint::cumulativeVolumeM3).toList();
        var point = new BucketSimulationClient.Point(req.slug(), t.contributingAreaM2(), z, v,
                t.streetElevationM() - t.bottomElevationM());
        return client.simulate(req.rainfallMmH(), point, f, scale);
    }
}
