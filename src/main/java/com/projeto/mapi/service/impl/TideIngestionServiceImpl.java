package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.service.PdfConversionService;
import com.projeto.mapi.service.TideIngestionService;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideIngestionServiceImpl implements TideIngestionService {

    private final PdfConversionService pdfConversionService;

    private static final String LOCAL_PDF_DIR = "src/exemplos pdf";

    @Override
    public TideTableResponseDTO ingestRecifeTide(Integer year) throws Exception {
        log.info("Iniciando ingestão manual (local) para Recife e ano {}", year);

        File dir = new File(LOCAL_PDF_DIR);
        if (!dir.exists()) {
            throw new RuntimeException("Diretório de exemplos locais não encontrado: " + LOCAL_PDF_DIR);
        }

        File[] files = dir.listFiles((d, name) -> 
            name.toUpperCase().contains("RECIFE") && name.endsWith(".pdf"));

        if (files != null && files.length > 0) {
            File file = files[0];
            log.info("Processando arquivo local: {}", file.getName());
            
            byte[] content = Files.readAllBytes(file.toPath());
            MultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    file.getName(),
                    "application/pdf",
                    content
            );
            
            return pdfConversionService.convertAndSave(multipartFile, "PE", year);
        }

        throw new RuntimeException("Nenhum PDF de Recife encontrado localmente em " + LOCAL_PDF_DIR);
    }
}
