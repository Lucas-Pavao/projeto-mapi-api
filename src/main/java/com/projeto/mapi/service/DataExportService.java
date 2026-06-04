package com.projeto.mapi.service;

import com.projeto.mapi.dto.UnifiedDataDTO;
import java.io.OutputStream;
import java.util.List;

public interface DataExportService {
    List<UnifiedDataDTO> exportUnifiedData(String slug, int days);
    List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days);
    List<UnifiedDataDTO> exportAllPointsData(int days);
    String generateCsv(List<UnifiedDataDTO> data);
    void streamUnifiedDataToOutputStream(OutputStream outputStream, int days);
}
