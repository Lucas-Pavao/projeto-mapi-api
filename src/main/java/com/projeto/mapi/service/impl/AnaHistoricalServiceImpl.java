package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.AnaHistoricalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnaHistoricalServiceImpl implements AnaHistoricalService {

    private final SensorDataRepository sensorDataRepository;
    private final FloodPointRepository floodPointRepository;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://telemetriaws1.ana.gov.br/ServiceANA.asmx")
            .build();

    @Override
    public void ingestHistoricalSensorData(String stationCode, int years) {
        if (stationCode == null || stationCode.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < years; i++) {
            LocalDateTime end = now.minusYears(i);
            LocalDateTime start = end.minusYears(1).plusDays(1);
            
            String startDateStr = start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String endDateStr = end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

            log.info("Buscando levas da ANA para estação {} (Ano {}): {} a {}", stationCode, (now.getYear() - i), startDateStr, endDateStr);

            try {
                String xml = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/DadosHidrometereologicos")
                                .queryParam("codEstacao", stationCode)
                                .queryParam("dataInicio", startDateStr)
                                .queryParam("dataFim", endDateStr)
                                .build())
                        .retrieve()
                        .body(String.class);

                if (xml != null && !xml.contains("Nenhum registro encontrado")) {
                    parseAndSaveAnaXml(xml, stationCode);
                }
            } catch (Exception e) {
                log.error("Erro ao buscar ano {} da ANA para {}: {}", (now.getYear() - i), stationCode, e.getMessage());
            }
            
            // Pequena pausa para evitar bloqueio
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    private void parseAndSaveAnaXml(String xml, String stationCode) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

        NodeList nodes = doc.getElementsByTagName("DadosHidrometereologicos");
        List<SensorData> batch = new ArrayList<>();

        // Identifica se a estação pertence a um ponto monitorado
        String finalSensorId = stationCode;
        Optional<FloodPoint> fp = floodPointRepository.findByPluviometerStationId(stationCode);
        if (fp.isEmpty()) fp = floodPointRepository.findByRiverLevelStationId(stationCode);
        
        if (fp.isPresent()) {
            finalSensorId = fp.get().getSlug();
            log.info("---- Mapeando estação ANA {} para o ponto monitorado {}", stationCode, finalSensorId);
        }

        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            
            String dateStr = getTagValue("DataHora", element);
            String rainStr = getTagValue("Chuva", element);
            String levelStr = getTagValue("Nivel", element);
            String flowStr = getTagValue("Vazao", element);

            if (dateStr == null || dateStr.isBlank()) continue;

            LocalDateTime timestamp;
            try {
                if (dateStr.contains("T")) {
                    timestamp = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
                } else if (dateStr.contains("/")) {
                    timestamp = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                } else {
                    timestamp = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
            } catch (Exception e) {
                log.warn("Falha ao parsear data ANA: {}. Pulando registro.", dateStr);
                continue;
            }
            
            // Ajuste de Fuso Horário: ANA/CEMADEN enviam dados em UTC.
            // Conforme diretriz técnica: Hora Local (Recife) = UTC - 3.
            timestamp = timestamp.minusHours(3);
            
            // Evitar duplicatas
            if (sensorDataRepository.findBySensorIdAndTimestamp(finalSensorId, timestamp).isPresent()) continue;

            SensorData data = SensorData.builder()
                    .sensorId(finalSensorId)
                    .timestamp(timestamp)
                    .code(stationCode)
                    .source("ANA_HISTORICAL")
                    .build();

            if (rainStr != null && !rainStr.isEmpty()) {
                data.setAccumulatedPrecipitation(Double.parseDouble(rainStr));
                data.setUnit("mm");
            }
            if (levelStr != null && !levelStr.isEmpty()) {
                data.setWaterLevel(Double.parseDouble(levelStr));
                data.setUnit("m");
            }
            if (flowStr != null && !flowStr.isEmpty()) {
                data.setFlowRate(Double.parseDouble(flowStr));
            }

            batch.add(data);
            if (batch.size() >= 500) {
                sensorDataRepository.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            sensorDataRepository.saveAll(batch);
        }
        log.info("Persistidos {} registros históricos da ANA para estação {}", nodes.getLength(), stationCode);
    }

    private String getTagValue(String tag, Element element) {
        NodeList nl = element.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).getFirstChild() != null) {
            return nl.item(0).getFirstChild().getNodeValue();
        }
        return null;
    }
}
