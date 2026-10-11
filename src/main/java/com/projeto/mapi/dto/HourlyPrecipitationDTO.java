package com.projeto.mapi.dto;

import java.util.List;

/**
 * Chuva horária recente do Open-Meteo forecast ({@code hourly=precipitation&past_hours=24}). Cada valor é a soma da
 * hora anterior ao rótulo, em hora local ({@code timezone=auto}), mesma convenção de {@code current.time}.
 */
public record HourlyPrecipitationDTO(List<String> time, List<Double> precipitation) {}
