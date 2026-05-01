package com.projeto.mapi.service;

import com.projeto.mapi.model.TideTable;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface PdfConversionService {
    TideTable convertAndSave(MultipartFile file, String state, Integer year) throws IOException;
}
