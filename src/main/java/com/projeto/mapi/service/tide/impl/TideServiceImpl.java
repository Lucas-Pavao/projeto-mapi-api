package com.projeto.mapi.service.tide.impl;

import com.projeto.mapi.dto.GeoLocationDTO;
import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.mapper.TideMapper;
import com.projeto.mapi.model.GeoLocation;
import com.projeto.mapi.model.HourData;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.ingestion.adapter.tide.TideConstants;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideServiceImpl implements TideService {
    private final TideTableRepository tideTableRepository;
    private final com.projeto.mapi.service.tide.TabuaMareService tabuaMareService;

    @Override
    @Transactional
    public Optional<TideTableResponseDTO> getTideTable(String harborName, Integer year) {
        // A tábua é gravada pelo nome do porto; o resto da API (e o front) chama o porto pelo id da TabuaMare.
        String name = TideConstants.STATION.equalsIgnoreCase(harborName) ? TideConstants.HARBOR_NAME : harborName;
        List<TideTable> results = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(name, year);
        
        if (results.isEmpty()) {
            log.info("Dados não encontrados para {} em {}.", harborName, year);
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
    public Page<TideTableResponseDTO> getTideTablesByState(String state, Integer year, Pageable pageable) {
        Page<TideTableResponseDTO> page = tideTableRepository.findAllByStateIgnoreCaseAndYear(state, year, pageable)
                .map(TideMapper::toDTO);

        page.forEach(this::populateCurrentTideHeight);
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TideTableResponseDTO> searchTideTablesByHarbor(String harborName, Integer year, Pageable pageable) {
        Page<TideTableResponseDTO> page = tideTableRepository.findAllByHarborNameContainingIgnoreCaseAndYear(harborName, year, pageable)
                .map(TideMapper::toDTO);

        page.forEach(this::populateCurrentTideHeight);
        return page;
    }

    private void populateCurrentTideHeight(TideTableResponseDTO dto) {
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
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllHarbors(Integer year) {
        return tideTableRepository.findDistinctHarborNamesByYear(year);
    }

    // F2-BACK-19: getCurrentTideHeight chama getTideHeightAt por auto-invocação (sem passar pelo proxy), então
    // o @Transactional de getTideHeightAt não valia e as coleções LAZY (geoLocations, months, days, hours)
    // estouravam LazyInitializationException em todo ciclo de predição. A transação de leitura fica aqui,
    // no ponto de entrada; o mapeamento das entidades continua LAZY.
    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "tideDataLocal", key = "T(java.lang.Math).round(#latitude * 100) / 100.0 + '-' + T(java.lang.Math).round(#longitude * 100) / 100.0")
    public Double getCurrentTideHeight(double latitude, double longitude) {
        return getTideHeightAt(latitude, longitude, java.time.LocalDateTime.now());
    }

    @Override
    @Transactional
    public Double getTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp) {
        return tideAt(latitude, longitude, timestamp, true);
    }

    /** F2-BACK-17: só a tábua local; nunca chama a API externa (export reprodutível e sem 1.800 chamadas HTTP). */
    @Override
    @Transactional(readOnly = true)
    public Double getStoredTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp) {
        return tideAt(latitude, longitude, timestamp, false);
    }

    private Double tideAt(double latitude, double longitude, java.time.LocalDateTime timestamp, boolean external) {
        log.debug("Buscando altura da maré para lat: {}, lon: {} em {}", latitude, longitude, timestamp);
        
        int year = timestamp.getYear();
        
        // 1. Buscar todas as tabelas do ano
        List<TideTable> tables = tideTableRepository.findAllByYear(year);
        if (tables.isEmpty()) {
            log.debug("Nenhuma tabela local para o ano {}. Buscando via TabuaMare API.", year);
            return external ? tabuaMareService.getTideHeightAt(latitude, longitude, timestamp) : null;
        }

        // 2. Encontrar o porto mais próximo
        TideTable nearestTable = tables.stream()
                .filter(t -> t.getGeoLocations() != null && !t.getGeoLocations().isEmpty())
                .min(java.util.Comparator.comparingDouble(t -> {
                    GeoLocation geo = t.getGeoLocations().get(0);
                    try {
                        double lat = Double.parseDouble(geo.getLat());
                        double lng = Double.parseDouble(geo.getLng());
                        return GeoUtils.calculateDistance(latitude, longitude, lat, lng);
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                }))
                .orElse(null);

        if (nearestTable == null || nearestTable.getMonths() == null) return null;

        // 3. Encontrar as horas cadastradas do dia (a tábua só traz os picos de maré alta/baixa,
        // ~4 por dia — não uma entrada por hora) e interpolar entre os dois picos ao redor do
        // horário pedido, exatamente como TabuaMareServiceImpl.getTideHeightAt já faz para a API
        // externa. Um filtro de "hora exata" aqui nunca acertaria, já que a hora pedida quase
        // sempre cai FORA dos poucos horários de pico cadastrados.
        int month = timestamp.getMonthValue();
        int day = timestamp.getDayOfMonth();

        List<HourData> hours = nearestTable.getMonths().stream()
                .filter(m -> m.getMonth() != null && m.getMonth() == month)
                .flatMap(m -> m.getDays() != null ? m.getDays().stream() : java.util.stream.Stream.empty())
                .filter(d -> d.getDay() != null && d.getDay() == day)
                .flatMap(d -> d.getHours() != null ? d.getHours().stream() : java.util.stream.Stream.empty())
                .filter(h -> h.getHour() != null && h.getLevel() != null)
                .sorted(java.util.Comparator.comparingInt(this::minutesOfDay))
                .toList();

        Double interpolated = interpolateLevel(hours, timestamp);
        if (interpolated != null) {
            return interpolated;
        }

        log.debug("Dado local não encontrado. Buscando maré via TabuaMare API para lat: {}, lon: {} em {}", latitude, longitude, timestamp);
        return external ? tabuaMareService.getTideHeightAt(latitude, longitude, timestamp) : null;
    }

    private Double interpolateLevel(List<HourData> sortedHours, java.time.LocalDateTime timestamp) {
        if (sortedHours.isEmpty()) return null;

        int targetMinutes = timestamp.getHour() * 60 + timestamp.getMinute();
        HourData prev = null;
        HourData next = null;

        for (HourData h : sortedHours) {
            if (minutesOfDay(h) <= targetMinutes) {
                prev = h;
            } else {
                next = h;
                break;
            }
        }

        if (prev != null && next != null) {
            double h1 = prev.getLevel();
            double h2 = next.getLevel();
            double fraction = (double) (targetMinutes - minutesOfDay(prev)) / (minutesOfDay(next) - minutesOfDay(prev));
            double result = (h1 + h2) / 2.0 + (h1 - h2) / 2.0 * Math.cos(Math.PI * fraction);
            return Math.round(result * 100.0) / 100.0;
        }

        // Só temos um lado (início ou fim do dia cadastrado): usa o pico mais próximo.
        HourData nearest = sortedHours.stream()
                .min(java.util.Comparator.comparingInt(h -> Math.abs(minutesOfDay(h) - targetMinutes)))
                .orElse(null);
        return nearest != null ? (double) nearest.getLevel() : null;
    }

    // Formato real confirmado ao vivo na API (TabuaMare/DHN): "HH:mm:ss", ex. "00:42:00". Mantém
    // fallback pro formato compacto "HHmm"/"H" só por segurança, caso a fonte de dados mude.
    private int minutesOfDay(HourData h) {
        String raw = h.getHour();
        try {
            if (raw.contains(":")) {
                String[] parts = raw.split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            String hStr = raw.replaceAll("[^0-9]", "");
            int hVal = Integer.parseInt(hStr);
            if (hStr.length() >= 3) return (hVal / 100) * 60 + (hVal % 100);
            return hVal * 60;
        } catch (Exception e) {
            return 0;
        }
    }
}
