package com.projeto.mapi.service.alerta;

import com.projeto.mapi.repository.analise.PointRain10MinRepository.Block;

import java.time.Instant;
import java.util.List;

/**
 * Controle de qualidade ao vivo (item 2.4, ADR 0022 emenda): estação suspeita numa janela móvel de 30 dias terminando
 * em t. O pico isolado já sai no compositor da série de 10 min.
 */
public interface LiveQualityControl {

    PointRain10MinComposer.LiveFilter filterFor(long floodPointId, List<Block> blocks, Instant now);
}
