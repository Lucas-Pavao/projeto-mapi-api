package com.projeto.mapi.service.tide.impl;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.ingestion.adapter.tide.TideConstants;
import com.projeto.mapi.service.tide.TabuaMareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TideServiceImplTest {

    @Mock
    private TideTableRepository tideTableRepository;

    @Mock
    private TabuaMareService tabuaMareService;

    @InjectMocks
    private TideServiceImpl service;

    @Test
    void idDoPortoBuscaPeloNomeGravado() {
        TideTable table = new TideTable();
        table.setHarborName(TideConstants.HARBOR_NAME);
        table.setYear(2026);
        when(tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(TideConstants.HARBOR_NAME, 2026)).thenReturn(List.of(table));

        assertTrue(service.getTideTable("pe02", 2026).isPresent());
        verify(tideTableRepository).findAllByHarborNameIgnoreCaseAndYear(TideConstants.HARBOR_NAME, 2026);
    }

    @Test
    void nomeDoPortoSegueComoAntes() {
        when(tideTableRepository.findAllByHarborNameIgnoreCaseAndYear("Outro porto", 2026)).thenReturn(List.of());

        assertTrue(service.getTideTable("Outro porto", 2026).isEmpty());
    }
}
