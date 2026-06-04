package com.projeto.mapi.service;

import com.projeto.mapi.dto.TabuaMareResponse;
import java.util.List;

public interface TabuaMareService {
    TabuaMareResponse<List<String>> getStates();
    TabuaMareResponse<List<Object>> getHarborNames(String state);
    TabuaMareResponse<List<Object>> getHarbors(String ids);
    TabuaMareResponse<List<Object>> getTideTable(String harbor, Integer month, String days);
    TabuaMareResponse<Object> getNearestHarbor(String latLng);
    Double getCurrentTideHeight(double latitude, double longitude);
    Double getTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp);
}
