# F2-BACK-21 — carga da maré 2021–2026 no banco local e novo export

Código: worktree da integração, `integracao/fase-2` @ `0497f3a` (sem alteração). Banco: `mapi-db`, volume `projeto-mapi-api_postgres_ha_data` (nenhum volume novo; 5 volumes antes e depois). Nada apagado. Stack subido com `docker compose -p projeto-mapi-api -f <wt-integ>/docker-compose.yml up -d --build --no-deps postgres mapi-api` e parado ao fim com `stop`.

## Migrações
V10, V11 e V12 em `flyway_schema_history` com `success = t`.

## Credencial admin
O usuário `revisao-admin` do F2-BACK-17 já existia (senha não conhecida; o seeder não a altera). Criei, via `ADMIN_BOOTSTRAP_*`, mais um usuário ADMIN (`revisao-admin-f221`): +1 linha em `users`. Senha aleatória, fora do repositório.

## Carga (3 execuções, `POST /api/admin/ingestion/runs`, `REFETCH`/`BACKFILL`)
Janelas da seção 5 do F2-BACK-14.

| Fonte | Janela | Resultado |
|---|---|---|
| `DHN_TABUA_PE02` | `[2021-09-29T03:00Z, 2026-01-01T03:00Z)` | OK: 6 009 extremos; quarentena 0 |
| `TABUAMARE_PE02` | `[2026-01-01T03:00Z, 2026-11-03T03:00Z)` | OK na 2ª tentativa: 1 183 extremos; quarentena 0 |
| `TIDE_PE02_HOURLY` | `[2021-10-01T03:00Z, 2026-11-01T03:00Z)` | OK (rodada final): 44 568 lidos e inseridos, 0 atualizados, 0 quarentenas, 0 `gaps` |

Desvios do roteiro, sem mudança de código:
1. O `POST` devolve o `runId` na hora e executa em segundo plano; as três chamadas em sequência rodaram em paralelo. A maré horária da 1ª tentativa rodou antes dos extremos existirem e terminou OK com 0 linhas (no-op). Repeti a horária depois das outras duas; é essa a rodada contada acima.
2. A 1ª tentativa de `TABUAMARE_PE02` falhou (`FAILED`, 0 linhas gravadas): "TabuaMare sem dados para 2026-05 após 3 tentativas", causa `RateLimiter 'tabuaMare' does not permit further calls` (limite de 15 chamadas/60 s em `application.yml`). Repetida após 70 s, passou. Ficam 1 run FAILED e 1 run horário vazio no `ingestion_run` (histórico, não apagados).
3. Recomendação ao orquestrador/autor: o roteiro de carga deve aguardar o fim de cada run (consultar `ingestion_run.status`) antes do seguinte.

### Horas com maré em `observation` (`source = TIDE_PE02_HOURLY`, por ano UTC)
| Ano | Banco local | Descartável (F2-BACK-14) |
|---|---|---|
| 2021 | 2 205 | 2 205 |
| 2022 | 8 760 | 8 760 |
| 2023 | 8 760 | 8 760 |
| 2024 | 8 784 | 8 784 |
| 2025 | 8 760 | 8 760 |
| 2026 | 7 299 | 7 299 |
| Total | 44 568 | 44 568 |

Idêntico ao banco descartável em todos os anos.

## Export
`GET /api/export/ia-dataset/AGAMENON_DERBY/csv?days=1825&to=2026-10-01T00:00:00Z`, 10 chamadas sequenciais com a API aquecida (`F2-BACK-21-bench.txt`), mais uma chamada fria depois de reiniciar a API.

| Medida | Valor |
|---|---|
| 1ª chamada fria (API recém-reiniciada) | 9,49 s |
| Mediana (10 quentes) | 8,62 s |
| p95 (= máx. de 10) | 8,82 s |
| Mín. | 8,54 s |
| Tamanho do CSV | 10 658 360 bytes, 43 801 linhas |
| Hashes distintos (11 chamadas) | 1 |

Gatilho do ADR 0006 §9 (p95 > 5 s): **excedido**. F2-BACK-17 media 3,24 s de mediana e 5,71 s de p95 com a maré lida da tábua local (ponto CIN_UFPE, 306 768 linhas de clima na consulta de referência); agora são 8,6 s com o ponto AGAMENON_DERBY e a maré do pipeline (Java, por hora, consulta a `observation`). Não é comparação de mesmo ponto: o ponto e o código mudaram juntos. Não alterei código; fica como pendência de otimização (o provável gargalo é a leitura da maré por hora em `ObservationReadRepository`/ `DataExportServiceImpl`, a confirmar com perfil).

### Células vazias por coluna de maré, por ano UTC
Linhas por ano: 2021 = 2 184 (a partir de 10-02), 2022 = 8 760, 2023 = 8 760, 2024 = 8 784, 2025 = 8 760, 2026 = 6 553 (até 10-01).

- `tideHeight`, `tide_height_t1..t6`, `tide_max_next_6h`, `tide_hours_to_next_high`, `tide_range_day_m`, `tide_slope_m_h` e todas as colunas `*_missing` de maré: **0 vazias em todos os anos**, inclusive 2023 e 2024.
- `tide_rising`: vazias 16 (2021), 88 (2022), 91 (2023), 79 (2024), 96 (2025), 79 (2026), cerca de 1% por ano; a flag `tide_rising_missing` não as marca (0). Provável: inclinação nula em horas de extremo (sem direção). Não investigado no código; pendência de conferência.

### Artefatos
Em `/home/lucas/Documents/Projetos/MAPI/exports/`:
- `AGAMENON_DERBY_mare-pipeline_2021-10-02_2026-10-01.csv` — SHA-256 `8f3e27029d87d719cce3979a43b1f2db302fe392952151fde043f6d0ec455b37` (igual ao `datasetId` do manifesto)
- `AGAMENON_DERBY_mare-pipeline_2021-10-02_2026-10-01.manifest.json` — SHA-256 `2ef188cb3c26779554a68a6cd52fb11afa2f61ee7f9747ae7f035efb487248f4`

Os arquivos antigos `AGAMENON_DERBY.csv`/`.manifest.json` e `SHA256SUMS.txt` ficaram intocados.
