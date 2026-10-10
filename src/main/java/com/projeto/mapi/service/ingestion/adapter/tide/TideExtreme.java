package com.projeto.mapi.service.ingestion.adapter.tide;

import java.time.LocalDate;
import java.time.LocalTime;

/** Um extremo da tábua (preamar ou baixa-mar) na hora local da tábua (UTC-3), altura em m acima do NR da DHN. */
public record TideExtreme(LocalDate date, LocalTime time, double level) {}
