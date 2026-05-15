package com.projeto.mapi.service;

import com.projeto.mapi.dto.TideTableResponseDTO;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface PdfConversionService {
    TideTableResponseDTO convertAndSave(MultipartFile file, String state, Integer year) throws IOException;
    TideTableResponseDTO convertAndSave(byte[] pdfBytes, String filename, String state, Integer year) throws IOException;
}
