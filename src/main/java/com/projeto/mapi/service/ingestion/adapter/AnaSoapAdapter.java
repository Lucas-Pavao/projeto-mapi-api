package com.projeto.mapi.service.ingestion.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fonte ANA_SOAP: fuso UTC-3 verificado em F2-DADOS-10 (antes: UNVERIFIED, quarentena TZ_UNVERIFIED).
 *
 * <p>Ao vivo, o coletor empurra envelopes ({@link SensorEnvelopeAdapter}). Para a carga histórica, este adaptador
 * lê o serviço legado público da ANA (sem credencial) {@code DadosHidrometeorologicos} por estação e mês, e grava
 * cada linha {@code <DadosHidrometereologicos>} como um registro bruto, sem tocar no fuso (o rótulo
 * {@code DataHora} vai como veio e o fuso declarado segue UNVERIFIED). O Hidro-WebService autenticado
 * ({@code ANA_IDENTIFICADOR}/{@code ANA_SENHA}) não é usado: o legado entrega a mesma série de 15 min sem credencial.
 *
 * <p>Como o fuso é desconhecido, {@code [from, to)} só escolhe os meses a baixar (datas de calendário em UTC, com o
 * mês inteiro): a janela não pode ser aplicada ao instante de origem antes da verificação.
 */
@Component
@RequiredArgsConstructor
public class AnaSoapAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "ANA_SOAP";
    static final String ORIGEM_LEGADO = "ServiceANA.DadosHidrometeorologicos";
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("\\d{8}");

    private final AnaLegacyClient client;
    private final IngestionProperties props;
    private final FloodPointRepository floodPoints;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public String source() {
        return SOURCE;
    }

    /** Rótulo do legado em UTC-3 (F2-DADOS-10, ver {@code V14__tz_verification_ana.sql}). */
    @Override
    public String declaredTz() {
        return "America/Recife";
    }

    /**
     * O legado entrega {@code Nivel} em centímetros (ex.: 239,00 = 2,39 m); o catálogo de {@code water_level} é em
     * metros. O bruto guarda o número como veio; a conversão é daqui (e o qualificador {@code UNIT_UNVERIFIED} sai,
     * pois a unidade deste endpoint é conhecida). Envelopes de outra origem (coletor ao vivo) não são convertidos.
     */
    @Override
    public List<DraftValue> extract(RawRecord record) {
        boolean legado = ORIGEM_LEGADO.equals(record.payload().path("origem").asText());
        List<DraftValue> out = new ArrayList<>();
        for (DraftValue d : super.extract(record)) {
            if (legado && "water_level".equals(d.variable()) && d.value() != null) {
                out.add(new DraftValue(d.variable(), d.resolution(), d.value() / 100.0, d.unit(), null));
            } else {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public String parserVersion() {
        return "ana-soap/3";
    }

    /** Estações da carga: lista configurada mais os pluviômetros de 8 dígitos ligados aos pontos. */
    Set<String> stationCodes() {
        Set<String> codes = new LinkedHashSet<>(props.getAnaHistoricalStations());
        for (FloodPoint p : floodPoints.findAll()) {
            if (p.getPluviometerStationIds() == null) continue;
            p.getPluviometerStationIds().stream().filter(c -> CODE.matcher(c).matches()).sorted().forEach(codes::add);
        }
        return codes;
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        LocalDate first = from.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate last = to.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        if (last.isAfter(today)) last = today;

        Set<String> codes = stationCodes();
        List<StationInfo> inventory = inventoryOf(codes);
        boolean firstBatch = true;
        for (String code : codes) {
            for (LocalDate m = first; !m.isAfter(last); m = m.plusMonths(1)) {
                LocalDate end = m.plusMonths(1).minusDays(1);
                AnaLegacyClient.Response resp = client.series(code, m, end.isAfter(last) ? last : end);
                List<RawRecord> records = parseSeries(resp.body(), code);
                ObjectNode req = mapper.createObjectNode();
                req.put("url", resp.url());
                req.put("sha256", Hashing.sha256(resp.body()));
                req.put("station", code);
                req.put("rows", records.size());
                sink.accept(new FetchedBatch(records, firstBatch ? inventory : List.of(), req));
                firstBatch = false;
            }
        }
    }

    /** Metadados das estações pedidas (nome, coordenadas) a partir do inventário; ausente = só o código. */
    private List<StationInfo> inventoryOf(Set<String> codes) {
        List<StationInfo> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        try {
            Document doc = parse(client.inventory().body());
            NodeList rows = doc.getElementsByTagName("Table");
            for (int i = 0; i < rows.getLength(); i++) {
                Element e = (Element) rows.item(i);
                String code = text(e, "CodEstacao");
                if (code == null || !codes.contains(code) || !seen.add(code)) continue; // inventário repete a estação por origem
                ObjectNode attrs = mapper.createObjectNode();
                attrs.put("operadora", text(e, "Operadora"));
                attrs.put("rio", text(e, "NomeRio"));
                out.add(new StationInfo(SOURCE, code, text(e, "NomeEstacao"), number(text(e, "Latitude")),
                        number(text(e, "Longitude")), text(e, "Municipio-UF"), attrs));
            }
        } catch (SourceUnavailableException ignored) {
            // inventário é só metadado: a carga segue sem ele
        }
        return out;
    }

    /** Uma linha de {@code DadosHidrometereologicos} vira um registro bruto (estação x rótulo de hora). */
    List<RawRecord> parseSeries(String xml, String stationCode) {
        Document doc = parse(xml);
        NodeList nodes = doc.getElementsByTagName("DadosHidrometereologicos");
        Instant collectedAt = clock.instant();
        List<RawRecord> out = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String label = text(e, "DataHora");
            if (label == null) continue;
            ObjectNode env = mapper.createObjectNode();
            env.put("codigo", stationCode);
            env.put("Data_Hora_Medicao", label);
            putIfPresent(env, "Chuva_Adotada", text(e, "Chuva"));
            putIfPresent(env, "Cota_Adotada", text(e, "Nivel"));
            putIfPresent(env, "Vazao", text(e, "Vazao"));
            env.put("origem", ORIGEM_LEGADO);
            out.add(toRaw(env, collectedAt));
        }
        return out;
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new SourceUnavailableException("ANA legado: XML inválido: " + e.getMessage(), e);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0 || nl.item(0).getFirstChild() == null) return null;
        String v = nl.item(0).getFirstChild().getNodeValue();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void putIfPresent(ObjectNode n, String key, String value) {
        if (value != null) n.put(key, value);
    }

    private static Double number(String s) {
        try {
            return s == null ? null : Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
