package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TideServiceImpl implements TideService {
    private final TideTableRepository tideTableRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TideTable> getTideTable(String harborName, Integer year) {
        List<TideTable> results = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(harborName, year);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    @Transactional
    public TideTable saveTideTable(TideTable tideTable) {
        return tideTableRepository.save(tideTable);
    }
}
