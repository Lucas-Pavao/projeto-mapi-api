package com.projeto.mapi.service.ingestion.adapter.tide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.dto.TabuaMareResponse;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.tide.TabuaMareService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * API TabuaMare ({@code /tabua-mare/pe02/{mês}/{dias}}), tábua da DHN do Porto do Recife.
 *
 * <p>Verificado em 2026-10-10 (https://tabuamare.api.br/openapi.json): o caminho NÃO tem parâmetro de ano e a resposta
 * traz {@code "year":2026}; a API serve só o ano corrente. Por isso o adaptador EXIGE que o {@code year} da resposta
 * seja o ano pedido e que {@code timezone} seja {@code "UTC -03.0"}; senão falha em vez de rotular dado de outro ano.
 * Anos passados vêm de {@link DhnTabuaCsvAdapter}.
 */
@Component
public class TabuaMareApiAdapter extends AbstractTideExtremesAdapter {

    public static final String SOURCE = "TABUAMARE_PE02";
    static final String EXPECTED_TIMEZONE = "UTC -03.0";
    private static final int ATTEMPTS = 3;

    private final TabuaMareService api;
    private final CacheManager cacheManager;
    private final ObjectMapper mapper;

    public TabuaMareApiAdapter(TabuaMareService api, CacheManager cacheManager, ObjectMapper mapper, Clock clock) {
        super(mapper, clock);
        this.api = api;
        this.cacheManager = cacheManager;
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "tabuamare-api/1";
    }

    @Override
    protected Loaded load(LocalDate firstDay, LocalDate lastDay, LocalDate coreFirst, LocalDate coreLast) {
        List<TideExtreme> out = new ArrayList<>();
        ArrayNode requests = mapper.createArrayNode();
        for (YearMonth ym = YearMonth.from(firstDay); !ym.isAfter(YearMonth.from(lastDay)); ym = ym.plusMonths(1)) {
            String days = days(ym);
            boolean marginOnly = ym.atEndOfMonth().isBefore(coreFirst) || ym.atDay(1).isAfter(coreLast);
            Map<String, Object> harbor;
            try {
                harbor = fetchMonth(ym, days);
            } catch (SourceUnavailableException e) {
                if (marginOnly) continue; // mês só de folga (vizinho da classificação): sem dado = sem vizinho
                throw e;
            }
            Object year = harbor.get("year");
            if (!(year instanceof Number n) || n.intValue() != ym.getYear()) {
                if (marginOnly) continue; // folga em outro ano: a API não o serve; o extremo da borda fica sem vizinho
                throw new SourceUnavailableException("TabuaMare serve o ano " + year + ", não " + ym.getYear()
                        + " (a API não tem parâmetro de ano)");
            }
            if (!EXPECTED_TIMEZONE.equals(harbor.get("timezone"))) {
                throw new SourceUnavailableException("TabuaMare declarou timezone=" + harbor.get("timezone")
                        + " (esperado " + EXPECTED_TIMEZONE + ")");
            }
            requests.addObject().put("path", "/tabua-mare/" + TideConstants.STATION + "/" + ym.getMonthValue() + "/" + days)
                    .put("year", ym.getYear()).put("timezone", String.valueOf(harbor.get("timezone")));
            collect(harbor, ym, out);
        }
        ObjectNode req = mapper.createObjectNode();
        req.put("origin", "TabuaMare API v2 (tabuamare.api.br), tábua da DHN");
        req.put("timezone", EXPECTED_TIMEZONE);
        req.set("requests", requests);
        return new Loaded(out, req);
    }

    @SuppressWarnings("unchecked")
    private void collect(Map<String, Object> harbor, YearMonth ym, List<TideExtreme> out) {
        Object months = harbor.get("months");
        if (!(months instanceof List<?> ml) || ml.isEmpty()) return;
        Map<String, Object> month = (Map<String, Object>) ml.get(0);
        Object days = month.get("days");
        if (!(days instanceof List<?> dl)) return;
        for (Object dObj : dl) {
            Map<String, Object> day = (Map<String, Object>) dObj;
            LocalDate date = ym.atDay(((Number) day.get("day")).intValue());
            if (!(day.get("hours") instanceof List<?> hl)) continue;
            for (Object hObj : hl) {
                Map<String, Object> h = (Map<String, Object>) hObj;
                if (!(h.get("level") instanceof Number level)) continue;
                out.add(new TideExtreme(date, LocalTime.parse(String.valueOf(h.get("hour"))), level.doubleValue()));
            }
        }
    }

    /**
     * A TabuaMare, sob rajada, às vezes devolve 200 com "months" vazio (F2-BACK-01/Sync): retenta evictando o
     * cache da chamada antes. Depois das tentativas, falha: mês vazio não vira "sem extremos" em silêncio.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMonth(YearMonth ym, String days) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            TabuaMareResponse<List<Object>> resp = api.getTideTable(TideConstants.STATION, ym.getMonthValue(), days);
            if (resp != null && resp.getData() != null && !resp.getData().isEmpty()
                    && resp.getData().get(0) instanceof Map<?, ?> m
                    && m.get("months") instanceof List<?> l && !l.isEmpty()) {
                return (Map<String, Object>) m;
            }
            Cache cache = cacheManager.getCache("tideTableDaily");
            if (cache != null) cache.evict(TideConstants.STATION + "-" + ym.getMonthValue() + "-" + days);
        }
        throw new SourceUnavailableException("TabuaMare sem dados para " + ym + " após " + ATTEMPTS + " tentativas");
    }

    private static String days(YearMonth ym) {
        StringBuilder sb = new StringBuilder("[");
        for (int d = 1; d <= ym.lengthOfMonth(); d++) sb.append(d > 1 ? "," : "").append(d);
        return sb.append("]").toString();
    }
}
