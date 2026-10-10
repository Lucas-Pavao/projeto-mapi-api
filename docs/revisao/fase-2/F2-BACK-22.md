# F2-BACK-22 — desempenho do export com a maré do pipeline e `tide_rising` vazio

Branch `fix/export-mare-desempenho` (a partir de `integracao/fase-2` @ `0497f3a`). Sem migração. Testes: `./mvnw -q clean test` verde, 200 testes (linha de base 198; +2 em `TideFeatureCalculatorEquivalenceTest`).

## 1. Desempenho

### Gargalo
`DataExportServiceImpl` chamava `TideFeatureCalculator.at(t, hourly, extremes)` uma vez por linha (43 801 linhas). Em cada chamada:
- `hoursToNextHigh` varria a lista de extremos do início até a primeira preamar após `t`;
- `rangeOfLocalDay` varria **todos** os extremos (≈7 200 em 5 anos) e convertia cada um para `America/Recife` (`atZone`) para achar os do dia de `t`.

Ou seja, ≈43 800 × 7 200 ≈ 315 milhões de conversões de fuso por export: custo O(linhas × extremos). As leituras do banco (view horária, extremos) são feitas uma vez por chamada e já estavam medidas em F2-BACK-17 (< 0,7 s). Não rodei async-profiler/JFR: a contagem acima, a medida antes/depois e a prova de equivalência bastam para localizar a causa; o tempo restante (≈2,4 s) é o SQL e a montagem da timeline.

### Correção
`TideFeatureCalculator.index(extremes)` constrói, uma vez por chamada do export, (a) o vetor de instantes das preamares, consultado por busca binária (primeira com `ts > t`), e (b) um mapa dia civil de Recife → {mín, máx, contagem}. `at(t, hourly, Index)` fica O(log m). A sobrecarga `at(t, hourly, List)` continua existindo e delega ao índice. Resultado idêntico à varredura linear para lista cronológica.

### Prova de que nenhum valor mudou
- `TideFeatureCalculatorEquivalenceTest.indiceNaoMudaValoresEMesmoHashDoResultado`: 2 880 horas sintéticas (maré semidiurna quantizada em 0,1 m, com lacunas), comparação campo a campo com a implementação anterior (cópia literal no teste) e SHA-256 igual da serialização das duas versões, exceto `tide_rising` com derivada nula (item 2).
- No banco real: o CSV novo difere do de F2-BACK-21 em **449 linhas, somente nas colunas `tide_rising` e `tide_rising_missing`** (nenhuma outra coluna de nenhuma linha). Comparação com `csv` do Python, 43 801 linhas e cabeçalho idêntico.

### Medida (banco real, mesmo endpoint/período do F2-BACK-21)
`GET /api/export/ia-dataset/AGAMENON_DERBY/csv?days=1825&to=2026-10-01T00:00:00Z`, 1ª chamada fria (API recém-subida) à parte e 10 sequenciais (`F2-BACK-22-bench.txt`).

| Medida | F2-BACK-21 | Agora |
|---|---|---|
| 1ª chamada fria | 9,49 s | 2,83 s |
| Mediana (10) | 8,62 s | **2,36 s** |
| p95 (= máx. de 10) | 8,82 s | **2,47 s** |
| Mín. | 8,54 s | 2,33 s |
| Tamanho do CSV | 10 658 360 B | 10 658 809 B (+449 B: o `1` de `tide_rising_missing` virou `0` e o vazio virou `0`/`1` em 449 linhas) |
| Hashes distintos em 11 chamadas | 1 | 1 |

Gatilho do ADR 0006 §9 (p95 > 5 s): **não excedido** (2,5 s).

## 2. `tide_rising` vazio

### Causa
Não era erro de carga: `rising` era nulo quando a derivada central `(h(t+1) − h(t−1))/2` valia exatamente 0,0. Isso acontece na hora de preamar/baixa-mar, em que a altura quantizada em 0,1 m é igual nos dois vizinhos. Mas `tide_slope_m_h` ficava preenchido (0,0) e `tide_slope_m_h_missing = 0`, então a ausência era uma regra implícita.

Correção ao relatório F2-BACK-21: a flag `tide_rising_missing` **já** marcava 1 nas células vazias (449 de 449 no CSV de F2-BACK-21; "0" no relatório foi leitura equivocada). O defeito real é de definição, não de marca ausente: 449 de 43 801 linhas (1,0%), `tide_rising` vazio por regra não declarada.

### Regra nova (declarada no manifesto, `TIDE_NOTE`)
- derivada central > 0 → 1; < 0 → 0;
- derivada nula: desempata pelo passo à frente `h(t+1) − h(t)` (> 0 → 1; < 0 → 0); a maré prevista pode olhar à frente (A.2.2 de MODELAGEM-IA);
- nulo, com `tide_rising_missing = 1`, só se faltar insumo ou o passo também for 0 (plateau).

No CSV real, as 449 linhas passaram de vazio/1 para 232 com valor 1 e 217 com valor 0, com `_missing = 0`; restam 0 vazios. Testes: pico → 0, vale → 1, plateau → nulo, sem hora seguinte → nulo (`derivadaNulaEmPreamarEBaixaMarDesempataPeloPassoAFrente`).

Efeito para a modelagem: nos 449 pontos de extremo, `tide_rising` passa a ser o sentido imediatamente posterior, não "estável". Reprocessar datasets já exportados (o `datasetId` muda).

## 3. docs/INGESTAO.md
Editado no repo principal (sem commit): runs assíncronos, espera por `GET /api/admin/ingestion/runs/{id}` até sair de `RUNNING`, ordem da maré, rate limiter da TabuaMare (15/60 s), função `run` em `bash` com `curl`/`jq`.

## 4. Banco e artefatos
- Stack: `docker compose -p projeto-mapi-api -f <worktree>/docker-compose.yml up -d --build --no-deps postgres mapi-api`; parado com `stop`. Volumes: 5 antes e depois (nenhum novo, nada apagado); migrações V1–V12 sem alteração.
- Credencial: criei mais um ADMIN, `revisao-admin-f222` (via `ADMIN_BOOTSTRAP_*`; +1 linha em `users`), porque a senha de `revisao-admin-f221` não estava no relatório nem no ambiente. Senha aleatória, fora do repositório.
- SHA-256 do CSV novo: `8cf0ff80661516570e6ea5ca68b1b71e4909c18570f15fea429aa6c7cb8dc124` (contra `8f3e2702…` de F2-BACK-21). A diferença é exatamente as 449 linhas acima. O CSV novo não foi gravado em `MAPI/exports/` (fica só na sessão); o autor decide se substitui o anterior.

## Pendências
1. Decidir se a regra do desempate pelo passo à frente é a desejada (alternativa: manter nulo e marcado, ou um valor "estável" fora de 0/1).
2. Regerar e republicar o CSV de AGAMENON_DERBY (e demais pontos) com o novo `datasetId`.
