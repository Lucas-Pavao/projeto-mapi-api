package com.projeto.mapi.service.ingestion;

import lombok.Getter;

/** Contadores de um ingestion_run, acumulados por lote. */
@Getter
public class RunCounters {
    private int fetched;
    private int batchDeduped;
    private int rawInserted;
    private int rawUnchanged;
    private int valid;
    private int quarantined;
    private int gaps;
    private int obsInserted;
    private int obsUpdated;
    private int obsUnchanged;
    private int obsRetracted;
    private int absentInRefetch;

    public void addFetched(int n) { fetched += n; }
    public void addBatchDeduped(int n) { batchDeduped += n; }
    public void addRawInserted(int n) { rawInserted += n; }
    public void addRawUnchanged(int n) { rawUnchanged += n; }
    public void addValid(int n) { valid += n; }
    public void addQuarantined(int n) { quarantined += n; }
    public void addGaps(int n) { gaps += n; }
    public void addObsInserted(int n) { obsInserted += n; }
    public void addObsUpdated(int n) { obsUpdated += n; }
    public void addObsUnchanged(int n) { obsUnchanged += n; }
    public void addObsRetracted(int n) { obsRetracted += n; }
    public void setAbsentInRefetch(int n) { absentInRefetch = n; }
}
