# F2-DADOS-11 — Chuva da ANA na grade horária

- Data: 2026-10-11 · Branch `feat/ana-chuva-horaria` (a partir de `integracao/fase-2`, 2e2e5bf).
- Critério fixado antes de qualquer estatística em `F2-DADOS-11-criterio.md` (commit `ea9b43c`). Depois dele só foram acrescentadas notas datadas ao fim do arquivo; o texto original não mudou.
- Nenhum rótulo de alagamento nem escore de avaliação foi lido. A inspeção das leituras grandes usou apenas a série de chuva.

## 1. Decisão

A chuva do legado da ANA (`ServiceANA.asmx`, fonte `ANA_SOAP`) é um **incremento de 15 min com carimbo no fim da janela**, `(t − 15 min, t]`. Ela passa a entrar como resolução `PT15M`, sem o qualificador `WINDOW_UNVERIFIED`, e portanto em `observation_hourly_v`. O envelope do coletor ao vivo (outra origem) continua `EVENT` com `WINDOW_UNVERIFIED`.

A verificação é **por norma mais consistência do bruto**, não empírica no carimbo (limitação L1). Há um desvio declarado do critério (L2) que o autor deve confirmar ou reverter.

## 2. Evidência documental

- Página do serviço (`https://telemetriaws1.ana.gov.br/ServiceANA.asmx?op=DadosHidrometeorologicos`): só parâmetros e "Listar os dados Hidrometeorológicos por estação". Não define `Chuva`, `DataHora`, unidade nem janela.
- Manual do Hidro-WebService, anexo I: `Chuva_Adotada [Precipitação (mm)]`, `Data_Hora_Medicao [DataHora da medição/coleta do dado]`, exemplo com leituras às 23:00 e 23:15. Dá unidade e grade de 15 min; não dá janela nem posição do carimbo.
- Norma da ANA "Procedimentos para envio dos dados hidrológicos em tempo real das estações telemétricas" (março/2012, seção 4.2, `https://arquivos.ana.gov.br/infohidrologicas/cadastro/ProcedimentosParaEnvioDosDadosHidrologicosEmTempoRealDasEstacoesTelemetricas_RevisaoMar12.pdf`): coleta em quartos de hora tendo a hora cheia como referência; "Os dados de chuva deverão ser acumulados no intervalo de transmissão. Por exemplo, para uma coleta com intervalo horário a chuva registrada às 14:00h deverá corresponder ao valor total da precipitação ocorrida entre as 13:00h e 14:00h"; data/hora "hora de Brasília sem correção do horário de verão". É esta norma que sustenta H1 (incremento, carimbo no fim). Ela vale para o operador, não descreve o endpoint legado.

## 3. Resultado dos testes do critério (bruto: `observation`, `ANA_SOAP`, 548 696 leituras, 4 estações)

| Teste | Resultado | Limite | Veredito |
|---|---|---|---|
| C1a leituras em múltiplo de 15 min | 100 % nas 4 estações; 0 fora da grade | ≥ 99 % | aprova |
| C1b intervalos ≤ 1 h que são 15 min | 99,55 % a 99,99 % | ≥ 90 % | aprova |
| C2a pares positivos em que o valor cai | 42,3 % a 45,9 % (1 829 a 5 894 pares) | ≥ 25 % | aprova (rejeita H3) |
| C2b quarto de hora com mais transições "> 0 para 0" | 1,8 % a 2,3 % | < 20 % | aprova (sem zerador fixo) |
| C2c horas com 4 leituras iguais e positivas | 0,10 % a 0,84 % das horas com chuva | < 2 % | aprova (rejeita H4) |
| C2d soma anual por estação-ano com ≥ 70 % do ano | 39167000/2024: **781 mm**; demais 1 336 a 2 738 mm; maior leitura 68,8 mm | 800 a 4 000 mm; ≤ 100 mm | **reprova em 1 estação-ano** |
| C3 carimbo início ou fim | não testável pelo bruto | — | só a norma |

C2d: 781 mm está 2,4 % abaixo do piso `[sem fonte]`. O Open-Meteo archive soma 821 mm na mesma coordenada e ano. Tabelas por estação-ano completas ficam reproduzíveis pelas consultas da seção 8.

## 4. As leituras de 68,8 mm

Pedido: dizer se são reais, acumulado de janela maior ou erro.

- A leitura de 68,8 mm é de 2026-08-21 (07:30 local, estação 39187800). Está **fora de 2021–2024**, por isso não aparece no máximo horário de 36,4 mm/h do F2-DADOS-10 §7. Outra, de 51 mm, é de 2022-01-30 (08:45 local, mesma estação): dentro do período, mas a hora dela tinha menos de 3 leituras e saiu da tabela de 3 de 4.
- Todas as leituras acima de 30 mm (68,8; 51; 36,8; 35) vêm **logo depois de uma lacuna longa** na própria série (1 dia 6 h, 1 dia 13 h 30 min, 3 dias 7 h 15 min, 28 dias 23 h), com vizinhos seguintes em zero. Na estação 39370100 em 2022-01-30 só caíram 1,8 mm na janela de 3 h; em 2026-08-21 as outras duas estações registraram 0,0 mm.
- Entre as 2 264 leituras que chegam depois de uma lacuna maior que 15 min, a média dos valores positivos é 4,15 mm contra 0,88 mm nas demais, e o máximo é 68,8 contra 27,8 mm. Fora de lacuna nenhum valor passa de 27,8 mm.
- Conclusão: **não é provável que sejam 15 min de chuva real**. O padrão é compatível com chuva acumulada no período sem dado que o registrador entrega na primeira leitura depois da falha (a norma fala em acumular "no intervalo de transmissão"), mas também com erro de sensor. Os dados não permitem separar as duas causas. O bruto não traz o campo de status do Hidro-WebService (`Chuva_Adotada_Status`) que poderia decidir.
- **Regra aplicada (V15):** a leitura da ANA cuja leitura anterior da mesma série está a mais de um passo (15 min) não entra na soma horária; a hora dela fica `NULL` por completude. A leitura segue gravada em `observation`. A regra é genérica (estrutura da série), sem datas, e foi commitada em nota do critério antes de a view ser alterada. Efeito colateral declarado: uma única leitura faltando também exclui a seguinte (a de depois da lacuna), então uma hora com um furo fica com 2 de 4 leituras. Isso exclui 2 264 leituras (0,41 %).

## 5. O que foi implementado

- `AnaSoapAdapter.extract`: para origem `ServiceANA.DadosHidrometeorologicos`, `precipitation` sai em `PT15M`, qualificador nulo; `parserVersion` `ana-soap/4`; `ruleVersion` `2026.10.4`.
- `V15__ana_chuva_horaria.sql`: coluna `source_registry.exclude_after_gap` (padrão `false`, `true` só para `ANA_SOAP`) e `CREATE OR REPLACE VIEW observation_hourly_v` com as mesmas colunas e ordem da V5, mais o filtro pós-lacuna. As outras fontes saem idênticas. A consulta de uma estação por ano roda em 178 ms (o filtro por fonte e estação desce para o índice). Pode colidir com migração de outra branch; renumerar se necessário.
- Export: colunas `ana_precipitation` e `ana_precipitation_missing` **ao fim** do CSV (antes dos `terrain_*`); fonte = pluviômetro `ANA_SOAP` mais próximo do ponto que tenha chuva `PT15M` (haversine); nota e procedência no manifesto. A estação fica a dezenas de km do ponto: é chuva regional, não no ponto.
- Regra de agregação: hora `(T − 1 h, T]` = soma das 4 leituras com carimbo em `T − 45 min … T`; leitura às `hh:00:00` pertence à hora que termina em `hh:00`; com a grade alinhada à hora cheia nenhuma janela cruza a borda; fora da grade a leitura cai inteira na hora do carimbo (não ocorreu: 0 leituras fora da grade). Hora com menos de 4 leituras (`0,8 × 4 = 3,2`) é `NULL`, nunca preenchida; zero medido é valor.
- Testes: `AnaChuvaHorariaIntegrationTest` (leitura em `hh:00:00`, janela de 15 min e fuso UTC−3, lacuna, zero medido, leitura pós-lacuna fora da soma, reprocessar não muda), 2 testes novos no `AnaSoapAdapterTest`, 1 no `DataExportIntegrationTest`. O `@MockitoBean AnaLegacyClient` foi para `AbstractIngestionTest` porque um contexto novo estourava `max_connections` do contêiner de teste. `./mvnw -q clean test`: **240 testes verdes** (linha de base 236).

## 6. Banco local e reprocessamento

`mapi-db` já estava de pé (reusado, não reiniciado); `pg_stat_activity` sem conexão de aplicação de outro agente. Só `mapi-api` foi reconstruído (`-p projeto-mapi-api`, volume `projeto-mapi-api_postgres_ha_data`); a V15 rodou no start. Cinco runs `REVALIDATE` de `ANA_SOAP`, 2021-09-30 a 2026-10-13, todos `OK`, `quarantined = 0`:

| Janela | fetched | valid | obs_inserted (chuva PT15M) | retratadas |
|---|---:|---:|---:|---:|
| 2021-09-30 a 2022-10-01 (`cea125ae-2ff8-4b8b-bc5a-4851baf31f0d`) | 64 634 | 118 957 | 62 576 | 62 576 |
| 2022-10-01 a 2023-10-01 (`514f39df-c422-4323-a223-14e81f53e1b6`) | 101 046 | 198 106 | 100 753 | 100 753 |
| 2023-10-01 a 2024-10-01 (`5e799c76-44a1-44a7-8d3c-80c815f7bd9d`) | 130 721 | 253 208 | 127 390 | 127 390 |
| 2024-10-01 a 2025-10-01 (`692b4936-d5ee-4ee6-a5bb-d9b041b907e6`) | 131 410 | 256 642 | 130 428 | 130 428 |
| 2025-10-01 a 2026-10-13 (`50e30b34-860e-45d9-9f0e-9d00a391e5c1`) | 127 926 | 238 932 | 127 549 | 127 549 |

Estado final da chuva: 548 696 linhas `PT15M` `ACTIVE` sem qualificador e 548 696 linhas `EVENT`/`WINDOW_UNVERIFIED` `RETRACTED` (trilha em `observation_revision`). Nível d'água não mudou. Quarentena aberta da ANA: 0.

## 7. Horas de chuva horária por estação e ano (2021–2024; hora UTC rotulada pelo fim, regra de 4 de 4 e leitura pós-lacuna fora)

| Estação | Ano | Horas válidas | Horas com chuva | Total (mm) | Máx. (mm/h) |
|---|---:|---:|---:|---:|---:|
| 39084000 Caricé | 2022 | 2 555 | 177 | 229 | 15,4 |
| 39084000 Caricé | 2023 | 7 829 | 924 | 1 417 | 28,0 |
| 39084000 Caricé | 2024 | 8 694 | 851 | 1 315 | 34,8 |
| 39167000 Vitória de Santo Antão Montante | 2023 | 2 375 | 216 | 178 | 13,6 |
| 39167000 Vitória de Santo Antão Montante | 2024 | 8 165 | 679 | 742 | 33,0 |
| 39187800 São Lourenço da Mata II | 2021 | 1 166 | 68 | 64 | 7,4 |
| 39187800 São Lourenço da Mata II | 2022 | 7 335 | 864 | 1 685 | 28,6 |
| 39187800 São Lourenço da Mata II | 2023 | 8 583 | 1 152 | 1 805 | 27,8 |
| 39187800 São Lourenço da Mata II | 2024 | 7 857 | 924 | 1 484 | 32,8 |
| 39370100 Engenho Maranhão | 2021 | 2 146 | 207 | 284 | 23,2 |
| 39370100 Engenho Maranhão | 2022 | 7 863 | 1 319 | 2 656 | 31,2 |
| 39370100 Engenho Maranhão | 2023 | 7 678 | 1 325 | 2 356 | 32,8 |
| 39370100 Engenho Maranhão | 2024 | 7 696 | 974 | 1 364 | 30,4 |

Totais: **79 942 horas válidas e 9 680 horas com chuva**; intensidade máxima horária **34,8 mm/h** (Caricé, hora que termina 2024-01-11 05:00Z). Contra o F2-DADOS-10 §7 (81 009 horas válidas, 9 811 com chuva, máximo 36,4 mm/h, regra de 3 de 4 sem exclusão pós-lacuna) perdem-se 1 067 horas válidas (1,3 %) e 131 com chuva. As estações ficam a 17 a 60 km do ponto Derby; nenhuma dentro da RMR urbana.

## 8. Limitações

1. **L1 (carimbo).** Início ou fim da janela se apoia só na norma de 2012 (seção 2). Nenhuma referência de 15 min com poder existe no domínio (F2-DADOS-10 §6.3). Se o carimbo fosse o início, cada hora teria 15 min de chuva do futuro (vazamento de até 15 min). O Hidro-WebService, com `Chuva_Adotada_Status`, poderia confirmar; não foi testado.
2. **L2 (desvio do critério).** C2d reprovou na leitura literal (781 contra piso de 800 mm em 39167000/2024). A decisão de prosseguir está na nota datada do critério; reversão = voltar o adaptador a `EVENT` (um commit) e a V15 fica inofensiva.
3. **L3 (lacunas).** A regra pós-lacuna é inferida do padrão dos dados, sem confirmação da ANA; ela remove também leituras legítimas depois de furos de 1 leitura.
4. O piso de 4 de 4 leituras é mais estrito que o 3 de 4 do F2-DADOS-07/10 §7 e do PRE-REGISTRO-ANALISE-2 §hora válida ("vale a desta seção"); a diferença está medida na seção 7. Se o pré-registro exigir 3 de 4, `min_completeness` da chuva precisa de `0,75` (decisão do autor, não alterada aqui).
5. Verificação do relógio e da norma vale para o serviço legado; a chave `ANA_SOAP` também recebe o coletor ao vivo (outra origem, intocada).

## 9. Como reproduzir

1. Banco: `docker compose -p projeto-mapi-api -f <worktree>/docker-compose.yml --env-file /home/lucas/Documents/Projetos/MAPI/.env.revisao up -d --build --no-deps postgres mapi-api`.
2. `POST /api/admin/ingestion/runs` com `{"source":"ANA_SOAP","from":...,"to":...,"mode":"REVALIDATE","kind":"REPROCESS"}` por ano, aguardando sair de `RUNNING` (docs/INGESTAO.md).
3. Testes do critério e tabelas: consultas sobre `observation_current` (`source = 'ANA_SOAP'`, `variable = 'precipitation'`, `resolution = 'PT15M'`) e `observation_hourly_v`, como descrito nas seções 3, 4 e 7.
