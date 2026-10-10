# F2-BACK-15 — Adaptadores ERA5-Land (umidade do solo) e Open-Meteo Marine (nível do mar)

- Branch: `feat/pipeline-era5-marine` (a partir de `integracao/fase-2`, e9ccd46). Sem push.
- Status: **verificação da documentação feita; implementação NÃO feita.** (b) é inviável pela fonte; (a) é viável, mas exige migração nova (proibida sem aprovação). Nenhum código, migração, teste ou export alterado.
- Pedido de aprovação: autorizar a migração `V10` abaixo para liberar (a).

## 1. Verificação da documentação (consultada em 2026-10-10 via WebFetch)

### (a) Umidade do solo ERA5-Land
Fonte: https://open-meteo.com/en/docs/historical-weather-api

| Item | Conclusão (trecho literal entre aspas) |
|---|---|
| Variáveis | `soil_moisture_0_to_7cm`, `soil_moisture_7_to_28cm`, `soil_moisture_28_to_100cm`, `soil_moisture_100_to_255cm` ("Soil Moisture (0-7 cm)" etc.) |
| Unidade | "m³/m³", "Average soil water content as volumetric mixing ratio" |
| Resolução temporal | ERA5-Land "Hourly" |
| Resolução espacial | 0,1° (~11 km) |
| Período | "1950 to present" (cobre os 5 anos de treino) |
| Latência | "Daily with 5 days delay" (por isso a feature 22 é "não" em produção, como na A.2.3) |
| Fuso | padrão "GMT" ("Not set (GMT+0)"); usar `timezone=GMT` explícito |
| Seleção do modelo | a página lista "ERA5-Land" entre os modelos de reanálise, mas **não mostra o nome do parâmetro** (`models=era5_land` é o nome esperado, não confirmado pela página). Confirmar com uma chamada real controlada antes de implementar; sem `models`, vale "Best match", que não garante ERA5-Land |
| Rótulo da hora | a página não diz explicitamente; estado do solo tratado como instantâneo (`INSTANT`), como as demais variáveis de estado não acumuladas |
| Termos / limites | a página oferece licença "Non-Commercial / Commercial / Self-Hosted"; chave só para uso comercial. **Não cita rate limit** (nenhum número a registrar) |

### (b) Nível do mar (Marine API)
Fonte: https://open-meteo.com/en/docs/marine-weather-api

- `sea_level_height_msl`: "Sea Level Height including tides (above global mean sea level)"; "accounts for ocean tides, the inverted barometer effect, sea surface height". **Inclui a maré astronômica; não é só o resíduo.** O resíduo da feature 21 exigiria subtrair a tábua da DHN (referência vertical diferente: MSL global vs. NR da DHN, ADR 0009).
- Modelo: MeteoFrance SMOC Currents, Tides, 0,08° (~8 km), horário. "Accuracy is limited in coastal areas" e "not suitable for coastal navigation".
- Série histórica: "MeteoFrance SMOC Currents, Tides ... Hourly | Historical start January 2022". ERA5-Ocean (1940 a hoje, 0,5°, 5 dias de atraso) está na tabela, mas a página **não afirma que ele fornece `sea_level_height_msl`**.
- Período disponível: janeiro/2022 a outubro/2026 = cerca de 4,8 anos, **abaixo dos 5 anos de treino desejados**, e em resolução de ~8 km, limitada na costa.
- Limites: a página não cita rate limit; exige atribuição e licença não comercial gratuita.

**Conclusão (b): NÃO implementar**, conforme a regra do pedido. Motivos: histórico < 5 anos; variável inclui a maré (resíduo exigiria a tábua em referência diferente, ADR 0009); precisão costeira limitada pela própria fonte. A feature 21 segue "N (histórico a verificar)" na A.2.3; o que a documentação dá é a resposta "histórico insuficiente (~4,8 anos), desde 2022-01". Alternativa a decidir pelo autor: resíduo a partir de marégrafo (fora do escopo desta tarefa).

## 2. Por que (a) parou: migração imprescindível

O pipeline lê `variable_catalog` e `source_registry` do banco. Em `ValidationService` (linha 59-61), variável fora do catálogo é rejeitada como `UNKNOWN_VARIABLE`, e `observation_hourly_v` faz `JOIN variable_catalog`. Logo, sem uma linha nova em `variable_catalog` (e, se for usada uma fonte própria, em `source_registry`) nada é consolidado nem aparece na view. Não existe caminho sem migração que preserve o ADR 0003 (Flyway como fonte única do schema); inserir por código em runtime violaria o ADR.

### Migração proposta (aguarda aprovação; só DML, aditiva)

```sql
-- V10__era5land_soil_moisture_catalog.sql
INSERT INTO variable_catalog
  (variable, canonical_unit, min_value, max_value, range_source, expected_resolution,
   interval_convention, hourly_aggregation)
VALUES
  ('soil_moisture_0_7cm', 'm³/m³', 0, 1,
   'física: fração volumétrica de água (0 a 1); fonte da unidade: Open-Meteo historical-weather-api, 2026-10-10',
   'PT1H', 'INSTANT', 'LAST');
-- Reaproveita a fonte OPEN_METEO_ARCHIVE (UTC, VERIFIED): nenhuma linha nova em source_registry.
```

Justificativa de agregação: umidade do solo é **estado**, não fluxo: `LAST` (último valor da hora), `INSTANT`; soma não tem sentido físico e média de uma única leitura horária equivale a LAST. Como a resolução já é horária, a view devolve o próprio valor.

## 3. Plano de implementação após aprovação (esforço P/M)

1. V10 acima. Camada 0-7 cm apenas (feature 22); as outras três ficam fora até haver feature que as use.
2. Estender `OpenMeteoArchiveAdapter`/`OpenMeteoArchiveHttpClient` (mesmo cliente com timeout `open-meteo-archive`, `timezone=GMT`, hash do corpo, `utc_offset_seconds=0` conferido): acrescentar `soil_moisture_0_to_7cm` em `VARIABLES`/`HOURLY` com o parâmetro de modelo `era5_land` confirmado. Alternativa: chamada separada só do solo, para que `models=era5_land` não mude as demais variáveis (preferível: não altera o hash/valores já carregados); nesse caso, adaptador novo `OpenMeteoEra5LandAdapter` com fonte `OPEN_METEO_ERA5LAND` exigiria também linha em `source_registry` (V10 maior). Decisão do autor.
3. Testes sintéticos no formato oficial: T1 (idempotência: segunda carga igual = `raw_unchanged`, nada novo), T6 (valor na borda da hora e `[from,to)`), faixa 0-1 (1,2 em quarentena `OUT_OF_RANGE`).
4. Export: `soil_moisture_0_7cm` e `soil_moisture_0_7cm_missing` no fim do CSV, sem tocar nas colunas existentes (mudança isolada em `DataExportServiceImpl`, porque `feat/export-rotulo-pipeline` mexe no mesmo arquivo).
5. Uso em produção: feature 22 não vale em produção (latência de 5 dias); ficar claro no manifesto do export.

## 4. Medidas
Nenhum código alterado: linha de base mantida (151 testes, 59,0% de linhas em `integracao/fase-2`, segundo o pedido); `./mvnw clean test` não foi reexecutado por não haver mudança de código.
