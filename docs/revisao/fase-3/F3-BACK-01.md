# F3-BACK-01 — Terreno do PE3D no backend

Branch `feat/terreno-backend` (base `integracao/fase-2`). Especificação: ADR 0008 (aprovado, inclui V9) e ADR 0009 (sem comparação com maré).

## O que foi feito

| Item | Onde |
|---|---|
| Migração `V9__terrain.sql` (V9 estava livre; V1 a V8 existiam) | `src/main/resources/db/migration/` |
| Carga idempotente `POST /api/admin/terrain/runs` (ADMIN) | `TerrainAdminController`, `TerrainLoadServiceImpl` |
| Leitura `GET /api/pontos/{slug}/terrain` | `TerrainController`, `TerrainQueryServiceImpl`, `TerrainResponseDTO` |
| Colunas `terrain_*` e proveniência no export | `TerrainExportColumnsImpl` + 3 linhas em `DataExportServiceImpl` |
| Testes | `TerrainIntegrationTest` (11 testes, Testcontainers) e fixtures em `src/test/resources/terrain/` |

## Decisões e desvios do ADR 0008 (para revisão)

1. **`point_terrain_curve.m3_per_cm` é nulável.** O ADR diz `not null`, mas o CSV traz vazio no nível 0 de cada ponto (dV/dh indefinido no fundo; 4 de 164 linhas). Gravar 0 seria inventar dado.
2. **Idempotência sem coluna nova.** O ADR não tem coluna para o hash dos arquivos de entrada. Guardei `inputSha256` (SHA-256 da concatenação dos hashes de `ponto_terreno.csv`, `.meta.json` e `curva_cota_volume.csv`) dentro de `terrain_run.parameters`, com índice único parcial sobre `parameters->>'inputSha256'`. Se preferir coluna própria, é uma migração trivial antes de aplicar em produção.
3. **Mesmo conteúdo reenviado não muda o vigente.** Devolve 200 com `created=false` e o run existente. Reverter para uma carga antiga exige conteúdo diferente (ou ação manual); a decisão é conservadora.
4. **Colunas únicas**: cota da rua = mediana no raio de 3 m; declividade = janela de 100 m (proposta do ADR; configuráveis em `mapi.terrain.elevation-radius-m` / `slope-window-m`, e a escolha vai em `parameters.selected`). Área contribuinte = `area_contribuinte_m2` (snap 10 m). A sensibilidade ao snap (0/5/10/20 m) e as flags de borda vão em `diagnostics`.
5. **Slug do CSV sem ponto em `flood_points`** é ignorado e listado em `slugsNotFound`. Ponto em `flood_points` ausente do CSV responde `NO_DATA` no GET (sem linha em `point_terrain`).
6. **Diretório** vem de `mapi.terrain.dir` (padrão `../terreno`); o endpoint não aceita caminho no corpo (evita leitura arbitrária de arquivos).
7. **Vertical**: `vertical_reference = 'datum do MDT não confirmado (hipótese: altitude normal, Imbituba/REALT 2018)'`, `vertical_reference_adr = '0009'`; o GET devolve `tideComparabilityWarning` fixo. Nenhuma coluna ou cálculo de maré.
8. A data exata do voo não existe na fonte (só o ano, 2016, como no ADR); `flight_year` apenas.
9. `ExportManifestDTO` ganhou o componente `terrain {runId, vrtSha256}` (nulo sem carga). `datasetId` continua sendo o SHA-256 do CSV final, que agora inclui as colunas novas.

## Export (isolamento para o merge com `feat/pipeline-fontes`)

A mudança em `DataExportServiceImpl` é um campo, uma chamada (`terrainColumns.append(generateCsv(data))`) e um argumento no construtor do manifesto. `generateCsv` e seu cabeçalho não foram tocados (o teste existente de cabeçalho continua valendo); as colunas são anexadas depois. Conflito provável apenas se a outra branch editar essas mesmas linhas de `exportCsv` ou a assinatura de `ExportManifestDTO`.
Novas colunas, ao final: `terrain_run_id, terrain_street_elevation_m, terrain_mean_slope_pct, terrain_depression_depth_m, terrain_contributing_area_m2`. Vazias se `NO_DATA` ou sem registro.

## Medição

| | integracao/fase-2 | esta branch |
|---|---|---|
| Testes | 127 | 138 (+11), 0 falhas |
| Cobertura de linhas (JaCoCo 0.8.12 via CLI) | 51,1% | 52,8% (2361/4472) |

Comando: `./mvnw -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent clean test org.jacoco:jacoco-maven-plugin:0.8.12:report` (os avisos de instrumentação de classes do JDK 25 são ruído).
Os 11 testes cobrem: V9 aplicada e índices; proveniência/valores da carga; 2 cargas do mesmo CSV = 1 run; nova carga troca o vigente e preserva o histórico; GET OK, NO_DATA e 404; POST 403 para USER, 201 e depois 200; diretório sem arquivos = 400; export com colunas e hash do VRT no manifesto; export sem carga.

## Como usar (sem aplicar em banco real)

1. `./mvnw spring-boot:run` com `MAPI_TERRAIN_DIR` (ou `mapi.terrain.dir`) apontando para `/home/lucas/Documents/Projetos/MAPI/terreno`.
2. `POST /api/admin/terrain/runs` como ADMIN. Reexecute após a validação F3-GEO-07: se os valores mudarem, vira um novo run vigente.

Pendência: nenhum teste sobe o POST com o CSV real (fora do git); as fixtures são recortes do real (2 pontos OK + JABOATAO_CENTRO).
