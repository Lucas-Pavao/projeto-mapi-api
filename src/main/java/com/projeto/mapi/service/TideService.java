package com.projeto.mapi.service;

import com.projeto.mapi.model.TideTable;
import java.util.List;
import java.util.Optional;

public interface TideService {
    Optional<TideTable> getTideTable(String harborName, Integer year);
    List<TideTable> getTideTablesByState(String state, Integer year);
    List<TideTable> searchTideTablesByHarbor(String harborName, Integer year);
    List<String> getAllHarbors(Integer year);
    TideTable saveTideTable(TideTable tideTable);
}
