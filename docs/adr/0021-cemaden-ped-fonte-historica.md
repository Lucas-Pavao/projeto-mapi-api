# 0021 — Cemaden PED como fonte histórica de chuva sub-horária

- Status: aprovado (2026-11-09, Lucas: decisões 1 e 2 da etapa 2, com ajustes)
- Data: 2026-11-09
- Decisores: Claude (autor), Lucas
- Tarefa: F6-DADOS-02

## Contexto

Nenhuma fonte carregada alcança as intensidades que alagam rua: pico horário de 21,1 mm/h na reanálise e 41,0 mm/h na ANA a 11–20 km, contra a capacidade de projeto de 60,1 mm/h (`docs/EVENTOS-DE-CHUVA.md`). Com o raio de 5 km do ADR 0018, os cinco pontos caem na reanálise. O Cemaden tem de 5 a 9 estações a até 5 km de cada ponto (cadastro da PED, 2026-11-09; `docs/extratos/cemaden-ped.md`), com histórico desde 2013–2014.

Levantamento da etapa 1 (`docs/extratos/cemaden-ped.md`):

- A rota `/pcds/dados_pcd` devolve até 30 dias de uma estação na hora, sem agendamento. O agendamento (`/controle-agendamento/pcds-dados-historicos`) leva de minutos a 3 dias e o arquivo expira em 30 dias.
- O bloco de chuva tem duração variável: 60 min no seco; 10 min com chuva em 2015; de 10 a 60 min com chuva de 2021 em diante.
- HTTP 202 com "Nenhum resultado foi encontrado" = sem dado no intervalo.
- O arquivo declara UTC no cabeçalho.

## Opções consideradas

### A — Agendamento (arquivo histórico)
A favor: uma requisição por estação e período longo. Contra: espera de até 3 dias, expiração em 30 dias, fluxo de estado a mais (pedido, espera, download) sem ganho de conteúdo.

### B — `dados_pcd` em janelas de 29 dias, 10 requisições por minuto, retomável por janela
A favor: síncrono, mesmo conteúdo, retomável. Contra: ~2 000 requisições, horas de carga.

### C — Bloco com duração fixa de 10 min
A favor: simples. Contra: falso desde 2021; a hora com um bloco de 60 min e a hora com seis de 10 min ficariam indistinguíveis.

## Decisão

Opção B, fonte `CEMADEN_PED` no pipeline do ADR 0006, fuso UTC verificado.

- **Duração do bloco** = intervalo até a leitura anterior da mesma estação. Cada requisição começa 70 min antes da janela; a anterior, se existe a até 70 min, está sempre na resposta, então a duração não depende de onde a janela começa. Ela vai para `observation.interval_start_utc`.
- **Bloco mais longo que 60 min vai para a quarentena** (`GAP / AFTER_GAP`): a leitura veio depois de uma lacuna e a chuva pode ter caído em qualquer ponto do intervalo. Sem anterior em 70 min, o bloco é declarado com 71 min e também cai na quarentena.
- **Hora**: a regra da V22 continua (soma dos blocos com fim na hora; hora sem bloco = lacuna). A `observation_hourly_v` passa a declarar, por hora, `n_readings` (blocos), `blocks_covered_min` (soma das durações) e `first_block_start_utc`. A exigência de 6 blocos por hora foi descartada: só 3–6% das horas de maio/2022 têm seis leituras.
- **Qualificação**: o código vai para `observation.qualifier`. `0000` e `0004` sem filtro. `0002` vai para a quarentena (`QUALIFIER / SUSPECT_QUALIFIER`): decisão do Lucas em 2026-11-09, depois da carga de 5 anos mostrar 70 blocos `0002`, todos acima de 25 mm e até 255,5 mm, contra no máximo 26,9 mm com `0004` (`docs/extratos/cemaden-ped.md`, seção 5). Reversível por REVALIDATE. Significado em consulta ao Cemaden.
- **Limite**: uma requisição a cada 6 s (10 por minuto), contando a emissão de token, sem rajada.
- **Token**: renovado antes de vencer (`exp` do JWT) e depois de 401/403; expiração é erro recuperável.
- **Retomada**: a janela entra em `ped_fetch_window` na mesma transação do bruto e da observação; a carga seguinte pula as janelas gravadas.
- **Citação obrigatória**: "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC" (`docs/FONTES-E-LICENCAS.md`).

## Consequências

- Melhor: chuva medida a até 5 km de todos os pontos, com resolução de bloco e duração conhecida.
- Pior: horas de carga; leitura depois de lacuna é perdida (a chuva dela não entra na hora). Um bloco que atravessa a virada da hora entra inteiro na hora do seu fim; `blocks_covered_min` mostra quando isso acontece.
- Muda: `DraftValue` e `FetchedBatch` ganham um campo opcional (as fontes antigas não mudam); a view horária ganha duas colunas no fim.
- Medida: quarentena por motivo, cobertura por estação e ano, número de pontos com chuva medida.

## Condições que reabririam a decisão

- O Cemaden informar que `0002` não é dado suspeito (devolve os 70 blocos à série) ou que outro código é.
- A `dados_pcd` passar a recusar períodos antigos.
- Evidência de que o bloco não termina no carimbo.

## Emenda de 2026-11-16: carga periódica

- Status: aprovado (pedido do Lucas em 2026-11-16: "retoma a carga da PED periódica")

**Contexto.** A carga histórica foi rodada à mão e parou em 2026-10-07 23:50 UTC (`SELECT max(ts_utc) FROM observation WHERE source = 'CEMADEN_PED'`, 2026-11-16). A série de chuva dos pontos (`point_rain_hourly`, ADR 0022) parou junto, em 2026-10-08 00:00. A coleta ao vivo (`CEMADEN`, via APAC) seguiu, mas não entra na série do ponto.

**Decisão.** `CemadenPedIncrementalTask` roda a cada hora (`mapi.ingestion.cemaden-ped.incremental-delay-ms`, padrão 3 600 000), no `taskExecutor`, sem sobrepor a si mesma:

1. baixa `[última leitura PED gravada − 6 h, hora cheia atual)` pelo pipeline (REFETCH, BACKFILL), com o mesmo cliente e o mesmo limite de uma requisição a cada 6 s dos acumulados recentes (ADR 0023). A sobreposição de 6 h `[sem fonte]` cobre blocos que a PED completa com atraso; leitura igual não vira nova versão;
2. se o run termina `OK` ou `PARTIAL`, recalcula a chuva dos pontos (`PointRainService.rebuild`) **desde o dia 1 do primeiro mês tocado**: a checagem espacial é por mês, e recalcular só a cauda trocaria a checagem do mês inteiro por uma de poucas horas.

Banco sem PED recente: a carga periódica volta no máximo 60 dias (`incremental-max-catch-up-days`); o histórico continua pelo `POST /api/admin/ingestion/runs`.

**Consequências.** A checagem do mês corrente passa a existir e é provisória: muda a cada ciclo até o mês fechar. Um ciclo com atraso de 40 dias faz 2 janelas × 46 estações ≈ 92 requisições (≈ 9 min a 6 s); um ciclo normal, 46 requisições (≈ 5 min). Desligar: `mapi.ingestion.cemaden-ped.incremental-enabled=false`.
