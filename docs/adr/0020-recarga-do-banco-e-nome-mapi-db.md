# 0020 — Recarga do banco e troca de nome para `mapi_db`

- Status: aprovado (2026-11-08, Lucas)
- Data: 2026-11-08
- Decisores: Claude (autor), Lucas
- Tarefa: F6-DADOS-01

## Contexto

O banco de desenvolvimento (`tide_db`, 1 491 MB) tem só dado ao vivo na série consolidada; o histórico de 5 anos está no snapshot `2026-10-10-pos-carga`. As fontes novas (ADR 0018 e 0019) e a migração do rótulo mudam o significado das séries por ponto. O nome `tide_db` vem de quando o projeto era só tábua de maré.

## Opções consideradas

### A — Migrar no lugar, sem apagar
A favor: nada se perde. Contra: mistura série recalculada com série antiga; o antes e depois fica difícil de auditar.

### B — Banco novo `mapi_db` em volume novo, recarga do zero; `tide_db` e volume antigo ficam até aprovação
A favor: antes e depois lado a lado; volta atrás trocando a URL; o nome novo entra sem renomear banco vivo. Contra: dobra o espaço em disco enquanto os dois existirem (`/` com 11 GB livres em 2026-11-08).

## Decisão

Opção B.

**Preservado (copiado do `tide_db` antes de carregar):** `users` (2 contas), `alert_rule`, `flood_report`, `flood_points` e vínculos, `terrain_run`/`point_terrain`/`point_terrain_curve` (ou recarga pelo `POST /api/admin/terrain/runs`, idempotente), `linkage_config`, `label_filter`.

**Perdido no banco novo (fica no dump e no banco antigo):** `sensor_data`, `weather_data` e `flood_predictions` legados; `alert` e `alert_state` (histórico de alertas do desenvolvimento).

**Ordem da recarga** (assíncrona, uma execução por vez, esperando `status` final; `docs/INGESTAO.md`):
1. Migrations até a última V aprovada.
2. Estações: inventário ANA e CEMADEN (e APAC histórica, se aprovada).
3. Chuva: `OPEN_METEO_ARCHIVE`, `OPEN_METEO_ERA5LAND`, `ANA_SOAP`, CEMADEN histórico (com token), APAC histórica diária; blocos de 12 meses.
4. Maré: `DHN_TABUA_PE02`, `TABUAMARE_PE02` (15 chamadas/60 s; blocos de 12 meses com pausa de 60 s), por último `TIDE_PE02_HOURLY`.
5. Vínculo estação↔ponto (`linkage_config`, ADR 0018).
6. Rótulos: CKAN e fontes do ADR 0019; depois o casamento com os pontos.
7. Terreno.

**Antes:** dump `tide_db-2026-11-08-pre-recarga` (SHA-256 `c8e8e068…`) com cópia no Drive. **Depois:** relatório por fonte (linhas, período, lacunas, quarentena por motivo), comparado com o `pos-carga`, e dump `mapi_db-<data>` com manifesto.

**Voltar atrás:** apontar `POSTGRES_URL` para `tide_db` (o banco e o volume continuam lá) ou restaurar o dump.

## Consequências

- Melhor: banco com nome do projeto, histórico completo e proveniência nova de ponta a ponta.
- Pior: horas de carga; espaço em disco duplicado até apagar o antigo; a reprodução da F1 precisa restaurar o `pos-carga` num banco `tide_db` (os scripts pré-registrados não mudam).
- Apagar `tide_db` e o volume antigo é outro passo, com nova aprovação.

## Condições que reabririam a decisão

- Falta de espaço em disco para os dois bancos: aí se apaga `tide_db_hist` (cópia de trabalho) primeiro, com aprovação.
- Recarga com divergência inexplicada contra o `pos-carga` em alguma fonte.
