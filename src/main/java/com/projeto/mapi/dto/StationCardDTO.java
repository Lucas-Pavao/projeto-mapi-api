package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Card do pluviômetro: tudo o que a tela da estação mostra, numa chamada. Ausência = null, nunca zero. */
@Schema(description = "Card de uma estação canônica: blocos de 10 min das 3 h, acumulados, estado, aviso, quarentena do dia e pontos atendidos")
public record StationCardDTO(
        String canonicalId,
        String name,
        @Schema(description = "Identificadores de origem, no formato SOURCE:station_id") List<String> aliases,
        @Schema(description = "Distância (m) entre a estação ao vivo e a PED unificadas; null se só há uma fonte") Double aliasDistanceM,
        @Schema(description = "Fontes com apelido (CEMADEN, CEMADEN_PED)") List<String> sources,
        @Schema(description = "Só a PED tem a estação; etiqueta à parte do estado") boolean pedOnly,
        @Schema(description = "Prioridade: NO_RESPONSE, GAP_NOW, FAIL_MONTH, DIVERGENT, PEAK_REMOVED, UNCHECKED, NORMAL") String state,
        @Schema(description = "Chuva (mm) da última hora fechada até 'at'; null se sem dado ou pico isolado removido") Double nowMmH,
        @Schema(description = "Fonte do valor da hora: CEMADEN_PED, CEMADEN ou null") String nowSource,
        @Schema(description = "Soma horária da coleta ao vivo na última hora fechada") Double liveMmH,
        @Schema(description = "Soma horária da PED na última hora fechada") Double pedMmH,
        @Schema(description = "A estação tem PED, mas a PED ainda não trouxe a hora que a coleta ao vivo já trouxe") boolean pedPending,
        @Schema(description = "18 blocos de 10 min das 3 h anteriores a 'at', do mais antigo ao mais recente") List<Block> blocks,
        @Schema(description = "Soma dos 6 últimos blocos (mm); null se algum faltar") Double acc1hMm,
        @Schema(description = "Soma dos 18 blocos (mm); null se algum faltar") Double acc3hMm,
        @Schema(description = "Soma das 24 últimas horas fechadas (mm); null se alguma faltar") Double acc24hMm,
        @Schema(description = "O aviso do estado (um só); null em NORMAL") Note note,
        @Schema(description = "Checagem espacial do mês de 'at'; null se a estação PED não foi checada") MonthCheckDTO monthCheck,
        @Schema(description = "Linhas abertas na quarentena, por motivo, do dia civil UTC de 'at'") Map<String, Integer> quarantineToday,
        @Schema(description = "Pontos críticos a até 5 km e o papel desta estação na chuva de cada um") List<Use> uses,
        @Schema(description = "Última leitura de chuva até 'at', UTC") Instant lastReadingUtc,
        @Schema(description = "Citação obrigatória dos dados do Cemaden") String attribution) {

    @Schema(description = "Bloco de 10 min (fim em endUtc). mm null = sem dado nas duas fontes ou pico isolado removido")
    public record Block(Instant endUtc, Double mm, String source, boolean peakRemoved) {
    }

    @Schema(description = "Aviso do estado, com os números do caso; só os campos do caso vêm preenchidos")
    public record Note(
            String kind,
            Instant hourEndUtc,
            Instant blockEndUtc,
            Double liveMm,
            Double pedMm,
            Double diffMm,
            @Schema(description = "Valor do pico isolado removido (mm)") Double valueMm,
            Instant lastReadingUtc,
            Long minutesSinceLastReading,
            List<String> reasons) {
    }

    @Schema(description = "role: USED | LOWER (com usedStationName) | EXCLUDED | NO_VALUE")
    public record Use(String slug, String name, double distanceM, String role, String usedStationName) {
    }
}
