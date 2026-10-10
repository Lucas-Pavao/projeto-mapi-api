package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import com.projeto.mapi.service.ingestion.validation.TsUnparseableException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regras puras do rótulo CKAN (ADR 0006 seções 4 e 6; decisão do autor: rótulo positivo só "alagamento").
 *
 * <p>O dataset "Registro de atendimentos da Defesa Civil" NÃO traz código Cobrade (campos verificados em
 * 2026-10-10 via datastore_search: _id, Regional, Ano, Mês, Data, Ocorrencia, Solicitacao, Endereco, Bairro,
 * Localidade, Grau_de_Risco, Data_da_Acao, Tipo_da_Acao, Quantidade, Altura, Largura, Area_m). O filtro é, portanto,
 * por TEXTO no campo {@code Ocorrencia} (tipo da ocorrência; valor observado "Alagamentos"). {@code Solicitacao}
 * não entra no filtro (valores como "Vistoria Alagado" descrevem o serviço, não o tipo). Limitação registrada.
 */
public final class OccurrenceClassifier {

    /** Termos que excluem o registro mesmo que contenha um termo aceito (decisão do autor). */
    static final List<String> EXCLUDED_TEXTS = List.of("deslizamento", "enxurrada", "inundacao");

    private OccurrenceClassifier() {}

    public static OccurrenceClassification classify(Map<String, Object> fields, LabelFilterConfig filter, String tz) {
        String type = text(fields, "Ocorrencia");
        String folded = fold(type);
        String address = text(fields, "Endereco");
        String neighborhood = text(fields, "Bairro");
        for (String ex : EXCLUDED_TEXTS) {
            if (folded.contains(ex)) {
                return new OccurrenceClassification(OccurrenceClassification.Status.EXCLUDED_BY_FILTER, null, null,
                        null, null, address, neighborhood, "excluído por '" + ex + "'");
            }
        }
        String matched = null;
        for (String accepted : filter.acceptedTexts()) {
            if (folded.contains(fold(accepted))) {
                matched = accepted;
                break;
            }
        }
        if (matched == null) {
            return new OccurrenceClassification(OccurrenceClassification.Status.EXCLUDED_BY_FILTER, null, null, null,
                    null, address, neighborhood, "Ocorrencia fora do filtro '" + filter.name() + "'");
        }
        String norm = TimestampNormalizer.normalize(text(fields, "Data"));
        try {
            Instant from = TimestampNormalizer.toInstant(norm, tz);
            boolean midnight = TimestampNormalizer.isDateOnly(norm) || norm.endsWith("T00:00") || norm.endsWith("T00:00:00");
            if (midnight) {
                // data sem hora (o CKAN grava meia-noite): dia civil local [00:00, 24:00), precisão DAY
                Instant to = TimestampNormalizer.endOfLocalDay(norm, tz);
                return new OccurrenceClassification(OccurrenceClassification.Status.ACCEPTED, matched, from, to,
                        "DAY", address, neighborhood, null);
            }
            // com hora: instante; o intervalo [from, to) tem 1 microssegundo (não inventa duração)
            return new OccurrenceClassification(OccurrenceClassification.Status.ACCEPTED, matched, from,
                    from.plusNanos(1000), "HOUR", address, neighborhood, null);
        } catch (TsUnparseableException e) {
            return new OccurrenceClassification(OccurrenceClassification.Status.TS_UNPARSEABLE, matched, null, null,
                    null, address, neighborhood, e.getMessage());
        }
    }

    static String text(Map<String, Object> fields, String key) {
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
            }
        }
        return "";
    }

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
