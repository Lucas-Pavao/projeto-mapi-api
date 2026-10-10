# Ingestão — guia operacional do pipeline alvo

- Tarefa: F2-DADOS-05 · Data: 2026-10-10 · Status: **alvo proposto**. Nada abaixo existe no código ainda; o desenho, as tabelas e as decisões estão em `docs/adr/0006-pipeline-de-ingestao.md` (proposto, depende do ADR 0005). Os nomes de endpoint e comando são propostas sujeitas à aprovação de contrato.

## Fluxo em uma linha

Fonte → parser (declara o fuso) → `raw_observation` (bruto, append-only) → validação (converte para UTC, confere faixa, unidade, duplicata e lacuna; reprovado vai para `ingestion_quarantine`) → `observation` (série consolidada, `timestamptz`, chave natural) → view horária (`observation_hourly_v`) → export com manifesto. Cada passo grava uma linha em `ingestion_run`.

## Regras que valem sempre

1. UTC no banco; `America/Recife` só na exibição (ADR 0001).
2. O fuso é convertido na validação, não no parser. Fonte com fuso `UNVERIFIED` (ANA, APAC meteorologia24h e Cemaden, até serem verificadas) fica em quarentena `TZ_UNVERIFIED`.
3. Nunca preencher lacuna com constante: ausência é `NULL` mais flag.
4. Hora é a grade do dataset, rotulada pelo **fim** do intervalo. Total diário da APAC (`P1D`) é série separada e não entra na grade horária.
5. Nada é apagado. Correção da agência cria nova `revision`; o valor anterior fica em `observation_revision`.
6. Schema só muda por Flyway, com aprovação (ADR 0003). Testes de ingestão usam Testcontainers (ADR 0004).

## Como rodar uma carga histórica

A carga parte do zero (não há histórico de 5 anos em lugar nenhum). Ordem recomendada, após as migrations V3–V6 aplicadas e aprovadas:

1. Confirmar o fuso da fonte em `source_registry` (estado diferente de `UNVERIFIED`) e o `variable_catalog` preenchido com faixas e fontes citadas.
2. Disparar o backfill por fonte e intervalo (proposta): `POST /api/admin/ingestion/runs` com `{"source":"OPEN_METEO_ARCHIVE","from":"2021-01-01T00:00:00Z","to":"2026-01-01T00:00:00Z","mode":"REFETCH"}` (papel `ADMIN`). Fazer por blocos (por exemplo, um ano) para limitar o tamanho de cada lote; uma transação por lote.
3. Conferir o `ingestion_run`: `status = OK`, e as contagens `fetched = raw_inserted + raw_unchanged`, `valid + quarantined` coerente com `fetched`.
4. Revisar a quarentena (consulta abaixo) antes de liberar o dataset.
5. A leitura horária é a view `observation_hourly_v` (sem materialização): não há agregado para atualizar. Hora rotulada pelo fim do intervalo `(t−1h, t]`.
6. Rótulos: importar o CKAN (`source = CKAN_RECIFE`), conferir o filtro Cobrade em `label_filter` e o raio em `linkage_config`. O realinhamento ao pico de chuva fica desativado.

## Como reprocessar `[t0, t1]` de uma fonte

Mesma chamada, com `kind` implícito `REPROCESS`:

- `"mode":"REFETCH"`: baixa de novo a fonte (para pegar correção da agência); registros sem mudança também são revalidados.
- `"mode":"REVALIDATE"`: não vai à agência; reaplica regras e fuso sobre o bruto já guardado, selecionado pelo instante de origem (use depois de corrigir uma regra, uma faixa ou o fuso de uma fonte). Uma linha deslocada pelo fuso pode cair fora de `[t0, t1)`; isso é esperado.

Garantias: só registros de origem em `[t0, t1)` são lidos; repetir a chamada sobre a mesma resposta não insere nem altera nada (`raw_inserted`, `obs_inserted`, `obs_updated` zerados na segunda vez); registro que a agência deixou de devolver não é apagado (conta em `absent_in_refetch`). Há um reprocessamento por fonte por vez (advisory lock).

## Como auditar

Consultas de partida (nomes do ADR 0006; ajustar ao schema aprovado):

```sql
-- o que uma execução fez
SELECT * FROM ingestion_run WHERE id = :run_id;

-- o que mudou numa execução (valores corrigidos)
SELECT * FROM observation_revision WHERE superseded_by_run_id = :run_id;

-- de onde veio um valor exportado: do provenanceId ao bruto
SELECT o.*, r.payload, r.source_ts_text, r.source_tz
FROM observation o
JOIN raw_observation r ON r.content_hash = o.raw_hash AND r.source = o.source
WHERE o.run_id = :provenance_id AND o.station_id = :station AND o.ts_utc = :ts;

-- quarentena aberta por regra
SELECT rule_id, reason, count(*) FROM ingestion_quarantine
WHERE resolved_at IS NULL GROUP BY 1, 2 ORDER BY 3 DESC;
```

No dataset exportado, o **manifesto** (`/api/export/ia-dataset/{slug}/manifest`, proposto) lista `datasetId` (SHA-256 do CSV), período, fusos, `linkage_config` usada, `rule_version` e os `run_id` por fonte e variável. É o documento que a banca recebe junto do CSV.

## Retenção e compressão (resumo)

Staging bruto e série consolidada **não têm `drop_chunks`**. Compressão (14 dias no bruto, 60 na consolidada) e continuous aggregates estão **adiados** (V7): entram só se um limiar de medição for atingido (ADR 0006, seção 9). Apagar bruto exige exportar para arquivo com SHA-256 e aprovação explícita. Detalhe e volumes: ADR 0006, seção 9.

## Pendências antes de usar

- Aprovação de contrato e das migrations V3–V6 (ADR 0006, "Contratos que mudam").
- Verificação do fuso de ANA, APAC meteorologia24h e Cemaden.
- Decisão sobre os ~4 semanas de dados de desenvolvimento (ADR 0006, seção 4).
- ADR 0005 (banco) aprovado.
