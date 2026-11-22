# Onda 16 — estado inicial (Fase 0, sem mudar nada)

Conferido em 2026-11-22, das 21h às 22h UTC, na `main` de cada repositório. Ponto de parada A.

## 1. Commits, testes e ciclo ao vivo

| Repositório | `main` | Testes hoje | O prompt dizia |
|---|---|---|---|
| projeto-mapi-api | `911d8a2` (merge do PR #70) | `mvn test`: **576**, 0 falhas | 561 |
| projeto-mapi-ai | `d1b791f` (merge do PR #14) | `pytest tests avaliacao/tests`: **130**, 0 falhas (com e sem `AVALIACAO_F1_DIR`) | 120 |
| projeto-mapi-front | `374dab0` (merge do PR #24) | `vitest`: **588**, 0 falhas; `tsc -b` limpo | 578 |

Os números do prompt são de antes da onda 15. Os de hoje somam os testes dela: INMET, Open-Meteo por modelo, horizontes e
dia de risco.

Ciclo ao vivo (stack Docker com o mesmo código da `main`; mudam só documentos), último ciclo às 21h46 UTC:

| Ponto | Alerta mostrado (`/v1/flood/forecast`, V38) | Alerta do ADR 0026 (`/v1/bucket/point10`, `alert_point_cycle`) |
|---|---|---|
| AV_RECIFE_IBURA | SEM_DADO · TERRENO_EM_REVISAO | chuva NENHUM · balde SEM_DADO (terreno em revisão) |
| CIN_UFPE | SEM_ALERTA · 0 cm | chuva NENHUM · balde NAO · 0,0 mm |
| AGAMENON_DERBY | SEM_DADO · TERRENO_EM_REVISAO | chuva **SEM_DADO · LEITURA_VELHA** · balde SEM_DADO |
| JABOATAO_CENTRO | SEM_DADO · TERRENO_EM_REVISAO | chuva **SEM_DADO · LEITURA_VELHA** · balde SEM_DADO |
| MASCARENHAS_IMBIRIBEIRA | SEM_ALERTA · 0 cm | chuva NENHUM · balde NAO · 0,0 mm |

O alerta do ADR 0026 gravou 200 linhas nas últimas 2 h (`alert_point_cycle`). Nenhuma tela as lê.

## 2. "Coisas novas" do prompt, conferidas

| # | Afirmação | Conferido | Evidência |
|---|---|---|---|
| 1 | Mascarenhas tem taxa de limpeza, mas a última limpeza é de 31/08/2023 e a chance de entupimento satura | **Confirmado, com uma ressalva.** λ = 0,03331/dia, 130 limpezas a 150 m, última em 2023-08-31; Ibura 2023-08-30 (λ 0,02337), Derby 2024-07-05 (λ 0,00981); base até 2026-06-19. P(entupida) = 1 − exp(−λ·t): Mascarenhas e Ibura dão 1,000000 tanto no fim da base (1.023 dias) quanto hoje (1.179 dias); Derby dá 0,99909 e 0,99980. **Ressalva:** no ar, hoje, a limpeza não age: a tabela `drainage_cleaning` (V40) está vazia, porque a carga inicial (`POST /api/admin/limpeza/ingestao`) nunca rodou. Sem data de limpeza, o ciclo usa a regra de sempre. A saturação aparece quando a carga rodar, e já está nos resultados da IA | `projeto-mapi-ai/data/entupimento/parametros_156.json`; `mapi_ai/entupimento.py:32-38`; `LimpezaProperties.java`; `select count(*) from drainage_cleaning` = 0 |
| 2 | CIn com bairro "AREIAS" | **Confirmado e explicado.** A regra (`scripts/central-156/entupimento_156.py:132-134`) pega o bairro mais frequente entre os chamados a ≤ 150 m. Perto do CIn há **um só** chamado (2021, "PRC JORNALISTA ANIBAL FERNANDES", que fica na Cidade Universitária), gravado na 156 como AREIAS. A 300 m: 4 AREIAS e 3 CIDADE UNIVERSITARIA. **Achado a mais:** a 156 tem coordenadas-padrão. A 482 m do CIn, a coordenada (−8,05157; −34,95338) junta 115 chamados de Beberibe, Dois Unidos e Casa Amarela, bairros distantes. As três coordenadas mais repetidas da cidade têm 2.771, 2.398 e 1.879 chamados. Isso pode contaminar o casamento por coordenada a 150 m em qualquer ponto | `fontes/emlurb-156/*.csv.gz` (contagem refeita em 22/11); `LimpezaProperties.java` também guarda AREIAS para o CIN_UFPE |
| 3 | Mascarenhas com profundidade de depressão 0,0 m | **Confirmado.** Depois de mover 1.393 m, a profundidade foi de 0,271 para 0,0 m e a área snap 10 m de 10.103 para 713 m². A curva cota-volume continua crescente (V(0,05 m) = 0,177 m³; V(2,0 m) = 1.275,8 m³), e o terreno está `OK` no alerta. Diagnóstico na Fase 3 | `docs/ALINHAMENTO-PONTOS.md` (tabela "O que mudou"); `projeto-mapi-ai/data/bucket10/terreno.json` |
| 4 | Datas de notícia do alinhamento não podem validar Ibura, Derby e Mascarenhas | **Confirmado.** 06/02/2023 posicionou Ibura; 15/05/2025, Ibura e Mascarenhas; 04/08/2026, Derby e Mascarenhas. **Achado a mais:** 2023-02-06 também é um dos 18 dias da Defesa Civil e um dia da avaliação F1 (R2) de Ibura. Hoje nenhuma validação exclui essas datas por código | `docs/ALINHAMENTO-PONTOS.md`; `data/in1165/flood_occurrences.csv.gz` (18 dias); `avaliacao/tests/congelar_regressao.py:16` |
| 5 | `icon_global` ainda no coletor | **Confirmado** | `application.yml:156-157` (`live` e `history`); defaults em `OpenMeteoModelServiceImpl.java:76-77` |

## 3. Tabela de cobertura (seção 4 do prompt)

| Origem | Item | Fase | Situação encontrada | Arquivo e evidência |
|---|---|---|---|---|
| Geral, ajuste 1 | Dois alertas; refluxo e limpeza no que não aparece | 2 | Confirmado. O ciclo chama os dois. O ADR 0026 recebe refluxo e limpeza; a tela lê só a regressão. `useAlertaPontos` existe e não é chamado em nenhum componente | `AlertaCycleServiceImpl.java:188-259` (point10 com refluxo e limpeza) e `:218` (flood forecast); `features/alerta/hooks/useAlertaPontos.ts` sem uso |
| Geral, ajuste 2 | O balde não acumula | 4 | Confirmado. Entrada `C·P·A` e saída `C·i_cap·A·dt/60` com o mesmo C e a mesma A: só acumula com P > 125,4/6 = 20,9 mm em 10 min. A chuva prevista é espalhada por igual nos passos | `mapi_ai/bucket10.py:7-8, 85-86, 130-131`; `mapi_ai/flood/forecast.py:6, 106` |
| Geral, ajuste 3 | 3 de 5 pontos sem dado | 3 | Confirmado no ciclo ao vivo (Ibura, Derby e Jaboatão `TERRENO_EM_REVISAO`) | seção 1; `data/bucket10/terreno.json` (status `EM_REVISAO`, com motivo) |
| Geral, ajuste 4 | Validação oficial desatualizada | 9 | Confirmado. `validacao.json` (20/11) tem 4 pontos com terreno; depois da trava da onda 14, a mesma rotina dá 2 (0/7 eventos FORTE; 0/18 ponto-dias) | `projeto-mapi-ai/reports/regressao/validacao.json`; nota de 22/11 em `reports/RELATORIO-REGRESSAO.md` |
| Geral, ajuste 5 | Diferenças entre treino e serviço no rain_v1 | 7.2 | Confirmado, sem correção. Estação sem leitura sai do denominador (91% contra 46%); soma móvel com arredondamento; juntas dão 1,13 mm no q50 de 6 h. 22 estações só ao vivo, que não estavam no treino | `projeto-mapi-ai/reports/RELATORIO-REGRESSAO.md:87-88`; `docs/INGESTAO.md:166` |
| Geral, ajuste 6 | Card com chuva do Open-Meteo | 7.3 | Confirmado. Chuva 3 h do card vem de `historicalAggregates`, etiquetada "estimada · Open-Meteo". Não existe rota de acumulado medido por ponto | `features/map/cards/components/PointCard.tsx:89`; `components/risk/SourceTag.tsx:16` |
| Geral, ajuste 7 | Refluxo com viés 0 e sem validação | 2, 4 | Confirmado. `viesMdtM = 0.0` (cenário central); +0,37 só como comentário de cenário; nenhuma validação do refluxo | `AlertaProperties.java:84-85` |
| Geral, ajuste 8 | Limpeza saturada | 1.4, 2 | Confirmado (item 1 acima), mas inativa no ar enquanto `drainage_cleaning` estiver vazia | `parametros_156.json`; `drainage_cleaning` = 0 linhas |
| Geral, ajuste 9 | `best_match` e ICON em 30/11 | 1.1 | Confirmado. `icon_global` no coletor; nenhuma marca de quebra de série; o rain_v1 usa `precipitation_previous_day1` do `best_match` | `application.yml:153-157`; `mapi_ai/rain/dataset.py:21-30` |
| Geral, ajuste 10 | CIn sem município e bairro errado | 1.3 | Confirmado. `flood_points.municipality` vazio para CIN_UFPE; bairro AREIAS (item 2) | `select ... from flood_points` (22/11); `LimpezaProperties.java` |
| Geral, ajuste 11 | APAC meteorologia em quarentena | 7.1 | Confirmado. `APAC_METEO24H` com `verification_state = UNVERIFIED`; 130 registros em quarentena `TZ_UNVERIFIED`, o mais novo de 22/11 | `source_registry`; `ingestion_quarantine` |
| Geral, ajuste 12 | Limite de conexões ao vivo | 7.7 | Confirmado. 3 por usuário; a 4ª aba recebe 429 | `LiveStreamServiceImpl.java:32, 62` |
| Geral, ajuste 13 | Simulador com outro balde | 8.8 | Confirmado. O Simulador chama `/v1/simulate/bucket` (balde por hora, ADR 0014), não o de 10 min do alerta | `ModelController.java`; `BucketSimulationClientImpl.java` |
| Geral, ajuste 14 | Histórico Open-Meteo por modelo e OpenAPI | 7.7 | Pendente. A carga do histórico existe (`POST /api/admin/open-meteo/historico`) e não foi disparada; o snapshot do OpenAPI mais novo é de 17/11 | `docs/openapi/openapi-2026-11-17.json`; `open_meteo_model_hour` só com `run` |
| Pesquisa 22/11 | Δ = 0,821 m; auditar 1,144; RN 2603F; pedir F-41 | 1.2 | A auditar. Arquivos com o número: `1,144` em 5, `1,106` em 9, `1,25 m` em 1, `+0,250` em 0, `0,821` em 18 (código, docs e scripts dos três repositórios). A lista com o contexto de cada uso vai no ponto B. Pedido 01 ao CHM em rascunho, **não enviado** (o README dos pedidos diz que nenhum foi enviado) | `grep` de 22/11; `docs/pedidos/README.md:3`; `docs/pedidos/01-chm-dhn-ficha-f41-recife.md` |
| Pesquisa 22/11 | Teste de fuso APAC × PED | 7.1 | Não feito | — |
| Pesquisa 22/11 | Dados Vivos da SEDEC como rótulo com hora | 7.4 | Parcial. O serviço da Defesa Civil já procura recursos SEDEC de "solicitação/chamado" no pacote de tempo real do CKAN, mas não há coleta a cada 15 min, `first_seen_at` nem armazenamento lacrado. Existe a pasta `docs/extratos/emlurb-156/lacrado` (da onda 14) | `CivilDefenseServiceImpl.java:264-266` |
| Pesquisa 22/11 | Índice caixa branca (logística e EBM) | 5 | Não existe. Depende do ADR 0031 e do aval do orientador | — |
| Pesquisa 22/11 | Pré-registro da validação de 2027 | 9 | Não existe | — |
| Pesquisa 22/11 | Telas: semáforo, "faltam X mm", leque, linha do tempo | 8 | Parcial. A IA já devolve contrafactual, SHAP local, sensibilidade e decomposição; as abas Por quê e Balde mostram parte. Não há semáforo com 3 motivos, leque nem linha do tempo única | `mapi_ai/flood/explain.py`; `features/map/cards/components/AnalysisPanel.tsx` |
| Pesquisa 22/11 | GOES-19 RRQPE e radar (ESIG) | 6 | Não feito. O radar da APAC é coletado como imagem (`APAC_RADAR`, V33, UTC verificado), sem conversão para mm/h | `source_registry` |
| Pesquisa 22/11 | pySTEPS (opcional) | 6.3 | Não feito | — |
| Pesquisa 22/11 | Capacidade por campo e calibração inversa | 4 | Parcial. Capacidade pelo ESIG calculada na onda 14, nenhum ponto completo (Ibura: só entrada; Derby: só entrada; Mascarenhas: só cano D 0,6 m, S 0,0018) | `docs/ALINHAMENTO-PONTOS.md` (linha "Capacidade pelo ESIG"); `scripts/capacidade-galeria/` |
| Pesquisa 22/11 | Maré harmônica local | 7.5 | Parcial. `TIDE_HARMONIC_F41` é um ajuste UTide sobre a tábua DHN 2021-10..2024-12, usado como reserva, cobrindo até 31/12/2027. Não usa constantes do CHM. A TabuaMare cobre até 01/01/2027 (extremos) e 08/01/2027 (horária) | `observation_current` (máx. por fonte, 22/11) |
| Pesquisa 22/11 | Conformal (CQR) | 9.3 | Não feito | — |
| Pesquisa 22/11 | Data de quebra da Mascarenhas (reservatório) | 3.4 | Não existe configuração `obras`. Nenhum documento cita o reservatório da Rua Muritiba | `grep -i muritiba` sem resultado |
| Relatório anexado | Tabela mestre só como prior | regra 2 | A tabela não está no repositório; nada dela entrou como valor | `grep -i "tabela mestre"` sem resultado |
| Relatório anexado | Fatores DAEE/CETESB com fonte | 4.1 | Sem fonte com página: item 9 "não achado online com página, `[sem fonte]`" | `docs/PARAMETROS-ENTUPIMENTO.md:20, 91` |
| Relatório anexado | Revista DAE 225 só se achar no PDF | 4.1 | Não citada em nenhum documento; PDF ainda não aberto | `grep` sem resultado |
| Relatório anexado | Limiares do Duas Unas só na tela | 7.6 | Os níveis de pré-alerta, alerta e inundação vêm do JSON da APAC e só chegam à tela (`SensorResponseDTO`); não entram em nenhum cálculo. A unidade não foi confirmada | `SensorServiceImpl.java:428-430` |
| Conferência de hoje | Mascarenhas com depressão 0,0 m | 3.1 | Confirmado (item 3) | `docs/ALINHAMENTO-PONTOS.md` |
| Conferência de hoje | Datas do alinhamento fora da validação | regra 1 | Não implementado; 2023-02-06 cruza com a Defesa Civil e a F1 (item 4) | item 4 |

## 4. O que muda nas próximas fases por causa da Fase 0

- **Fase 1.4 (limpeza):** a regra vale para quando a carga da 156 rodar. Proponho não disparar essa carga antes da emenda
  aprovada; senão, Mascarenhas e Ibura passam a ficar sempre "entupidas" no alerta do ADR 0026.
- **Fase 1.3 (CIn):** a correção do bairro precisa tratar também as coordenadas-padrão da 156. Proposta para o ponto B:
  ignorar no casamento por coordenada as coordenadas com mais de N chamados de bairros diferentes, com N escrito antes.
- **Regra 1:** 2023-02-06 sai da validação de Ibura também por ser dia de posicionamento. Como já é dia da Defesa Civil,
  só serve para calibrar.
- **Fase 7.2:** Derby e Jaboatão estão com a leitura de chuva velha no alerta do ADR 0026. Conferir a estação de cada um
  antes da paridade treino × serviço.
