package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TideServiceImpl implements TideService {
    private final TideTableRepository tideTableRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TideTable> getTideTable(String harborName, Integer year) {
        return tideTableRepository.findByHarborNameAndYear(harborName, year);
    }

    @Override
    @Transactional
    public TideTable saveTideTable(TideTable tideTable) {
        return tideTableRepository.save(tideTable);
    }
}
