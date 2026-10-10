package com.projeto.mapi.service.export;

import com.projeto.mapi.dto.UnifiedDataDTO;
import java.io.OutputStream;
import java.util.List;

public interface DataExportService {
    List<UnifiedDataDTO> exportUnifiedData(String slug, int days);
    List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days);
    List<UnifiedDataDTO> exportUnifiedData(String slug, int days, java.time.LocalDateTime end);
    List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days, java.time.LocalDateTime end);

    /** CSV (colunas atuais) e manifesto; slug "all" = todos os pontos. {@code end} nulo = hora atual. */
    CsvExport exportCsv(String slug, int days, java.time.LocalDateTime end);

    record CsvExport(String csv, com.projeto.mapi.dto.ExportManifestDTO manifest) {}

    List<UnifiedDataDTO> exportAllPointsData(int days);
    List<UnifiedDataDTO> exportAllPointsDataWithAccumulated(int days);
    String generateCsv(List<UnifiedDataDTO> data);
    void streamUnifiedDataToOutputStream(OutputStream outputStream, int days);
}
