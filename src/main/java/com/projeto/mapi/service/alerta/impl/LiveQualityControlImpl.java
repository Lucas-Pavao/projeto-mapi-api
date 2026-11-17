package com.projeto.mapi.service.alerta.impl;

import com.projeto.mapi.repository.analise.PointRain10MinRepository.Block;
import com.projeto.mapi.service.alerta.LiveQualityControl;
import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Placeholder até a Etapa 2.4: sem filtro extra (o pico isolado já sai no compositor). */
@Service
public class LiveQualityControlImpl implements LiveQualityControl {
    @Override
    public PointRain10MinComposer.LiveFilter filterFor(long floodPointId, List<Block> blocks, Instant now) {
        return PointRain10MinComposer.LiveFilter.NONE;
    }
}
