package com.projeto.mapi.service.ingestion;

import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import com.projeto.mapi.service.ingestion.validation.TsUnparseableException;
import com.projeto.mapi.service.ingestion.validation.TzUnverifiedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T3: conversão por fonte, idêntica com a JVM em UTC, America/Recife ou qualquer outro fuso. */
class TimezoneConversionTest {

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Recife", "Asia/Tokyo"})
    void resultadosIndependemDoFusoDaJvm(String jvmZone) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
        try {
            // Open-Meteo: UTC
            assertThat(TimestampNormalizer.toInstant("2026-10-10T14:00", "UTC")).isEqualTo(Instant.parse("2026-10-10T14:00:00Z"));
            // APAC portal: dia 2026-10-10 em Recife = [03:00Z, 03:00Z do dia seguinte)
            assertThat(TimestampNormalizer.toInstant("2026-10-10", "America/Recife")).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
            assertThat(TimestampNormalizer.endOfLocalDay("2026-10-10", "America/Recife")).isEqualTo(Instant.parse("2026-10-11T03:00:00Z"));
            // CKAN: data com hora 14:30 local = 17:30Z; data sem hora normaliza igual
            assertThat(TimestampNormalizer.toInstant(TimestampNormalizer.normalize("10/10/2026 14:30"), "America/Recife"))
                    .isEqualTo(Instant.parse("2026-10-10T17:30:00Z"));
            assertThat(TimestampNormalizer.normalize("10/10/2026")).isEqualTo("2026-10-10");
            assertThat(TimestampNormalizer.toInstant(TimestampNormalizer.normalize("10/10/2026"), "America/Recife"))
                    .isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void anaComFusoNaoVerificadoNaoConverte() {
        assertThatThrownBy(() -> TimestampNormalizer.toInstant("2026-10-10T14:00", "UNVERIFIED"))
                .isInstanceOf(TzUnverifiedException.class);
    }

    @Test
    void textoIlegivelNuncaViraNow() {
        assertThatThrownBy(() -> TimestampNormalizer.toInstant("ontem de manhã", "UTC"))
                .isInstanceOf(TsUnparseableException.class);
    }

    @Test
    void formatosDiferentesDoMesmoInstanteTemAMesmaIdentidade() {
        assertThat(TimestampNormalizer.normalize("2026-10-10 14:00:00")).isEqualTo(TimestampNormalizer.normalize("2026-10-10T14:00"));
        assertThat(TimestampNormalizer.normalize("10/10/2026 14:00")).isEqualTo("2026-10-10T14:00");
    }
}
