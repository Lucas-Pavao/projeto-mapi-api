package com.projeto.mapi.service.horizontes;

import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.dto.HorizonsDTO;
import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.dto.OfficialForecastDTO;
import com.projeto.mapi.dto.OfficialWarningsResponseDTO;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import com.projeto.mapi.service.ingestion.freshness.SourceFreshnessService;
import com.projeto.mapi.service.warning.InmetWarningParser;
import com.projeto.mapi.service.warning.OfficialForecastService;
import com.projeto.mapi.service.warning.OfficialWarningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Junta as três camadas sem combinar: cada uma vem da sua fonte, com origem, idade e motivo. Falha de uma camada não
 * derruba as outras: vira motivo naquela camada.
 */
@Service
@Slf4j
public class HorizonsServiceImpl implements HorizonsService {

    static final String DAY_RISK_VERSION = "day_risk_v0";
    static final String DAY_RISK_REPORT = "projeto-mapi-ai: reports/day_risk/RELATORIO.md";
    static final String DAY_RISK_NOT_VALIDATED = "O \"dia de risco\" do MAPi não venceu a climatologia na avaliação de "
            + "22/11/2026. Fica só a previsão do INMET para o dia.";
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    private final FloodPointRepository points;
    private final OfficialWarningService warnings;
    private final OfficialForecastService forecasts;
    private final FloodForecastService flood;
    private final SourceFreshnessService freshness;
    private final Clock clock;
    private final boolean dayRiskValidated;

    public HorizonsServiceImpl(FloodPointRepository points, OfficialWarningService warnings, OfficialForecastService forecasts,
                               FloodForecastService flood, SourceFreshnessService freshness, Clock clock,
                               @Value("${app.horizontes.dia-de-risco-validado:false}") boolean dayRiskValidated) {
        this.points = points;
        this.warnings = warnings;
        this.forecasts = forecasts;
        this.flood = flood;
        this.freshness = freshness;
        this.clock = clock;
        this.dayRiskValidated = dayRiskValidated;
    }

    @Override
    public HorizonsDTO forPoint(String slug, boolean admin) {
        FloodPoint p = points.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        Instant now = clock.instant();
        String ibge = InmetWarningParser.resolveIbge(p.getMunicipality()).orElse(null);
        return new HorizonsDTO(p.getSlug(), p.getName(), p.getMunicipality(), ibge, now,
                official(ibge, now), dayRisk(), nextHour(p, admin, now));
    }

    private HorizonsDTO.Official official(String ibge, Instant now) {
        if (ibge == null) {
            OfficialForecastDTO f = forecasts.current(null);
            return new HorizonsDTO.Official(OfficialForecastDTO.SOURCE, OfficialWarningsResponseDTO.ATTRIBUTION, List.of(),
                    "MUNICIPIO_DESCONHECIDO", "O município do ponto não tem código IBGE conhecido.", null, f);
        }
        List<com.projeto.mapi.dto.OfficialWarningDTO> active = List.of();
        String wReason = null;
        String wText = null;
        Instant checked = null;
        try {
            active = warnings.activeWarnings(ibge).warnings();
            Freshness fr = freshness.freshness().get("INMET_AVISOS");
            checked = fr == null ? null : fr.lastAcceptedAt();
            if (fr == null || !"FRESH".equals(fr.state())) {
                wReason = "AVISOS_SEM_COLETA";
                wText = "A coleta de avisos do INMET está parada. Sem aviso na lista não quer dizer que não há aviso.";
            }
        } catch (RuntimeException e) {
            log.warn("Avisos do INMET indisponíveis para {}: {}", ibge, e.toString());
            wReason = "AVISOS_INDISPONIVEIS";
            wText = "Não foi possível ler os avisos do INMET agora.";
        }
        OfficialForecastDTO f;
        try {
            f = todayAndTomorrow(forecasts.current(ibge), now);
        } catch (RuntimeException e) {
            log.warn("Previsão do INMET indisponível para {}: {}", ibge, e.toString());
            f = new OfficialForecastDTO(OfficialForecastDTO.SOURCE, OfficialForecastDTO.SOURCE_LABEL,
                    OfficialForecastDTO.ATTRIBUTION, OfficialForecastDTO.NOTE, ibge, null, null, null, true,
                    "PREVISAO_INDISPONIVEL", "Não foi possível ler a previsão do INMET agora.", List.of());
        }
        return new HorizonsDTO.Official(OfficialForecastDTO.SOURCE, OfficialWarningsResponseDTO.ATTRIBUTION, active, wReason,
                wText, checked, f);
    }

    /** Só hoje e amanhã (data civil de Recife), como pede a tela "Hoje". */
    static OfficialForecastDTO todayAndTomorrow(OfficialForecastDTO f, Instant now) {
        LocalDate today = now.atZone(RECIFE).toLocalDate();
        List<OfficialForecastDTO.Day> days = f.days().stream()
                .filter(d -> !d.date().isBefore(today) && !d.date().isAfter(today.plusDays(1))).toList();
        return new OfficialForecastDTO(f.source(), f.sourceLabel(), f.attribution(), f.note(), f.ibge(), f.municipality(),
                f.lastCollectedAt(), f.ageMinutes(), f.stale(), f.reason(), f.reasonText(), days);
    }

    private HorizonsDTO.DayRisk dayRisk() {
        if (dayRiskValidated) {
            // O serviço POST /v1/forecast/day-risk só existe se o bloco C passar; até lá esta configuração fica falsa.
            return new HorizonsDTO.DayRisk(false, "MAPi", DAY_RISK_VERSION, "SEM_SERVICO",
                    "O \"dia de risco\" foi marcado como validado, mas o serviço ainda não existe.", DAY_RISK_REPORT);
        }
        return new HorizonsDTO.DayRisk(false, "MAPi", DAY_RISK_VERSION, "NAO_VALIDADO", DAY_RISK_NOT_VALIDATED, DAY_RISK_REPORT);
    }

    private HorizonsDTO.NextHour nextHour(FloodPoint p, boolean admin, Instant now) {
        boolean inTest = flood.shadow() && !admin;
        if (!flood.enabled()) {
            return new HorizonsDTO.NextHour("MAPi", "SEM_DADO", null, true, inTest, null, null, "ALERTA_DESLIGADO",
                    "O alerta de alagamento está desligado nesta instalação.");
        }
        Optional<AlagamentoPontoDTO> st;
        try {
            st = flood.current().stream().filter(a -> a.slug().equals(p.getSlug())).findFirst();
        } catch (RuntimeException e) {
            log.warn("Estado do alerta indisponível para {}: {}", p.getSlug(), e.toString());
            st = Optional.empty();
        }
        if (st.isEmpty()) {
            return new HorizonsDTO.NextHour("MAPi", inTest ? null : "SEM_DADO", null, true, inTest, null, null,
                    "SEM_CICLO", "O alerta ainda não avaliou este ponto. Sem dado não é \"sem alerta\".");
        }
        AlagamentoPontoDTO a = st.get();
        Instant at = a.evaluatedAt() != null ? a.evaluatedAt() : a.issuedAt();
        Long age = at == null ? null : Duration.between(at, now).toMinutes();
        if (inTest) {
            return new HorizonsDTO.NextHour("MAPi", null, null, a.experimental(), true, a.issuedAt(), age, null, null);
        }
        return new HorizonsDTO.NextHour("MAPi", a.level(), a.worstHorizonHours(), a.experimental(), false, a.issuedAt(), age,
                a.reason(), a.reasonText());
    }
}
