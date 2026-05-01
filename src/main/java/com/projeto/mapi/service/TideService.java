package com.projeto.mapi.service;

import com.projeto.mapi.model.TideTable;
import java.util.Optional;

public interface TideService {
    Optional<TideTable> getTideTable(String harborName, Integer year);
    TideTable saveTideTable(TideTable tideTable);
}
