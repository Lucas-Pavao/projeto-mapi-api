package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.mapper.TideMapper;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.NavyScraperService;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideServiceImpl implements TideService {
    private final TideTableRepository tideTableRepository;
    private final NavyScraperService navyScraperService;

    @Override
    @Transactional
    public Optional<TideTableResponseDTO> getTideTable(String harborName, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(harborName, year);
        
        if (results.isEmpty()) {
            log.info("Dados não encontrados para {} em {}. Tentando buscar no site da Marinha...", harborName, year);
            try {
                if (isTargetPernambucoPort(harborName)) {
                    List<TideTableResponseDTO> scraped = navyScraperService.scrapeAndIngestPernambuco(year);
                    return scraped.stream()
                            .filter(t -> t.getHarborName().equalsIgnoreCase(harborName))
                            .findFirst();
                }
            } catch (Exception e) {
                log.error("Falha na busca automática por dados faltantes: {}", e.getMessage());
            }
            return Optional.empty();
        }
        
        return Optional.of(TideMapper.toDTO(results.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTableResponseDTO> getTideTablesByState(String state, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByStateIgnoreCaseAndYear(state, year);
        
        if (results.isEmpty() && state.equalsIgnoreCase("PE")) {
            log.info("Nenhum dado encontrado para o estado PE em {}. Tentando raspagem...", year);
            try {
                return navyScraperService.scrapeAndIngestPernambuco(year);
            } catch (Exception e) {
                log.error("Erro ao buscar dados do estado PE: {}", e.getMessage());
            }
        }
        
        return results.stream().map(TideMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTableResponseDTO> searchTideTablesByHarbor(String harborName, Integer year) {
        return tideTableRepository.findAllByHarborNameContainingIgnoreCaseAndYear(harborName, year)
                .stream()
                .map(TideMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllHarbors(Integer year) {
        return tideTableRepository.findDistinctHarborNamesByYear(year);
    }

    private boolean isTargetPernambucoPort(String name) {
        String upper = name.toUpperCase();
        return upper.contains("RECIFE") || 
               upper.contains("SUAPE") || 
               upper.contains("FERNANDO DE NORONHA");
    }
}
