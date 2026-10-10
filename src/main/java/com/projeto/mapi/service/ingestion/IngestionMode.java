package com.projeto.mapi.service.ingestion;

/** REFETCH baixa de novo da agência; REVALIDATE só reaplica regras e fuso sobre o bruto guardado. */
public enum IngestionMode { REFETCH, REVALIDATE }
