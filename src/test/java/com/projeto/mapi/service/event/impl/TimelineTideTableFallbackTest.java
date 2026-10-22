package com.projeto.mapi.service.event.impl;

import com.projeto.mapi.model.DayData;
import com.projeto.mapi.model.HourData;
import com.projeto.mapi.model.MonthData;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.event.TideInterpolation;
import com.projeto.mapi.service.ingestion.adapter.tide.TideConstants;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sem extremos em observation, a maré da timeline sai da tábua local (tide_tables), hora da tábua em UTC-3. */
class TimelineTideTableFallbackTest {

    private static HourData h(String hour, float level) {
        return HourData.builder().hour(hour).level(level).build();
    }

    @Test
    void extremosDaTabuaEmUtcNaJanelaEOrdenados() {
        DayData d21 = DayData.builder().day(21).hours(List.of(h("12:38:00", 1.84f), h("00:04:00", 1.81f), h("06:40:00", 0.68f),
                h("lixo", 1.0f))).build();
        DayData d22 = DayData.builder().day(22).hours(List.of(h("00:50:00", 1.75f))).build();
        MonthData out = MonthData.builder().month(10).days(List.of(d21, d22)).build();
        TideTable table = TideTable.builder().harborName(TideConstants.HARBOR_NAME).year(2026).months(List.of(out)).build();

        TideTableRepository repo = mock(TideTableRepository.class);
        when(repo.findAllByHarborNameIgnoreCaseAndYear(TideConstants.HARBOR_NAME, 2026)).thenReturn(List.of(table));
        TimelineServiceImpl service = new TimelineServiceImpl(null, null, null, null, repo, null, null, Clock.systemUTC());

        List<TideInterpolation.Extreme> ex = service.tableExtremes(Instant.parse("2026-10-21T05:00:00Z"),
                Instant.parse("2026-10-22T03:50:00Z"));

        // 00:04 local = 03:04Z fica antes da janela; 00:50 local de 22/10 = 03:50Z fica fora ([from, to))
        assertThat(ex).extracting(TideInterpolation.Extreme::ts).containsExactly(
                Instant.parse("2026-10-21T09:40:00Z"), Instant.parse("2026-10-21T15:38:00Z"));
        assertThat(ex.get(0).level()).isEqualTo(0.68f);
    }
}
