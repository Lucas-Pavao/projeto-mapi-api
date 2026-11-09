package com.projeto.mapi.service.ingestion.occurrence.impl;

import com.projeto.mapi.dto.LabelInventoryDTO;
import com.projeto.mapi.repository.ingestion.OccurrenceClusterRepository;
import com.projeto.mapi.repository.ingestion.OccurrenceRepository;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClusterService;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClusterer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OccurrenceClusterServiceImpl implements OccurrenceClusterService {

    static final String CIRCULARITY_WARNING = "Rótulo é alvo e validação. Nada daqui pode realimentar a definição de "
            + "evento, o limiar de classificação ou a capacidade da galeria (ADR 0006, 0017, 0019).";

    private final OccurrenceClusterRepository clusters;
    private final OccurrenceRepository occurrences;

    @Override
    @Transactional
    public LabelInventoryDTO rebuild(double pointRadiusM) {
        List<OccurrenceClusterer.Cluster> built = OccurrenceClusterer.cluster(clusters.canonicalOccurrences(),
                OccurrenceClusterer.DEFAULT_MAX_DISTANCE_M);
        clusters.replaceAll(built, OccurrenceClusterer.RULE_VERSION);
        var totals = clusters.clusterTotals();
        var linkage = occurrences.occurrenceLinkage(null, pointRadiusM);
        List<LabelInventoryDTO.PointLabels> perPoint = linkage.map(l -> clusters.perPoint(l.id()).stream()
                .map(p -> new LabelInventoryDTO.PointLabels(p.slug(), p.occurrences(), p.days(), p.daysWithHour()))
                .toList()).orElse(List.of());
        log.info("rótulos: {} grupos, {} dias, {} com hora", totals.clusters(), totals.days(), totals.daysWithHour());
        return new LabelInventoryDTO(OccurrenceClusterer.RULE_VERSION, OccurrenceClusterer.DEFAULT_MAX_DISTANCE_M,
                clusters.countBy("source"), clusters.countBy("temporal_precision"),
                clusters.countBy("spatial_precision"), clusters.countBy("confidence"), totals.clusters(),
                totals.days(), totals.daysWithHour(), totals.daysAlta(),
                linkage.map(OccurrenceRepository.LinkageRef::radiusM).orElse(null), perPoint, CIRCULARITY_WARNING);
    }
}
