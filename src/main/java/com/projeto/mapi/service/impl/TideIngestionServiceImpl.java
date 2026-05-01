package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.service.PdfConversionService;
import com.projeto.mapi.service.TideIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideIngestionServiceImpl implements TideIngestionService {

    private final PdfConversionService pdfConversionService;

    private static final String NAVY_PORTAL_URL = "https://www.marinha.mil.br/chm/tabuas-de-mare-6";
    private static final String BASE_DOMAIN = "https://www.marinha.mil.br";

    @Override
    public TideTable ingestRecifeTide(Integer year) throws Exception {
        log.info("Iniciando ingestão automática da tábua de maré do Recife para o ano {}", year);

        String pdfUrl = findRecifePdfUrl();
        if (pdfUrl == null) {
            throw new RuntimeException("Link do PDF do Recife não encontrado no portal da Marinha.");
        }

        log.info("PDF encontrado: {}", pdfUrl);
        byte[] pdfContent = downloadPdf(pdfUrl);

        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                "tide_recife_" + year + ".pdf",
                "application/pdf",
                pdfContent
        );

        return pdfConversionService.convertAndSave(multipartFile, "PE", year);
    }

    private String findRecifePdfUrl() throws Exception {
        byte[] htmlContent = downloadPdf(NAVY_PORTAL_URL);
        String html = new String(htmlContent);
        
        Document doc = Jsoup.parse(html, BASE_DOMAIN);
        Elements links = doc.select("a[href$=.pdf]");

        for (Element link : links) {
            String text = link.text().toUpperCase();
            String href = link.attr("href");

            if (text.contains("RECIFE")) {
                if (href.startsWith("http")) {
                    return href;
                } else {
                    return BASE_DOMAIN + (href.startsWith("/") ? "" : "/") + href;
                }
            }
        }
        return null;
    }

    private byte[] downloadPdf(String url) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }
}
