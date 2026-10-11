# Linha de base de medidas — 2026-10-10 (antes de qualquer mudança de código)

Tarefas F1-BACK-04 e F1-BACK-05. Código: `main` em `accba26`, mais a correção de slug do k6
(`8ec2f5f`, `54275ea`) na segunda rodada. Banco: o do snapshot `docs/snapshots/2026-10-10-pre-revisao.md`
(quase vazio: 555 leituras de sensor e 40 de clima), mais o que os jobs coletaram durante o teste.

## OpenAPI
`docs/openapi/openapi-2026-10-10.json` — 45 caminhos, 46 operações, 42 schemas. Coletado de
`/v3/api-docs` com a imagem reconstruída a partir do código atual (a imagem anterior, de 2026-09-06,
não tinha o commit `0554073`).

## Cobertura de teste (JaCoCo 0.8.12 via CLI, JDK local 25; projeto alvo Java 21)
- Linhas **26,1%** (837/3 208) · ramos **17,6%** · instruções 24,5% · 20 testes, 0 falhas.
- Menores: `mapper` 0,0% · `service.export.impl` **0,6%** · `service.tide.impl` 2,2% ·
  `service.flood.impl` 4,2% · `service.sensor.ana.impl` 4,2%.
- Dado bruto: `jacoco-2026-10-10.csv`.

## Latência (k6 0.54.0, `loadtest/stress-test.js`, rede do compose, sem Prometheus)

| Endpoint (tag `name`) | n | p50 ms | p95 ms | máx ms | Rodada |
|---|---|---|---|---|---|
| pontos | 9 325 | 2,3 | 4,0 | 15,1 | 1 |
| sensors/latest | 9 325 | 1,6 | 2,5 | 13,3 | 1 |
| sensors/inventory | 9 325 | 1,4 | 2,3 | 19,3 | 1 |
| sensors/{id}/history | 9 325 | 1,1 | 1,9 | 10,9 | 1 |
| tabua-mare/states | 9 325 | 0,9 | 1,9 | 5 535,4 | 1 |
| tide/harbors | 9 325 | 1,0 | 1,9 | 10,0 | 1 |
| marine | 9 325 | 0,8 | 1,5 | 792,1 | 1 |
| sensors/{id}/latest | 9 325 | 0,8 | 1,4 | 10,4 | 1 |
| sensors/ids | 9 325 | 0,7 | 1,2 | 8,9 | 1 |
| weather | 9 325 | 0,5 | 0,8 | 479,9 | 1 |
| flood-predictions (POST) | 68 / 72 | 23,2 / 23,0 | **2 984,8 / 54,5** | 5 097,9 / 5 025,9 | 1 / 2 |
| export/csv (30 dias) | 0 / 44 | — / 91,7 | — / **143,1** | — / 171,2 | 1 / 2 |

Total: 93 322 requisições na rodada 1 (330/s, 0% de falha) e 92 180 na rodada 2 (322/s).

**Limites desta medida (ler antes de citar):**
1. O banco está quase vazio. O export de 30 dias leu poucas centenas de linhas e **não representa** o
   custo com 5 anos de histórico. A medida que vale para o item 12 da matriz precisa ser refeita depois
   da carga histórica.
2. Os p95 de 1–4 ms vêm do cache (`@Cacheable`). O máximo de 5,5 s em `tabua-mare/states` e o de ~5 s
   na predição mostram o custo real quando o cache falha ou a IA está fria (sem timeout, F1-B2-01).
3. A predição variou de p95 2,98 s para 54,5 ms entre rodadas: é efeito do aquecimento da IA, não melhoria.
4. **Lacuna de medição encontrada:** na rodada 1, `heavy_export` fez 0 requisições, porque o script
   lia `id_ponto`, renomeado para `slug` em `0554073`. O limite `p(95)<15000` passou sem nenhuma
   amostra. Corrigido em `fix/k6-remote-write-env` (`8ec2f5f`), e agora o `setup()` falha se não houver slug.
