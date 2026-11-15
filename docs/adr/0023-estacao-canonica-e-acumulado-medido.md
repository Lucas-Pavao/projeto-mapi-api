# 0023 — Estação canônica por coordenada e acumulado medido do Cemaden

- Status: aprovado (autonomia da revisão, 2026-10-10; decisão registrada em `.claude/mapi/PROGRESSO.md`)
- Data: 2026-11-15
- Decisores: Claude (autor), Lucas
- Tarefa: IN1165-API-0 (Etapa 0)

## Contexto

Os pluviômetros do Cemaden chegam ao MAPi por dois caminhos: a coleta ao vivo pelo feed da APAC (`source = CEMADEN`, 47 estações) e a carga histórica pela PED (`CEMADEN_PED`, 46 estações). Em `station` a mesma estação física aparece duas vezes (contagem de 2026-11-15, `SELECT source, count(*) FROM station`).

O mapa mostra hoje o acumulado da reanálise (Open-Meteo), com pico horário de 21,1 mm/h contra 62 mm/h medidos pelo Cemaden (`docs/EVENTOS-DE-CHUVA.md`). A PED publica acumulados prontos de pluviômetro em `/pcds-acum/acumulados-recentes` (1 a 120 h).

Formato real da rota, medido em 2026-11-15 (Recife, 19 estações):

- `codibge` é obrigatório (HTTP 400 sem ele); `uf` não é aceito. Uma requisição por município.
- Por estação: `codestacao`, `acc1hr` … `acc120hr`, `datahora`. Janela sem leitura vem `null`, não `0.0`.
- `datahora` é o instante do cálculo, em UTC sem sufixo (17:27 na resposta; `date -u` 17:27). Não é a hora da última leitura.
- A resposta traz estação de outra rede (`A301`, do INMET).

## Opções consideradas

### A — Unificar pelo código da estação
A favor: 25 das 46 estações da PED têm o mesmo código na coleta ao vivo. Contra: o feed da APAC usa códigos próprios para parte das estações (`[APAC] ...`); código diferente da mesma estação física ficaria duplicado.

### B — Unificar por coordenada, até 150 m, com nome só no registro
A favor: independe do código; o raio cobre a imprecisão do cadastro com três casas decimais (~110 m). Contra: duas estações reais a menos de 150 m seriam fundidas. Mitigação: duas estações da mesma fonte nunca se fundem.

### C — Semelhança de nome como critério
Contra: o mesmo código aparece com nomes diferentes nas duas rotas (260960004A: "[APAC] Ouro Preto" na coleta ao vivo, "Jardim Fragoso" na PED, a 0 m).

## Decisão

Opção B. A PED define as canônicas; cada estação da coleta ao vivo vira apelido da estação PED mais próxima, se ela estiver a até 150 m `[sem fonte]`; senão, vira canônica sozinha. A semelhança de nome (Jaccard das palavras) vai para `station_alias.name_similarity` e para o log quando é menor que 0,5.

- **Tabelas** (V25, derivadas, refeitas por `POST /api/admin/stations/unify`): `station_canonical`, `station_alias`, `station_hour_divergence`; views `station_canonical_v` e `canonical_rain_hourly_v`.
- **Mesma hora nas duas fontes**: fica a PED (blocos de 10 min, fuso verificado na V24). Diferença acima de 0,5 mm `[sem fonte]` (duas básculas de 0,2 mm) vai para `station_hour_divergence`.
- **Acumulado medido**: `GET /api/stations/rain-accumulations`, cache renovado a cada 10 min fora da requisição, pelo mesmo cliente da carga (uma requisição a cada 6 s, limite da PED 12/min). Municípios = os dos códigos das canônicas (6 primeiros dígitos + dígito verificador do IBGE). HTTP 202 "Nenhum resultado" = município sem dado; a estação fica `NO_DATA`, nunca zero. A hora da última leitura vem da série gravada pelo MAPi (`lastReadingUtc`), não do `datahora`.
- **Acumulados por ponto**: view `point_rain_accum_v` sobre `point_rain_hourly` (ADR 0022), com 3, 6, 12 e 24 h; janela com qualquer hora sem valor dá `NULL`.
- Citação obrigatória no campo `attribution` da resposta: "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC".

## Consequências

- Melhor: uma estação física por linha no mapa; o acumulado exibido passa a ser medido, com origem declarada (`rainOrigin = MEASURED`).
- Pior: a primeira leitura depois de subir a API leva ~1,5 min (14 municípios × 6 s); até lá a rota responde 503. Uma carga da PED em andamento divide o mesmo limite de requisições e atrasa o ciclo.
- Medida: canônicas, unificadas, só PED, só ao vivo, horas comparadas e divergências, na resposta de `unify` e no log.
- `point_rain_hourly` continua lendo só a PED; a série canônica (`canonical_rain_hourly_v`) é o caminho para estender a série do ponto com a coleta ao vivo depois de 2026-10-07.

## Condições que reabririam a decisão

- Duas estações físicas distintas a menos de 150 m (hoje: nenhuma; a estação ao vivo não unificada mais próxima de uma PED está a 1 039 m).
- Divergência sistemática entre as fontes na mesma estação (taxa de horas divergentes acima de 5% `[sem fonte]`).
- A PED passar a aceitar `uf` na rota de acumulados.
