package com.projeto.mapi.service.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.mapper.TideMapper;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.service.NavyScraperService;
import com.projeto.mapi.service.PdfConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavyScraperServiceImpl implements NavyScraperService {

    private final PdfConversionService pdfConversionService;
    private final com.projeto.mapi.config.AppProperties appProperties;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";

    static {
        disableSslVerification();
    }

    private String getTideUrl() {
        return appProperties.getNavy().getBaseUrl() + "/chm/tabuas-de-mare-6?page=0";
    }

    @Override
    public List<TideTableResponseDTO> scrapeAndIngestPernambuco(Integer year) throws IOException {
        int targetYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Iniciando raspagem automática para Pernambuco e ano {}", targetYear);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-setuid-sandbox"
                    )));
            
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(USER_AGENT)
                    .setViewportSize(1920, 1080)
                    .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                    )));

            Page page = context.newPage();
            log.info("Navegando para o site da Marinha: {}", getTideUrl());
            page.navigate(getTideUrl());
            
            try {
                page.waitForSelector("caption:has-text('Pernambuco')", new Page.WaitForSelectorOptions().setTimeout(45000));
                log.info("Site carregado com sucesso!");
            } catch (Exception e) {
                String title = page.title();
                log.warn("Seletor de Pernambuco não encontrado. Título da página: '{}'.", title);
                if (title.contains("Just a moment") || title.contains("Verificando")) {
                    log.error("O Cloudflare ainda está bloqueando o acesso. Tentando aguardar mais 15s...");
                    page.waitForTimeout(15000);
                }
            }

            String html = page.content();
            browser.close();
            
            Document doc = Jsoup.parse(html, appProperties.getNavy().getBaseUrl());
            return parseAndProcess(doc, targetYear);
            
        } catch (Exception e) {
            log.error("Erro na raspagem com Playwright: {}", e.getMessage());
            throw new IOException("Falha no processo automático: " + e.getMessage());
        }
    }

    @Override
    public List<TideTableResponseDTO> ingestFromHtml(String html, Integer year) throws IOException {
        int targetYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Ingestão via HTML manual para o ano {}", targetYear);
        Document doc = Jsoup.parse(html, appProperties.getNavy().getBaseUrl());
        return parseAndProcess(doc, targetYear);
    }

    private List<TideTableResponseDTO> parseAndProcess(Document doc, int targetYear) {
        List<TideTableResponseDTO> results = new ArrayList<>();
        Element captionPe = doc.selectFirst("caption:contains(Pernambuco)");

        if (captionPe == null) {
            log.warn("Seção de Pernambuco não encontrada no HTML.");
            return results;
        }

        Element tablePe = captionPe.parent();
        Elements rows = tablePe.select("tbody tr");
        log.info("Processando {} linhas da tabela de Pernambuco.", rows.size());

        for (Element row : rows) {
            Element titleCell = row.selectFirst("td.views-field-title");
            String rawPortoName = (titleCell != null) ? titleCell.text().trim() : "Desconhecido";

            if (!isTargetPorto(rawPortoName)) continue;

            Element pdfCell = row.selectFirst("td.views-field-field-ficha-mare");
            if (pdfCell == null) pdfCell = row.selectFirst("td.views-field-ficha-mare");

            if (pdfCell != null) {
                Element linkTag = pdfCell.selectFirst("a");
                if (linkTag != null && linkTag.hasAttr("href")) {
                    String fullPdfUrl = linkTag.absUrl("href");
                    log.info("Baixando PDF para: {} | URL: {}", rawPortoName, fullPdfUrl);
                    
                    try {
                        byte[] pdfBytes = downloadPdf(fullPdfUrl);
                        TideTableResponseDTO savedTable = pdfConversionService.convertAndSave(pdfBytes, rawPortoName + ".pdf", "PE", targetYear);
                        results.add(savedTable);
                    } catch (Exception e) {
                        log.error("Erro ao baixar/salvar PDF do porto {}: {}", rawPortoName, e.getMessage());
                    }
                }
            }
        }
        return results;
    }

    private boolean isTargetPorto(String name) {
        String upper = name.toUpperCase();
        return upper.contains("RECIFE") || upper.contains("SUAPE") || upper.contains("FERNANDO DE NORONHA");
    }

    private byte[] downloadPdf(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .maxBodySize(0)
                .timeout(60000)
                .execute()
                .bodyAsBytes();
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }};
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.error("Erro SSL Bypass: {}", e.getMessage());
        }
    }
}
