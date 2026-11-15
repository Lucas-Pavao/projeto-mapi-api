package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Uma linha da tabela de eventos de chuva (ADR 0017, {@code docs/EVENTOS-DE-CHUVA.md}). A ordem dos campos é a ordem
 * das colunas do CSV. Ausente = {@code null} (célula vazia no CSV); nada é imputado.
 */
@Schema(description = "Evento de chuva num ponto crítico. Instantes em UTC; *Local só para leitura (America/Recife).")
public record RainEventDTO(
        String floodPointSlug,
        @Schema(description = "SHA-256 (16 hex) de slug + início + fonte + regras; determinístico") String eventId,
        String source,
        Instant startUtc,
        Instant endUtc,
        @Schema(description = "Início em America/Recife, só leitura") String startLocal,
        @Schema(description = "Fim em America/Recife, só leitura") String endLocal,
        Integer durationMin,
        @Schema(description = "Horas desde o fim do evento anterior; nulo se não há anterior na série ou há lacuna entre eles")
        Double dryHoursBefore,
        Double totalMm,
        Double meanIntensityMmH,
        Double maxHourlyMmH,
        @Schema(description = "Maior leitura de 15 min × 4 (só ANA); nulo nas outras fontes") Double max15minMmH,
        @Schema(description = "FRACA, MEDIA ou FORTE pela regra do manifesto") String type,
        @Schema(description = "Minutos do início do evento ao início da hora de pico") Integer peakOffsetMin,
        Integer expectedReadings,
        Integer receivedReadings,
        @Schema(description = "Lacuna dentro do evento ou nas horas de separação") Boolean hasGap,
        @Schema(description = "Começa antes de from, termina depois de to ou encosta na borda da série; fora do resumo")
        Boolean truncated,
        List<String> provenanceRunIds,
        String stationId,
        Double stationDistanceM,
        @Schema(description = "MEASURED (pluviômetro no raio), REANALYSIS (pedida) ou REANALYSIS_FALLBACK (nenhum "
                + "pluviômetro no raio do ADR 0018)") String rainOrigin,
        @Schema(description = "Maré prevista (NR DHN, pe02) nas horas do evento") Double tideMaxM,
        Double tideMeanM,
        @Schema(description = "Horas do evento com maré acima de tideSillM; nulo se a soleira não foi informada")
        Integer hoursTideAboveSill,
        @Schema(description = "Amplitude da maré no dia local do início (máx − mín dos extremos)") Double tideRangeDayM,
        @Schema(description = "SIZIGIA ou QUADRATURA pela amplitude do dia") String tideRegime,
        String riverStationId,
        Double riverStationDistanceM,
        Double riverLevelMaxM,
        @Schema(description = "Nível acima da cota de alerta; nulo se a estação não tem cota de alerta registrada")
        Boolean riverAboveAlert,
        @Schema(description = "Umidade do solo 0-7 cm (ERA5-Land, m³/m³) na hora que termina no início do evento")
        Double soilMoistureBefore,
        Double antecedentRain24h,
        Double antecedentRain72h,
        Double terrainContributingAreaM2,
        @Schema(description = "Declividade média (%) da janela de 100 m (point_terrain.mean_slope_pct)") Double terrainSlope,
        Double terrainStreetElevationM,
        @Schema(description = "Mês local do início (1-12)") Integer month,
        @Schema(description = "VERAO, OUTONO, INVERNO, PRIMAVERA (trimestres DJF, MAM, JJA, SON)") String season,
        @Schema(description = "totalMm × C") Double estimatedInflowMm,
        @Schema(description = "Soma, nas horas do evento, do que a chuva horária passou da capacidade") Double excessOverCapacityMm,
        Integer hoursAboveCapacity,
        @Schema(description = "SÓ VALIDAÇÃO. Houve ocorrência CKAN casada com o ponto num dia local do evento. "
                + "Nunca usar para calibrar limiar ou capacidade (circularidade).")
        Boolean ckanOccurrenceSameDay) {
}
