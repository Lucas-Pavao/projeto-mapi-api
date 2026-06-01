package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.ApacHistoricalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApacHistoricalServiceImpl implements ApacHistoricalService {

    private final SensorDataRepository sensorDataRepository;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://dados.apac.pe.gov.br:41120")
            .build();

    @Override
    public void ingestHistoricalRainfall(String stationCode, int year) {
        log.info("Iniciando coleta histórica APAC para estação {} e ano {}", stationCode, year);
        
        // A APAC fornece dados históricos muitas vezes em arquivos consolidados por mês ou ano.
        // Como o site é um diretório, implementamos uma lógica de busca por levas.
        
        for (int month = 1; month <= 12; month++) {
            try {
                processMonth(stationCode, year, month);
                // Sleep para não sobrecarregar o servidor da APAC (conforme pedido)
                Thread.sleep(2000); 
            } catch (Exception e) {
                log.warn("Falha ao processar mês {}/{} para APAC: {}", month, year, e.getMessage());
            }
        }
    }

    private void processMonth(String stationCode, int year, int month) throws Exception {
        // Exemplo de padrão de URL comum em portais de dados (ajustável conforme a estrutura real observada)
        String url = String.format("/boletins/historico-pluviometrico/%d/%02d/%s.csv", year, month, stationCode);
        
        log.debug("Buscando CSV APAC: {}", url);
        
        try {
            String csvContent = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (csvContent != null && !csvContent.isBlank()) {
                parseAndSaveCsv(csvContent, stationCode);
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // Silencioso se não houver boletim para aquele mês específico
        }
    }

    private void parseAndSaveCsv(String content, String stationCode) throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setDelimiter(';')
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        List<SensorData> batch = new ArrayList<>();
        try (CSVParser parser = new CSVParser(new StringReader(content), format)) {
            for (CSVRecord row : parser) {
                try {
                    String dateStr = row.get("Data");
                    String rainStr = row.get("Precipitacao").replace(",", ".");
                    
                    LocalDateTime ts = LocalDateTime.parse(dateStr + "T00:00:00");
                    
                    if (sensorDataRepository.findBySensorIdAndTimestamp(stationCode, ts).isEmpty()) {
                        batch.add(SensorData.builder()
                                .sensorId(stationCode)
                                .timestamp(ts)
                                .accumulatedPrecipitation(Double.parseDouble(rainStr))
                                .unit("mm")
                                .source("APAC_HISTORICAL")
                                .build());
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!batch.isEmpty()) {
            sensorDataRepository.saveAll(batch);
            log.info("Persistidos {} registros da APAC para estação {}", batch.size(), stationCode);
        }
    }
}
