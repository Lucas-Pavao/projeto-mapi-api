package com.projeto.mapi.service.ingestion.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base dos adaptadores das fontes de sensores ao vivo (ANA, APAC meteorologia24h, Cemaden). O coletor legado já
 * monta um "envelope" JSON por leitura; o pipeline grava esse envelope no bruto e extrai os valores daqui.
 *
 * <p>O fuso de origem é declarado {@code UNVERIFIED} (decisão do autor, ADR 0006 seção 4): a validação lê o fuso
 * efetivo de {@code source_registry} (UNVERIFIED) e manda todo registro para a quarentena {@code TZ_UNVERIFIED}
 * até a verificação F2-DADOS-07. Nenhuma observação é consolidada antes disso. Não há reconsulta (REFETCH): a
 * fonte só entrega o dado corrente ({@code IngestionPipelineService.executePushed}); o REVALIDATE funciona sobre o bruto.
 *
 * <p>Mapeamento de unidades e janelas é provisório e também depende de F2-DADOS-07: precipitação acumulada tem
 * janela desconhecida (resolução EVENT, qualificador {@code WINDOW_UNVERIFIED}) e o nível d'água não declara
 * unidade (o legado não a registra).
 */
public abstract class SensorEnvelopeAdapter implements SourceAdapter {

    private static final String[] TS_KEYS = {"timestamp_coleta", "data_hora", "Data_Hora_Medicao",
            "data_hora_medicao", "horario"};

    @Override
    public String declaredTz() {
        return "UNVERIFIED";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        throw new SourceUnavailableException("Fonte " + source() + " é ao vivo: sem reconsulta (REFETCH) por intervalo; "
                + "use REVALIDATE sobre o bruto");
    }

    /**
     * Lote a partir de envelopes já montados (fontes cujo endpoint devolve uma janela rolante de ~24 h). Estações
     * saem do próprio envelope (código, nome, coordenadas, município).
     */
    protected FetchedBatch snapshotBatch(List<JsonNode> envelopes, Instant collectedAt, JsonNode requestInfo) {
        List<RawRecord> records = new ArrayList<>();
        java.util.Map<String, StationInfo> stations = new java.util.LinkedHashMap<>();
        for (JsonNode e : envelopes) {
            RawRecord r = toRaw(e, collectedAt);
            records.add(r);
            if (!r.stationId().isEmpty()) {
                stations.putIfAbsent(r.stationId(), new StationInfo(source(), r.stationId(), text(e, "estacao_nome"),
                        e.hasNonNull("latitude") ? e.get("latitude").asDouble() : null,
                        e.hasNonNull("longitude") ? e.get("longitude").asDouble() : null, text(e, "municipio"), null));
            }
        }
        return new FetchedBatch(records, new ArrayList<>(stations.values()), requestInfo);
    }

    /** Registro de origem do envelope: estação = código GMMC/ANA, senão o id_sensor do legado. */
    public RawRecord toRaw(JsonNode envelope, Instant collectedAt) {
        String station = text(envelope, "codigo");
        if (station.isEmpty()) station = text(envelope, "id_sensor");
        String ts = "";
        for (String k : TS_KEYS) {
            if (!text(envelope, k).isEmpty()) {
                ts = text(envelope, k);
                break;
            }
        }
        String norm = com.projeto.mapi.service.ingestion.validation.TimestampNormalizer.normalize(ts);
        return new RawRecord(source(), station, collectedAt, ts.length() > 80 ? ts.substring(0, 80) : ts, norm,
                declaredTz(), envelope);
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode e = record.payload();
        List<DraftValue> out = new ArrayList<>();
        add(out, "temperature", e, "temperatura_ar", null);
        add(out, "humidity", e, "umidade_relativa", null);
        add(out, "pressure", e, "pressao_atmosferica", null);
        add(out, "wind_speed", e, "velocidade_vento", null);
        add(out, "solar_radiation", e, "radiacao_solar", null);
        String rain = firstPresent(e, "precipitacao_acumulada", "chuva_acumulada", "Chuva_Adotada");
        if (rain != null) add(out, "precipitation", e, rain, "WINDOW_UNVERIFIED");
        String level = firstPresent(e, "Cota_Adotada", "nivel_rio");
        if (level != null) add(out, "water_level", e, level, "UNIT_UNVERIFIED");
        return out;
    }

    private static void add(List<DraftValue> out, String variable, JsonNode e, String key, String qualifier) {
        JsonNode v = e.path(key);
        if (v.isNumber()) {
            out.add(new DraftValue(variable, "EVENT", v.asDouble(), null, qualifier));
        } else if (v.isTextual()) {
            try {
                out.add(new DraftValue(variable, "EVENT", Double.parseDouble(v.asText().trim().replace(',', '.')),
                        null, qualifier));
            } catch (NumberFormatException ignored) {
                // texto não numérico: tratado como lacuna
            }
        }
    }

    private static String firstPresent(JsonNode e, String... keys) {
        for (String k : keys) if (e.hasNonNull(k)) return k;
        return null;
    }

    private static String text(JsonNode e, String key) {
        return e.hasNonNull(key) ? e.get(key).asText().trim() : "";
    }
}
