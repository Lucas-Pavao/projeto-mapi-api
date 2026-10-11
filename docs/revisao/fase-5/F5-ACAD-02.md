# F5-ACAD-02 — Reprodutibilidade da avaliação F1 (roteiro, extração e regressão)

- Data: 2026-10-11 · Agentes 3 e 8 (Equilibrado) · Repositório `projeto-mapi-ai`, ramo `feat/avaliacao-f1` (enviado ao GitHub, 3 commits)
- Regra cumprida: nenhuma análise nova sobre rótulos; pré-registro intocado; o trabalho só refaz o que já existia e confere hashes. Rede de Petri fora do escopo.

## Entregas

| # | Entrega | Commit | Prova |
|---|---|---|---|
| 1 | `avaliacao/dados/extrair_entradas.sql` e `avaliacao/extrair_entradas.sh` | `144c80b` | Rodada contra o banco local: saída com SHA-256 `ef6772b73e34b6d73b167af5140005c0f7a5242e488e7ce3c3e023f5cf032e76` (igual ao arquivo citado na PRONTIDAO; 131 633 linhas). Lê só `observation` e `flood_points`. O `station_id` do archive é `lat,lon` com 4 casas, e a junção usa isso. A consulta de rótulos (Apêndice B) rodada pelo mesmo script também deu `3753ada8…` |
| 2 | `avaliacao/reproduzir.sh {congelado,fontes}`, `HASHES.sha256`, `dados/HASHES.sha256`, `avaliacao/README.md` | `abbd831` | `congelado`: hashes das entradas, escores `4615d416…`, resultado `dcf020cb…` (também byte a byte) e 27 testes passando. Adulterar um byte da chuva faz o script falhar na etapa 1. `fontes`: extração, escores e resultado reproduziram os mesmos hashes no banco local |
| 3 | `avaliacao/tests/test_regressao_f6ddacf.py`, `regressao_f6ddacf.json`, `congelar_regressao.py` | `a848410` | 17 testes novos (determinismo do CSV inteiro, R1–R8). Teste de sanidade: alterar o limiar do B3 e o denominador de R fez 8 falharem; o código foi restaurado |

Testes do ramo: **27 passaram** em 2,8 s (10 de `test_balde`, os de `test_avaliar` e os 17 novos), com as entradas congeladas em `AVALIACAO_F1_DIR`.

## Pontos a saber

1. **R8 foi adaptado.** A PRONTIDAO pede o "primeiro dia de 2025 com 0 mm", mas `escores_cegos.csv` termina em 2024-12-31. Usei o primeiro dia com 0 mm nas 24 h dos 4 pontos (regra que só olha a chuva) no período de aquecimento: **2021-04-02**. O teste confere que esse dia é mesmo o primeiro. Nesse dia, nas variantes com `f > 0`, `S = 0`, `R = 0` e sem alerta; B3 e B0 não alertam. A variante `f = 0` guarda 288,5 mm (só armazena; `R = 0`). B3-24h tem escore 2,1 por incluir a véspera, e por isso o escore dele não entra na invariante (o alerta entra). A PRONTIDAO diz "30 variantes"; são 24 de M1, conforme a errata.
2. **Os testes não afirmam desfecho.** Não há `assert alerta(dia) == 1` na versão central. R6 testa a função `episodios` (13 em P_principal, 17 nos 18 dias) com as datas copiadas de `resultado_etapa2.json`, não do arquivo de rótulos. O texto do teste declara que protege contra mudança de código e não mede acerto, e que os casos foram escolhidos conhecendo os resultados.
3. **Entradas continuam fora do git** (`AVALIACAO_F1_DIR`, padrão `../avaliacao-f1`). Sem elas, os testes dependentes são pulados com a razão. Falta a cópia fora da máquina (pré-requisito 5 da PRONTIDAO §1.2, com o Lucas).
4. **Modo `fontes`: o que depende de fora.** Dump pós-carga `MAPI/snapshots/tide_db-2026-10-10-pos-carga.dump` (SHA-256 `24aa31ced046b85fe3fddda1fd9fe2d86102635f39bf1412a79f489db0e7f2d0`), restaurado como na nota `2026-10-10-troca-de-imagem.md`. O backfill 2021-02→09 **não está nesse dump** e se refaz com dois `POST /api/admin/ingestion/runs` (`OPEN_METEO_ARCHIVE`, `REFETCH`, 2021-03-31→07-01 e 2021-07-01→10-01), um de cada vez (runs originais `902c2b09…` e `8cba199e…`). O script avisa se faltam horas. Terreno e xlsx do INMET não são refeitos (zips do PE3D sem termo). O refetch do Open-Meteo pode vir diferente; isso seria achado, e o script sai com código 3 e descreve a divergência.
5. **Lacunas.** (a) Verificado no banco local que já tem o backfill; **não** testei restaurar o dump em banco vazio (pré-requisito 2 da PRONTIDAO, "dump depois do backfill", segue com Dados). (b) Versões travadas por `requirements.txt` e Python 3.12 conferidos pelo script, mas **sem hashes de pacote** nem contêiner; o freeze completo é gravado em `pacotes-instalados.txt` a cada execução. (c) A máquina não tem `python3-venv` nem `pip`; os testes rodaram com `REPRO_VENV` apontando para o venv existente (`venv-f1`). Em máquina normal o script cria `.venv-repro/`, mas esse caminho não foi exercitado aqui.

## Banco

`docker compose -p projeto-mapi-api ... up -d --no-deps postgres` (já estava de pé); antes de parar, `pg_stat_activity` mostrou só o worker ocioso do Timescale; `stop postgres` executado ao final.
