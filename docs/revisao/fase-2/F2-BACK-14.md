# F2-BACK-14 — Maré prevista (DHN, Porto do Recife) no pipeline

- Branch: `feat/pipeline-mare` (a partir de `integracao/fase-2` em f6c553e). Sem push. Migração nova: `V12__tide_pe02.sql` (aditiva; V11 já é usada em outra worktree, `V11__occurrence_geocode_level.sql`).
- Status: itens 1 a 3 feitos e verdes (182 testes, linha de base 166); item 4 (carga real) **não feita no banco compartilhado** (ver seção 5), mas a carga completa foi validada em banco descartável.

## 1. A API TabuaMare serve o passado? Não. Alternativa oficial existe e serve.

Consultas de 2026-10-10:

- `GET https://tabuamare.api.br/openapi.json`: a rota é `/api/v2/tabua-mare/{harbor}/{month}/{days}`; os únicos parâmetros são `HarborId`, `Month` e `Days`. **Não há ano.** (A URL antiga `tabuamare.devtu.qzz.io` responde `301` para `tabuamare.api.br`; `/api/v1/...` responde `404`.)
- `GET https://tabuamare.api.br/api/v2/harbors/[pe02]`: `"id":"pe02","year":2026,...,"timezone":"UTC -03.0","card":"902","data_collection_institution":"DHN","mean_level":1.28`.
- `.../tabua-mare/pe02/1/[1]?year=2023` e `?ano=2023` devolvem `"year":2026` (janeiro de 2026). O parâmetro é ignorado. Resposta em hora local do porto, `hour` como `"02:47:00"` e `level` em m.
- Conclusão: a API só serve o ano corrente. Usá-la para 2021–2025 rotularia 2026 como outro ano; o adaptador `TabuaMareApiAdapter` **falha** se o `year` da resposta difere do ano pedido (teste `apiNaoRotulaOutroAnoComoOPedido`).

Alternativa oficial: a tábua anual da DHN/CHM (a mesma que a API repassa), PDF por ano, carta 902, "15 ou 16 Componentes", "Nível Médio 1.28 m". As páginas do CHM (`marinha.mil.br/chm/tabuas-de-mare*`) respondem 403 (Cloudflare); os PDFs estão em `assets.marinha.mil.br/chm/sites/www.marinha.mil.br.chm/files/dados_de_mare/`, cuja cadeia de certificado está incompleta (baixados com `curl -k`; a integridade fica no SHA-256 abaixo). Os nomes dos arquivos de 2021 e 2022 só existem no Internet Archive.

| Ano | Arquivo | Cabeçalho de fuso | SHA-256 do PDF |
|---|---|---|---|
| 2021 | `.../dados_de_mare/recife.pdf` (snapshot `web.archive.org/web/20210119221542id_/`) | `Fuso +03.0 horas` | `929ea2f9…d438ae` |
| 2022 | `.../dados_de_mare/24-porto_de_recife.pdf` (via `web.archive.org/web/2id_/`) | `Fuso +03.0 horas` | `7ef96f3a…d8f6c9` |
| 2023 | `.../24-porto_do_recife_tabua_2023_0.pdf` | `Fuso +03.0 horas` | `a87871ff…3acd1a` |
| 2024 | `.../24_-_porto_do_recife_2024_ok.pdf` | `Fuso +03.0 horas` | `789e9c65…f72c22` |
| 2025 | `.../24_-_porto_do_recife_-pe_-_80-82.pdf` | `Fuso UTC -03.0 horas` | `9226dca4…f789c4` |
| 2026 | `.../24%20-%20PORTO%20DO%20RECIFE%20-%2082%20-%2084.pdf` | `Fuso UTC -03.0 horas` | `721a006b…fea49f` |

(SHA-256 completos na primeira linha de cada `src/main/resources/tide/dhn-pe02-<ano>.csv`.) Os PDFs de 2021–2024 têm altura com 0,1 m; os de 2025–2026, com 0,01 m. **Isso é incerteza de quantização de até ±0,05 m em 2021–2024**, heterogênea no dataset.

**Validação do extrator.** `scripts/tabuas-dhn/extrair_tabua_dhn.py` lê as posições das palavras (`pdftotext -bbox`) e aborta se faltar dia, houver menos de 2 ou mais de 4 extremos num dia, ou horário fora de ordem. O extrato de 2026 foi comparado com a API: **1407 extremos em comum, diferença máxima 0,0 m**; só 4 extremos (2026-12-16) existem no PDF e não na API (a API devolveu o mês vazio nessa consulta; o adaptador retenta e falha se persistir). Isso valida o extrator e mostra que a API repassa exatamente a tábua DHN.

## 2. Adaptadores e consolidação (ADR 0006)

- Fontes novas: `TABUAMARE_PE02` (API, só o ano corrente), `DHN_TABUA_PE02` (CSV extraído dos PDFs) e `TIDE_PE02_HOURLY` (derivada). Todas com estação `pe02` (Porto do Recife).
- **Staging:** um registro de origem por extremo, `source_ts_text = "2023-01-01 00:00"` (hora local da tábua, como veio), `source_tz = UTC-03:00`, payload com `level`, `kind` e `vertical_reference = NR_DHN_pe02`; hash do payload canônico. `source_registry` (V12): `UTC-03:00`, estado `DECLARED` (declarado pelo cabeçalho; a API foi conferida contra o PDF). O fuso é fixo (a tábua não declara horário de verão e o Brasil não o usa desde 2019); a conversão para UTC acontece **só na validação** (teste: 00:00 local = 03:00Z; 21:53 local = 00:53Z do dia seguinte).
- **Validação:** `tide_height` e `tide_height_hourly` com faixa `-0,5 a 3,0 m` `[sem fonte, conservadora]` (V12; antes `-2 a 5`, sem uso). O extremo observado nas tábuas carregadas vai de `-0,1 a 2,7 m`. Altura fora da faixa vai para a quarentena `OUT_OF_RANGE` (teste).
- **Extremos (EVENT):** `observation` `tide_height`, `qualifier` = `PREAMAR`/`BAIXA_MAR`, unidade m. A classe vem da comparação com o extremo seguinte (`TideExtremeClassifier`); como depende só do vizinho seguinte, não muda com a janela pedida (teste `classificacaoNaoDependeDaJanela`).
- **Série horária:** `TIDE_PE02_HOURLY`, `tide_height_hourly`, `PT1H`, instante exato da hora cheia UTC (INSTANT/LAST, entra em `observation_hourly_v` sem mudar a view). **Método: cosseno entre extremos consecutivos**, `h(t) = h0 + (h1−h0)·(1−cos(π·(t−t0)/(t1−t0)))/2`, `qualifier = INTERP_COSSENO`. Não extrapola antes do primeiro ou depois do último extremo e não interpola entre extremos a mais de 8 h `[sem fonte]` (nas tábuas carregadas o intervalo vai de 5,78 h a 7,13 h, então o limite nunca atuou). O bruto de cada hora guarda os dois extremos usados, então uma correção na tábua muda o hash e gera revisão.
- **Incerteza do cosseno: não medida** `[sem fonte]`. O método é exato só para maré senoidal; a maré real tem 15–16 componentes e assimetria enchente×vazante (semiciclos de 5,8 a 7,1 h). Falta comparar com marégrafo (série horária observada) para quantificar; até lá, a série horária deve ser tratada como aproximação da tábua, e o erro dela soma-se à quantização acima. Registrado no manifesto do export (`tide.uncertainty`) e no `attrs` da estação.
- **Referência vertical:** altura em m acima do NR da DHN, `vertical_reference = NR_DHN_pe02` em estação, payload, manifesto e Javadoc. **Não foi convertida** ao datum do MDT (ADR 0009); a feature `tide_minus_street_m` continua proibida até o Δ ser aprovado.

## 3. Export

- `tideHeight` (coluna existente, mesmo nome e posição) passa a ser a altura em `t` da série `TIDE_PE02_HOURLY`; **sem fallback** para o `TideService`/`tide_tables` legado (o export deixou de injetá-lo; os endpoints de maré legados seguem como estavam). Ausente = vazio e `tideHeight_missing = 1`.
- Colunas novas **ao fim** de `generateCsv`, cada uma com `_missing`: `tide_height_t1..t6`, `tide_max_next_6h`, `tide_hours_to_next_high`, `tide_range_day_m`, `tide_rising`, `tide_slope_m_h`. Definições no manifesto (`tide.note`): derivada central `(h(t+1)−h(t−1))/2`; `tide_rising` vazio se a derivada é nula ou ausente; amplitude = máximo − mínimo dos extremos do dia civil `America/Recife` de `t`; sem imputação (qualquer insumo ausente deixa a feature vazia). O terreno (`terrain_*`) continua sendo acrescentado depois, como antes.
- JSON do `UnifiedDataDTO`: campos aditivos `tideHeightAhead` (mapa k→altura), `tideMaxNext6h`, `tideHoursToNextHigh`, `tideRangeDayM`, `tideRising`, `tideSlopeMh`. Manifesto: novo bloco `tide` e proveniência de `TIDE_PE02_HOURLY`.
- **Não feito:** "fase de sizígia" como lua; `tide_range_day_m` é o proxy (a lua exigiria cálculo astronômico próprio ou tabela; fica para decisão do autor).
- O `timestampUtc` do export vale para as colunas de maré (ao contrário dos sensores, que seguem UNVERIFIED).

## 4. Testes (182, 0 falhas; base 166)

`TideUnitTest` (7): cosseno com resposta conhecida (0→2 m em 6 h: 1 h = 0,1339746, 2 h = 0,5, 3 h = 1,0), extremo exato, lacuna, **T6** borda da hora (janela semiaberta), classificação, **conversão UTC−3→UTC**, features com resposta conhecida e sem imputação. `TideIngestionIntegrationTest` (9, TimescaleDB real): conversão de fuso e primeiros extremos reais de 2023-01-01 (1,9/0,7/1,8/0,7 m às 03:00/09:11/15:08/21:34Z), **T1** idempotência (extremos e série horária), borda da hora na `observation_hourly_v` (03:00Z exato pertence à linha 03:00), interpolação conhecida (06:00Z = 1,3279 m), API simulada (conversão, ano errado → FAILED, fuso errado → FAILED, faixa → quarentena), ano sem tábua → FAILED. `DataExportIntegrationTest` e `ExportLabelIntegrationTest` ajustados (cabeçalho novo).

## 5. Carga real

- **No banco compartilhado: não feita.** `docker ps` às 17:2x mostrou `mapi-api` (imagem `projeto-mapi-api-mapi-api`, criada pela worktree `agent-ab33cff6556dc578a`, com 128 `ingestion_run`) e `mapi-db` de pé e em uso por outro agente. Não derrubei nem reiniciei nada.
- **Validada em banco descartável (Testcontainers, mesmo código/migrações), com a API real para 2026:** 0 quarentenas, 0 `GAP_BEFORE`, nenhuma violação de alternância preamar/baixa-mar, nenhum extremo sem classe, nenhum intervalo entre extremos acima de 8 h, reexecução da série horária com `raw_inserted = obs_inserted = obs_updated = 0`.

| Ano (UTC) | Extremos | Fonte | Horas com maré | Horas possíveis |
|---|---|---|---|---|
| 2021 (de 10-01) | 363 | DHN | 2205 | 2205 |
| 2022 | 1410 | DHN | 8760 | 8760 |
| 2023 | 1411 | DHN | 8760 | 8760 |
| 2024 | 1414 | DHN | 8784 | 8784 |
| 2025 | 1411 | DHN | 8760 | 8760 |
| 2026 (até 10-31) | 1183 (carga até 11-02) | API | 7299 | 7299 |
| Total | | | **44 568** | 44 568 |

(Extremos por ano na janela carregada; faixa observada: −0,10 a 2,70 m.) Janelas usadas, importantes porque cada borda precisa de um extremo vizinho: extremos `DHN_TABUA_PE02` em `[2021-09-29T03:00Z, 2026-01-01T03:00Z)`, `TABUAMARE_PE02` em `[2026-01-01T03:00Z, 2026-11-03T03:00Z)`, depois `TIDE_PE02_HOURLY` em `[2021-10-01T03:00Z, 2026-11-01T03:00Z)`. Com janelas sem folga faltam 4 h no começo de 2021 e 6 h no fim de 2026.

**Para o orquestrador (quando o banco estiver livre):** subir esta branch com `docker compose -p projeto-mapi-api -f <worktree>/docker-compose.yml up -d --build --no-deps postgres mapi-api` (a V12 roda no start) e disparar, nesta ordem, três `POST /api/admin/ingestion/runs` (`mode = REFETCH`, `kind = BACKFILL`) com as janelas acima. Os três runs são idempotentes. Atenção: se a branch com `V11__occurrence_geocode_level.sql` for mesclada depois de a V12 já ter rodado num banco, o Flyway reclamará da ordem (V11 não aplicada com V12 aplicada); mescle V11 antes ou use `outOfOrder`.

## Limitações e pendências

1. Incerteza do cosseno não medida (precisa de série observada do marégrafo; hoje só há tábua). `[sem fonte]`.
2. Quantização 0,1 m em 2021–2024 versus 0,01 m em 2025–2026.
3. O Nível Médio 1,28 m é de série antiga (1978–1986, ADR 0009); a tábua não inclui resíduo meteorológico nem a elevação recente do nível do mar. É maré astronômica prevista, não nível observado.
4. O fuso está `DECLARED`, não `VERIFIED`: a tábua declara UTC−3 e a API concorda, mas não houve comparação com observação (marégrafo) para provar o instante. A linha `TABUA_MARE` da V3 (UNVERIFIED) segue sem uso.
5. 2021 e 2022 vêm de PDFs do Internet Archive (o CHM não os serve mais); a procedência é o SHA-256 acima.
6. 2027 em diante: a API é a fonte; a tábua anual do ano seguinte precisa ser extraída ou lida da API quando publicada.
7. Os pontos de interesse são todos servidos pelo mesmo porto `pe02`; a série é a mesma para todos os pontos críticos.
