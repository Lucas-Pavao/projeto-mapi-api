package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.NavyScraperService;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideServiceImpl implements TideService {
    private final TideTableRepository tideTableRepository;
    private final NavyScraperService navyScraperService;

    @Override
    @Transactional
    public Optional<TideTable> getTideTable(String harborName, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(harborName, year);
        
        if (results.isEmpty()) {
            log.info("Dados não encontrados para {} em {}. Tentando buscar no site da Marinha...", harborName, year);
            try {
                // Se for um dos portos de PE que monitoramos, podemos tentar a raspagem automática
                if (isTargetPernambucoPort(harborName)) {
                    List<TideTable> scraped = navyScraperService.scrapeAndIngestPernambuco(year);
                    return scraped.stream()
                            .filter(t -> t.getHarborName().equalsIgnoreCase(harborName))
                            .findFirst();
                }
            } catch (Exception e) {
                log.error("Falha na busca automática por dados faltantes: {}", e.getMessage());
            }
            return Optional.empty();
        }
        
        return Optional.of(results.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTable> getTideTablesByState(String state, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByStateIgnoreCaseAndYear(state, year);
        
        if (results.isEmpty() && state.equalsIgnoreCase("PE")) {
            log.info("Nenhum dado encontrado para o estado PE em {}. Tentando raspagem...", year);
            try {
                return navyScraperService.scrapeAndIngestPernambuco(year);
            } catch (Exception e) {
                log.error("Erro ao buscar dados do estado PE: {}", e.getMessage());
            }
        }
        
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTable> searchTideTablesByHarbor(String harborName, Integer year) {
        return tideTableRepository.findAllByHarborNameContainingIgnoreCaseAndYear(harborName, year);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllHarbors(Integer year) {
        return tideTableRepository.findDistinctHarborNamesByYear(year);
    }

    @Override
    @Transactional
    public TideTable saveTideTable(TideTable tideTable) {
        return tideTableRepository.save(tideTable);
    }

    private boolean isTargetPernambucoPort(String name) {
        String upper = name.toUpperCase();
        return upper.contains("RECIFE") || 
               upper.contains("SUAPE") || 
               upper.contains("FERNANDO DE NORONHA");
    }
}
