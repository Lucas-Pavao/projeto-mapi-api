-- V46 — Cobertura da Central 156 por ano (emenda 1 do pré-registro da 156, E3). Só adição.
-- fim_cobertura = maior data de demanda vista na base inteira (não só nos chamados casados com pontos). Atualizada a cada
-- ingestão; decide quando a última limpeza conhecida de um ponto ainda vale.
CREATE TABLE drainage_cleaning_coverage (
    source_year     INTEGER     PRIMARY KEY,
    max_demand_date DATE        NOT NULL,
    rows_read       INTEGER     NOT NULL,
    ingested_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
