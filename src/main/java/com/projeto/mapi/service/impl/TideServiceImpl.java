package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.GeoLocationDTO;
import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.mapper.TideMapper;
import com.projeto.mapi.model.GeoLocation;
import com.projeto.mapi.model.HourData;
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
    private final com.projeto.mapi.service.TabuaMareService tabuaMareService;

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
        
        TideTableResponseDTO dto = TideMapper.toDTO(results.get(0));
        
        // Adicionar altura da maré atual se as coordenadas estiverem disponíveis
        if (dto.getGeoLocations() != null && !dto.getGeoLocations().isEmpty()) {
            GeoLocationDTO geo = dto.getGeoLocations().get(0);
            try {
                double lat = Double.parseDouble(geo.getLat());
                double lng = Double.parseDouble(geo.getLng());
                dto.setCurrentTideHeight(getTideHeightAt(lat, lng, java.time.LocalDateTime.now()));
            } catch (Exception e) {
                log.warn("Erro ao parsear coordenadas para maré: {}", e.getMessage());
            }
        }
        
        return Optional.of(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTableResponseDTO> getTideTablesByState(String state, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByStateIgnoreCaseAndYear(state, year);
        
        List<TideTableResponseDTO> dtos;
        if (results.isEmpty() && state.equalsIgnoreCase("PE")) {
            log.info("Nenhum dado encontrado para o estado PE em {}. Tentando raspagem...", year);
            try {
                dtos = navyScraperService.scrapeAndIngestPernambuco(year);
            } catch (Exception e) {
                log.error("Erro ao buscar dados do estado PE: {}", e.getMessage());
                dtos = java.util.Collections.emptyList();
            }
        } else {
            dtos = results.stream().map(TideMapper::toDTO).collect(Collectors.toList());
        }

        // Popular altura atual
        dtos.forEach(dto -> {
            if (dto.getGeoLocations() != null && !dto.getGeoLocations().isEmpty()) {
                GeoLocationDTO geo = dto.getGeoLocations().get(0);
                try {
                    double lat = Double.parseDouble(geo.getLat());
                    double lng = Double.parseDouble(geo.getLng());
                    dto.setCurrentTideHeight(getTideHeightAt(lat, lng, java.time.LocalDateTime.now()));
                } catch (Exception e) {
                    log.warn("Erro ao parsear coordenadas: {}", e.getMessage());
                }
            }
        });
        
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TideTableResponseDTO> searchTideTablesByHarbor(String harborName, Integer year) {
        List<TideTableResponseDTO> dtos = tideTableRepository.findAllByHarborNameContainingIgnoreCaseAndYear(harborName, year)
                .stream()
                .map(TideMapper::toDTO)
                .collect(Collectors.toList());

        dtos.forEach(dto -> {
            if (dto.getGeoLocations() != null && !dto.getGeoLocations().isEmpty()) {
                GeoLocationDTO geo = dto.getGeoLocations().get(0);
                try {
                    double lat = Double.parseDouble(geo.getLat());
                    double lng = Double.parseDouble(geo.getLng());
                    dto.setCurrentTideHeight(getTideHeightAt(lat, lng, java.time.LocalDateTime.now()));
                } catch (Exception e) {
                    log.warn("Erro ao parsear coordenadas: {}", e.getMessage());
                }
            }
        });

        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllHarbors(Integer year) {
        return tideTableRepository.findDistinctHarborNamesByYear(year);
    }

    @Override
    @Transactional
    public Double getCurrentTideHeight(double latitude, double longitude) {
        return getTideHeightAt(latitude, longitude, java.time.LocalDateTime.now());
    }

    @Override
    @Transactional
    public Double getTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp) {
        log.info("Buscando altura da maré para lat: {}, lon: {} em {}", latitude, longitude, timestamp);
        
        int year = timestamp.getYear();
        
        // 1. Buscar todas as tabelas do ano
        List<TideTable> tables = tideTableRepository.findAllByYear(year);
        if (tables.isEmpty()) {
            log.info("Nenhuma tabela local para o ano {}. Usando TabuaMare API.", year);
            return tabuaMareService.getCurrentTideHeight(latitude, longitude);
        }

        // 2. Encontrar o porto mais próximo
        TideTable nearestTable = tables.stream()
                .filter(t -> t.getGeoLocations() != null && !t.getGeoLocations().isEmpty())
                .min(java.util.Comparator.comparingDouble(t -> {
                    GeoLocation geo = t.getGeoLocations().get(0);
                    try {
                        double lat = Double.parseDouble(geo.getLat());
                        double lng = Double.parseDouble(geo.getLng());
                        return calculateDistance(latitude, longitude, lat, lng);
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                }))
                .orElse(null);

        if (nearestTable == null || nearestTable.getMonths() == null) return null;

        // 3. Buscar o nível para a hora especificada
        int month = timestamp.getMonthValue();
        int day = timestamp.getDayOfMonth();
        int hour = timestamp.getHour();

        return nearestTable.getMonths().stream()
                .filter(m -> m.getMonth() != null && m.getMonth() == month)
                .flatMap(m -> m.getDays() != null ? m.getDays().stream() : java.util.stream.Stream.empty())
                .filter(d -> d.getDay() != null && d.getDay() == day)
                .flatMap(d -> d.getHours() != null ? d.getHours().stream() : java.util.stream.Stream.empty())
                .filter(h -> {
                    // Tratar formato de hora (ex: "0200" ou "2")
                    try {
                        String hStr = h.getHour().replaceAll("[^0-9]", "");
                        int hVal = Integer.parseInt(hStr);
                        // A Marinha usa HHmm, pegamos os primeiros dois dígitos para a hora
                        if (hStr.length() >= 3) hVal = hVal / 100;
                        return hVal == hour;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(h -> h.getLevel() != null ? (double) h.getLevel() : null)
                .findFirst()
                .orElseGet(() -> {
                    log.info("Dado local não encontrado. Buscando maré via TabuaMare API para lat: {}, lon: {}", latitude, longitude);
                    return tabuaMareService.getCurrentTideHeight(latitude, longitude);
                });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isTargetPernambucoPort(String name) {
        String upper = name.toUpperCase();
        return upper.contains("RECIFE") || 
               upper.contains("SUAPE") || 
               upper.contains("FERNANDO DE NORONHA");
    }
}
