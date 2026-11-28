package com.projeto.mapi.service.analise.impl;

import com.projeto.mapi.dto.PointMeasuredRainDTO;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.service.analise.PointMeasuredRainService;
import com.projeto.mapi.service.analise.PointRainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PointMeasuredRainServiceImpl implements PointMeasuredRainService {

    private final PointRainRepository repo;
    private final Clock clock;

    @Override
    public Optional<PointMeasuredRainDTO> latest(String slug) {
        return repo.latestAccum(slug, PointRainService.RULE_VERSION, clock.instant());
    }
}
