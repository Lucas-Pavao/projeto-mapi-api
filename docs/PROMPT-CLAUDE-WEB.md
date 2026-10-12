# Prompt para o Claude (web): explicar o MAPi de ponta a ponta e gerar o HTML

> Como usar: copie **tudo** abaixo da linha `--- INÍCIO DO PROMPT ---` e cole numa conversa nova no claude.ai.
> Gerado em 2026-10-11 a partir do código e do banco reais (main dos repositórios api, ai e front). Sem senhas.
> Quando o projeto mudar, peça ao Claude Code para regenerar este arquivo antes de colar de novo.

--- INÍCIO DO PROMPT ---

## Seu papel

Você é um professor e designer de informação. Vai me ajudar a **entender e apresentar** o MAPi, um sistema que eu,
Lucas, construí no meu mestrado. Use **português do Brasil simples**, como se explicasse para alguém inteligente que
não é da área: a Defesa Civil, um colega de outra área, a banca. Quando aparecer um termo técnico, explique-o numa
frase na primeira vez.

Tudo o que você precisa saber está na **Base de fatos** abaixo. **Não invente** número, rota, tabela, fonte ou resultado
que não esteja nela. Se eu perguntar algo que a base não cobre, diga "isso não está na base de fatos" e sugira o que eu
deveria verificar no código.

## O que eu quero que você faça

1. **Ataulizar o nnosso HTML completo e autocontido** (um só `.html`, CSS e JS embutidos, sem depender de servidor;
   bibliotecas só por CDN conhecido, por exemplo Mermaid). Ele deve explicar o MAPi de ponta a ponta, com as seções:
   1. O problema e a pergunta (por que alagamento no Recife é difícil de prever).
   2. Visão geral em uma imagem (diagrama do sistema inteiro).
   3. As fontes de dados: o que cada uma mede, de onde vem, com que frequência, qual o papel e qual a prioridade.
   4. O caminho do dado: da fonte até a tela (pipeline de ingestão, banco, previsão, mapa).
   5. O banco de dados: cada tabela explicada em uma linha, agrupada por assunto.
   6. A API: **cada rota** explicada em linguagem simples (o que pede, o que devolve, quem usa).
   7. A inteligência artificial: o que roda hoje, que arquitetura clássica é cada modelo, o que entra e o que sai, o que
      é validado e o que não é.
   8. Os datasets: o dataset de exportação (colunas) e o dataset da avaliação da dissertação (números).
   9. O terreno do PE3D: o que é, como é processado, onde é usado.
   10. A avaliação científica: hipóteses, resultado, por que deu inconclusivo.
   11. O que o sistema mostra na tela (front).
   12. Limitações, pendências e próximos passos.
   13. Glossário.
2. **Criar imagens** dentro do HTML: diagramas em Mermaid (ou SVG desenhado à mão) para:
   - o sistema inteiro;
   - o fluxo de uma previsão;
   - o pipeline de ingestão (bruto → quarentena → consolidado);
   - o modelo do balde;
   - a cadeia D − E da referência vertical;
   - um mapa conceitual das fontes, com o papel de cada uma.

   Use cores com significado (risco: verde = baixo, laranja = moderado, vermelho = alto, cinza tracejado =
   indisponível) e legenda em todas as figuras.
3. **Responder às minhas perguntas** sobre qualquer item, sempre em linguagem simples e com exemplo concreto do Recife.

### Regras do HTML

- Um índice fixo no topo ou na lateral, com link para cada seção; funciona no celular (390 px) e no projetor.
- Tabelas com cabeçalho fixo e uma frase simples por linha. Nada de parágrafo enorme.
- Cada número traz a origem entre parênteses, como está na base (ex.: "18 dias em 5 anos (CKAN Recife)").
- Avisos honestos sempre visíveis: "modelo experimental, não validado"; "cotas do terreno são relativas".
- **Atualização:** sempre que eu mandar uma informação nova ou uma correção, **gere de novo o HTML inteiro, completo,
  do começo ao fim**, já com a mudança aplicada. Nunca mande só o trecho alterado, nunca escreva "o resto continua
  igual". No fim de cada versão, inclua uma seção "Histórico de versões" com data e o que mudou.
- Manter o que ja temos de relavante e adapatar ao novo formato e ataulizar o que for necessário, por exemplo as redes de petri
---

## Base de fatos (estado em 2026-10-11)

### 1. O que é o MAPi

Sistema de monitoramento e previsão de risco de alagamento para a Região Metropolitana do Recife. Acompanha **5 pontos
críticos**:

| Ponto | Observação |
|---|---|
| Av. Agamenon Magalhães (Derby) | dominado por maré |
| Av. Mascarenhas de Morais (Imbiribeira) | área plana, escoamento lento |
| Av. Recife, entrada do Ibura | — |
| CIn – UFPE | — |
| Jaboatão Centro (Rio Duas Unas) | fora da área do terreno |

O sistema faz quatro coisas:
- coleta dados ao vivo (chuva, rio, maré, clima, ondas);
- guarda o histórico com rastreabilidade;
- pede a um modelo de IA uma probabilidade de alagamento por ponto;
- mostra tudo num mapa.

É a base da dissertação de mestrado; todo resultado precisa ser auditável.

Pergunta científica: **dá para ordenar os dias de maior risco de alagamento usando só dados abertos (chuva, maré,
terreno)?**

### 2. Arquitetura (3 repositórios + banco)

| Peça | Tecnologia | Papel |
|---|---|---|
| `projeto-mapi-api` | Java 21, Spring Boot 3.4 | Orquestrador: coleta das agências, pipeline de ingestão, API REST, ciclo de previsão, exportação de datasets, segurança (JWT) |
| `projeto-mapi-ai` | Python, FastAPI, XGBoost, scikit-learn, pandas | Serviço de inferência: recebe o estado de um ponto e devolve probabilidade e classe de risco. Também tem a pasta `avaliacao/` (avaliação científica reproduzível) |
| `projeto-mapi-front` | React 19, TypeScript, Vite, MapLibre GL, Tailwind | Painel geoespacial (mapa) |
| Banco | PostgreSQL 16 + TimescaleDB 2.30 (séries temporais) + PostGIS (geografia) | Guarda tudo; o esquema muda só por migrações versionadas (Flyway, V1 a V15) |
| Observabilidade | Prometheus + Grafana (5 painéis), k6 para teste de carga | Métricas de desempenho e da fila de coleta |

Tudo sobe com Docker Compose na máquina do autor:
- front na porta 3000, API na 8080, IA na 8000;
- Grafana na 3001 e banco na 5433, essas duas só locais.

Um quarto repositório, `projeto-mapi` (MAPI Edge, coleta via MQTT), foi **descontinuado**: a própria API passou a
coletar os dados.

Diagrama sugerido:

```
Fontes (APAC, Cemaden, ANA, Open-Meteo, DHN/TabuaMare, CKAN, PE3D)
  → API (coleta + pipeline) → Banco (TimescaleDB)
  → ciclo de previsão → IA (XGBoost) → tabela de previsões
  → API REST → Front (mapa)
Banco → exportação CSV → avaliação científica
```

### 3. Fontes de dados: papel, frequência e prioridade

| Fonte | O que mede | Como chega | Frequência | Papel no MAPi | Situação |
|---|---|---|---|---|---|
| **APAC** (Agência Pernambucana de Águas e Clima) | chuva dos pluviômetros, nível de rio | coleta automática da API | a cada 5 min | chuva e rio **medidos no local**; vence o Open-Meteo na chuva atual quando há sensor a até 3 km | funcionando; só traz as últimas 24 h (sem histórico longo para baixar) |
| **Cemaden** (via APAC) | chuva (pluviômetros) | coleta automática | a cada 3 min | idem; fuso horário verificado por teste (UTC−3) | funcionando; 8 638 leituras desde 2026-10-09 |
| **ANA** (Agência Nacional de Águas) | chuva e nível de rio (telemetria) | coleta automática (HidroWebService) | a cada 15 min | rio e chuva de estações oficiais; fuso verificado (UTC−3) | funcionando ao vivo desde 2026-10-12 (235 leituras na 1ª coleta). Histórico: 1 614 541 leituras de 2021-10 a 2026-10-10 |
| **Open-Meteo** (clima atual e forecast) | temperatura, umidade, pressão, vento, radiação, chuva das últimas 24 h | API pública | de hora em hora, com cache | clima de qualquer ponto; **única fonte dos acumulados de chuva (3 a 24 h) que vão para a IA** | funcionando |
| **Open-Meteo Archive** (reanálise ERA5) | chuva e clima horários do passado | carga histórica | sob demanda | histórico de 5 anos; foi a chuva usada na avaliação da dissertação | 1 714 440 valores, de 2021-01 a 2026-09-30 |
| **ERA5-Land** (via Open-Meteo) | umidade do solo (0 a 7 cm) | carga histórica | sob demanda | indica solo encharcado | 219 120 valores |
| **Open-Meteo Marine** | altura, período e direção das ondas | API pública | sob demanda | condição do mar | funcionando |
| **DHN / TabuaMare** (Marinha) | maré prevista (tábua) do Porto do Recife | API TabuaMare (ano corrente) + PDFs oficiais da DHN (2021–2025) | 1 vez ao dia | maré, que define se a galeria consegue escoar | 44 568 horas de maré reconstruídas |
| **CKAN Recife** (dados abertos da Prefeitura) | ocorrências atendidas pela Defesa Civil | carga histórica | sob demanda | **rótulo** ("alagou ou não"), o gabarito da avaliação | 619 ocorrências; só 18 dias com alagamento em 5 anos; todas só com a data, sem hora |
| **PE3D** (Governo de PE) | terreno: cota do chão, LiDAR de 2016, 1 m | 48 arquivos baixados do portal | estático | cota da rua, declividade, área que escoa para o ponto, curva de enchimento; camada colorida no mapa | processado; datum vertical não declarado pela fonte |
| **IBGE** (BDG) e **DHN** (ficha F-41) | altitude oficial de marcos e referência da maré | documentos | estático | ligar a régua da maré à altitude do terreno | desnível medido: Δ = +0,821 m |

**Regra de prioridade ao vivo**:
- **chuva atual**: sensor (APAC, Cemaden, ANA) a até 3 km tem prioridade, usando o maior valor; sem sensor, Open-Meteo;
- **nível do rio**: só sensores;
- **temperatura, umidade, pressão e vento**: média dos sensores, quando medem; senão, Open-Meteo;
- **chuva acumulada (3, 6, 12 e 24 h) enviada à IA**: só Open-Meteo. A reanálise vale até 30/09/2026; depois, as
  últimas 24 h do forecast. A origem fica declarada no campo `rainSource`.

Hoje o pluviômetro da APAC **não** entra nos acumulados. Isso é uma decisão em aberto: o modelo atual foi treinado
com acumulados do Open-Meteo.

Licenças:
- Open-Meteo: CC BY 4.0, uso não comercial;
- CKAN: ODbL;
- DHN, PE3D e APAC: termo de uso não localizado. Por isso os dados derivados não são publicados.

### 4. Caminho do dado (pipeline de ingestão, ADR 0006)

1. **Bruto** (`raw_observation`): cada pacote recebido é guardado do jeito que chegou, com o fuso declarado da fonte e
   um hash (impressão digital).
2. **Validação**: testes fixados antes (faixa de valor, unidade, fuso, duplicata). O que reprova vai para a
   **quarentena** (`ingestion_quarantine`), com o motivo.
3. **Consolidado** (`observation`): série limpa, em UTC, com versão. Uma correção posterior da agência vira nova versão
   (`observation_revision`); nada é apagado.
4. Cada carga é uma **execução** (`ingestion_run`): fonte, intervalo, versão do código, contagens.
5. Cada valor exibido aponta para a execução e o registro bruto de origem (`provenanceId`). É isso que permite responder
   na banca "de onde veio este número".
6. Visão horária (`observation_hourly_v`): grade de 1 em 1 hora, rotulada pelo fim da hora. Hora incompleta fica vazia,
   não vira zero.

Fuso: tudo guardado em UTC e mostrado no horário do Recife.

### 5. Banco de dados: tabelas

Séries temporais (hypertables do TimescaleDB):
- `observation`
- `raw_observation`
- `sensor_data`
- `weather_data`
- `flood_predictions`
- `flood_scenario_labels`

| Grupo | Tabela | Para que serve (uma linha) |
|---|---|---|
| Pipeline novo | `raw_observation` | pacote bruto recebido de cada fonte, com hash e fuso declarado |
| | `ingestion_run` | registro de cada carga: fonte, intervalo, versão do código, quantos entraram, quantos foram rejeitados |
| | `ingestion_quarantine` | valores reprovados na validação, com o motivo |
| | `observation` | série consolidada e versionada (fonte, estação, variável, instante UTC, valor, unidade, qualidade) |
| | `observation_revision` | versões antigas de valores corrigidos (histórico, nada se apaga) |
| | `observation_current` | visão só com a versão vigente de cada valor |
| | `observation_hourly_v` | visão horária pronta (valor por hora, leituras esperadas × recebidas) |
| | `source_registry` | cada fonte com seu fuso e se ele foi verificado |
| | `variable_catalog` | cada variável com unidade, faixa válida e regra de agregação |
| | `station` | cadastro das estações (nome, coordenada, município) |
| Pontos e ligações | `flood_points` | os pontos críticos (nome, slug, coordenada, município, bacia, altitude) |
| | `point_station_link` | qual estação serve a qual ponto, com a distância e o raio usado |
| | `linkage_config` | regras de ligação (raio, método) |
| | `flood_point_pluviometers`, `flood_point_river_levels`, `flood_point_weather_stations` | ligações antigas ponto ↔ estação (legado) |
| Rótulos | `flood_occurrence` | ocorrências do CKAN (endereço, data, código Cobrade, coordenada geocodificada, precisão) |
| | `occurrence_point_match` | qual ocorrência casou com qual ponto, a que distância |
| | `label_filter` | quais tipos de ocorrência contam como alagamento (Cobrade) |
| | `flood_events` | eventos de alagamento registrados por ponto (legado) |
| | `flood_scenario_labels` | cenários rotulados à mão para treinar o XGBoost (hoje vazia) |
| Previsão | `flood_predictions` | auditoria de cada previsão: entradas, probabilidade, classe, versão do modelo, motivo de falha, variáveis imputadas (27 297 linhas) |
| Terreno | `terrain_run` | cada processamento do PE3D (hash dos arquivos, versão do GDAL, ano do voo, referência vertical) |
| | `point_terrain` | atributos por ponto: cota da rua, declividade, profundidade da depressão, área contribuinte |
| | `point_terrain_curve` | curva cota-volume (quanto de água cabe a cada 5 cm) |
| Maré | `tide_tables`, `month_data`, `day_data`, `hour_data`, `geo_locations` | tábua de maré por porto, mês, dia e hora |
| Legado de coleta | `sensor_data` | leituras dos sensores ao vivo (APAC, Cemaden, ANA) no formato antigo |
| | `weather_data` | clima atual do Open-Meteo no formato antigo |
| | `hourly_precipitation` | agregado horário de chuva por sensor |
| Usuários | `users`, `refresh_token` | login (senha guardada como hash) e sessões |
| Técnicas | `flyway_schema_history`, `spatial_ref_sys`, `geometry_columns`, `geography_columns`, `raster_columns`, `raster_overviews` | controle de migrações e catálogos do PostGIS |

### 6. A API: todas as rotas (51 caminhos)

Autenticação: login devolve um token (JWT); rotas `/api/admin/...` exigem papel ADMIN.

**Login e conta**

| Rota | O que faz |
|---|---|
| `POST /api/auth/register` | cria usuário |
| `POST /api/auth/login` | entra e recebe o token |
| `POST /api/auth/refresh` | renova o token |
| `POST /api/auth/logout` | sai |
| `GET /api/auth/me` | diz quem está logado |

**Pontos críticos e previsão (o coração do painel)**

| Rota | O que faz |
|---|---|
| `GET /api/pontos` | lista os pontos com a última previsão de cada um |
| `POST /api/pontos` | cadastra um ponto novo (a altitude vem do Open-Meteo) |
| `GET /api/pontos/{slug}` | estado atual de um ponto: clima, chuva, maré, sensores, última previsão |
| `GET /api/pontos/{slug}/terrain` | terreno do ponto (cota, declividade, área, curva de enchimento) |
| `POST /api/pontos/scenarios` | grava cenários rotulados (para treino) |
| `GET /api/environmental-data` | dado ambiental de qualquer coordenada (clique no mapa) |
| `POST /api/flood-predictions` | roda uma previsão sob demanda para uma coordenada e grava a auditoria |

**Sensores**

| Rota | O que faz |
|---|---|
| `GET /api/sensors/latest` | última leitura de todos os sensores |
| `GET /api/sensors/{sensorId}/latest` | última leitura de um sensor |
| `GET /api/sensors/{sensorId}/history` | histórico de um sensor (paginado) |
| `GET /api/sensors/ids` | lista de identificadores |
| `GET /api/sensors/inventory` | inventário com metadados |

**Clima, mar e maré**

| Rota | O que faz |
|---|---|
| `GET /api/weather` | clima atual (Open-Meteo) |
| `GET /api/marine`, `GET /api/marine/wave-height` | ondas (Open-Meteo Marine) |
| `GET /api/tide/harbors`, `/api/tide/search`, `/api/tide/state/{state}`, `/api/tide/{harbor}` | tábua de maré guardada no banco |
| `GET /api/tabua-mare/states`, `/harbors/state/{state}`, `/harbors/{ids}`, `/nearest`, `/tide/{harbor}/{month}/{days}` | consulta direta à API TabuaMare |

**Eventos de alagamento**

| Rota | O que faz |
|---|---|
| `POST /api/eventos-alagamento` | registra um alagamento real num ponto |
| `POST /api/eventos-alagamento/ingest` | ingere ocorrência bruta por coordenada |
| `GET /api/eventos-alagamento/{slug}` | histórico de alagamentos do ponto |

**Exportação de dataset (para a IA e a dissertação)**

| Rota | O que faz |
|---|---|
| `GET /api/export/ia-dataset/{slug}` | dataset unificado de um ponto (JSON) |
| `GET /api/export/ia-dataset/{slug}/csv` | o mesmo em CSV; o cabeçalho `X-Dataset-Id` é o hash do arquivo |
| `GET /api/export/ia-dataset/{slug}/manifest` | manifesto: hash, período, versões e execuções de cada fonte |
| `GET /api/export/ia-dataset/all/csv` | CSV de todos os pontos |
| `GET /api/export/ia-dataset/stream` | todos os pontos em JSON contínuo |

**Administração (só ADMIN)**

| Rota | O que faz |
|---|---|
| `POST /api/admin/ingestion/runs` | reprocessa um intervalo de uma fonte: REFETCH (busca de novo) ou REVALIDATE (revalida o bruto) |
| `GET /api/admin/ingestion/runs/{id}` | situação de uma execução |
| `POST /api/admin/ingestion/historical-weather` | carrega histórico de clima (Open-Meteo) |
| `POST /api/admin/ingestion/historical-sensors` | histórico de sensores (ANA + APAC) |
| `POST /api/admin/ingestion/historical-apac`, `/historical-apac-full` | histórico de chuva da APAC (uma estação ou o estado) |
| `POST /api/admin/ingestion/historical-civil-defense` | histórico da Defesa Civil (CKAN) |
| `POST /api/admin/ingestion/historical-full-sync` | tudo de uma vez |
| `POST /api/admin/ingestion/tide-sync` | sincroniza a tábua de maré |
| `POST /api/admin/ingestion/repair-stations` | refaz a ligação estação ↔ ponto |
| `GET /api/admin/ingestion/check-integrity` | procura lacunas no banco |
| `POST /api/admin/ingestion/ckan/revalidate` | revalida ocorrências do CKAN a partir do bruto |
| `POST /api/admin/ingestion/ckan/backfill-geocode-level` | preenche a precisão do endereço das ocorrências antigas |
| `POST /api/admin/ingestion/align-events` | **desativada** (responde 410): ajustava o horário do evento ao pico de chuva, o que tornava o rótulo circular |
| `POST /api/admin/terrain/runs` | carrega os atributos de terreno do PE3D no banco |

### 7. A inteligência artificial

#### 7.1 O que roda hoje no sistema ao vivo

**Arquitetura clássica: XGBoost** (gradient boosting de árvores de decisão).
- Muitas árvores pequenas, cada uma corrigindo o erro da anterior.
- É a referência para dado em tabela.
- Tarefa: classificação binária ("vai alagar ou não").
- Devolve uma probabilidade. A classe sai por limiares (LOW, MEDIUM, HIGH).

Entradas do modelo (28):
- **posição**: latitude e longitude;
- **chuva**: atual e acumulados de 3, 6, 12 e 24 h;
- **água**: nível da maré, nível do rio e `is_high_tide` (maré acima de um limiar);
- **ponto**: altitude (do Open-Meteo, não do PE3D), distância ao canal, distância ao ponto crítico, ao pluviômetro e ao
  sensor de rio mais próximos;
- **clima**: vento, temperatura, sensação térmica, umidade, pressão, radiação solar;
- **mar**: altura, período e direção da onda;
- **calendário**: seno e cosseno da hora e do mês, para o relógio "dar a volta".

Como funciona a previsão:
1. Ao fim de cada ciclo de coleta, a API monta essas entradas para cada ponto e chama a IA.
2. A IA devolve probabilidade, classe e versão do modelo.
3. O resultado é gravado em `flood_predictions`.
4. O mapa mostra a última previsão gravada; abrir um ponto não dispara previsão nova.

Comportamento honesto quando falta dado:
- Variável ausente vai como **imputada e declarada** (`imputedFeatures`), ou a IA recusa com erro 422.
- Nesse caso o painel mostra **"Previsão indisponível"**, sem cor de risco e sem porcentagem. Nunca mostra "0%" falso.

**Status: experimental, não validado.**
- O modelo foi treinado com cenários rotulados à mão (tabela `flood_scenario_labels`, hoje vazia).
- O arquivo do modelo não registra com que dados foi treinado; a versão aparece como `unversioned`.
- O painel mostra "Previsão experimental — modelo não validado" ao lado de toda classe.

Também existe no repositório um **LSTM** (rede neural recorrente, boa para sequências) para prever nível de rio. Ele
**não é carregado** em produção.

#### 7.2 A avaliação científica (pasta `avaliacao/`)

O plano original era comparar vários classificadores no mesmo conjunto de teste: k-NN, árvore de decisão, Random
Forest, XGBoost, regressão logística, Naive Bayes, SVM e LSTM.

O levantamento dos rótulos mostrou que isso não se sustenta:
- o CKAN tem **18 dias com alagamento em 5 anos**, todos sem hora;
- no período com chuva confiável (2023–2024) sobram **14 dias positivos contra 689 negativos**;
- com 14 positivos, a diferença entre algoritmos fica dentro do ruído, e o "vencedor" seria sorte.

Para um teste com poder seriam necessários cerca de **43 episódios independentes** com hora e local.

**Decisão F1 (aprovada pelo autor)**: o modelo principal passou a ser um **modelo físico do balde**. Ele não aprende com
os dados: os parâmetros vêm da literatura.
- A rua alaga quando a chuva por hora passa da capacidade da galeria.
- Parâmetros:
  - coeficiente de escoamento C = 0,86;
  - capacidade de projeto 60,1 mm/h (chuva de 5 anos de retorno, 60 min);
  - lâmina crítica 15 cm;
  - galeria funcionando como projetada (f = 1,0, sem fonte).

Baselines (as "réguas" de comparação):

| Id | Baseline | Papel |
|---|---|---|
| B0 | "Nunca alaga" | âncora |
| B1 | Climatologia constante | referência do Brier skill score |
| B2 | Climatologia sazonal: chuva normal de cada mês (INMET) | hipótese H1 |
| B3 | Regra de chuva: alerta se passar de 50,3 mm em 60 min (curva IDF de 2 anos) | hipótese H2 |

Hipóteses pré-registradas (fixadas antes de olhar o resultado; escores congelados por hash):
- **H1**: o balde ordena os dias melhor que a climatologia sazonal.
- **H2**: o balde é melhor que a regra de chuva.

Métrica: AUC (chance de um dia com alagamento receber nota maior que um dia sem), com intervalo de confiança.

Resultado:
- **H1 inconclusiva**: ΔAUC = +0,113, IC 95% [−0,010; +0,252]. O intervalo inclui zero.
- **H2 não testada**, pela regra de parada.
- **Por que**: a reanálise do Open-Meteo chega a no máximo **16,7 mm/h**, porque suaviza a chuva convectiva.
  Nenhuma das 131 632 horas analisadas passa da capacidade, então o balde nunca transborda.
- Uma 2ª análise com o pluviômetro da ANA **não foi testada**: o critério pré-fixado reprovou a série (781 mm/ano contra
  um mínimo de 800).

Conclusão honesta: os dados abertos não permitiram testar o modelo físico. Não se afirma que ele funciona nem que não
funciona.

**Rede de Petri estocástica** (outro modelo físico do autor): fora do escopo atual, por decisão do autor.

**Calibração, SHAP e monitoramento de deriva**: não aplicados.
- Calibrar exige muitos positivos.
- SHAP explicaria um modelo não validado, o que mostraria uma "causa" inventada.
- Monitorar deriva exige saber com que dados o modelo foi treinado.

Reprodutibilidade: `reproduzir.sh` refaz tudo com hashes conferidos:
- escores `4615d416…`;
- resultado `dcf020cb…`;
- entradas de chuva `ef6772b7…`.

### 8. Datasets

**Exportação** (`GET /api/export/ia-dataset/{slug}/csv`): uma linha por hora por ponto, de 2021 até hoje.
- Colunas:
  - `floodPointSlug`, `timestamp`, `timestampUtc`;
  - `sensorPrecipitation`, `sensorWaterLevel`, `sensorSoilHumidity`;
  - `weatherPrecipitation`, `weatherTemperature`, `weatherPressure`, `weatherCode`;
  - `tideHeight`;
  - `accumulated3h`, `accumulated6h`, `accumulated12h`, `accumulated24h`, `accumulated48h`;
  - `isFlooded`, `severity`;
  - colunas de terreno `terrain_*`.
- Acompanha um manifesto com o hash, o período e as execuções de cada fonte.
- Valor que falta fica vazio (não vira zero), com unidade e fuso declarados.

**Dataset da avaliação F1**:
- unidade = dia × Recife, 2023–2024;
- 14 dias positivos (13 episódios) e 689 negativos (dias sem registro, excluindo ±1 dia dos positivos);
- chuva horária do Open-Meteo Archive nos 4 pontos com terreno;
- atributos de terreno do PE3D.

**Volumes no banco** (observações consolidadas, por fonte):

| Fonte | Valores | Período |
|---|---|---|
| Open-Meteo Archive | 1 714 440 | 2021-01 a 2026-09-30 |
| ANA | 1 614 541 | 2021-10 a 2026-10-10 |
| ERA5-Land | 219 120 | — |
| Maré horária | 44 568 | — |
| Tábua DHN | 6 009 | — |
| TabuaMare | 1 183 | — |
| Cemaden | 8 638 | desde 2026-10-09 |

### 9. Terreno PE3D

- **O que é**:
  - Modelo Digital de Terreno (MDT): a cota do chão nu, sem prédios nem árvores, em grade de 1 m × 1 m;
  - vem de laser aéreo (LiDAR), voo de 2016;
  - 48 arquivos (1,1 GB) cobrindo cerca de 21 × 28 km do Recife;
  - cota de −4,6 a 135,7 m.
- **Processamento** (`tools/terreno/run.sh`, num Docker com GDAL fixado):
  - os arquivos não são descompactados;
  - forma-se um mosaico virtual;
  - para cada ponto, numa janela de 2 km, calculam-se: cota da rua, declividade, profundidade da depressão, área
    contribuinte (com WhiteboxTools; 97 a 99,5% de concordância na validação) e curva cota-volume.
- **Valores**: cota da rua: Ibura 1,39 m · CIn 7,68 m · Derby 2,58 m · Imbiribeira 2,07 m. Jaboatão fica fora (a 1,4 km da
  folha mais próxima).
- **Onde é usado**:
  - no banco (`point_terrain`), no painel do ponto e no CSV de exportação;
  - na avaliação (modelo do balde);
  - numa camada colorida do mapa (hipsometria de 0 a 20+ m; tiles PMTiles de 34 MB, zoom 10 a 16, cobrindo o Recife todo).
  - O XGBoost ao vivo **não** usa o MDT.
- **Referência vertical**:
  - a maré é medida a partir do Nível de Redução da Marinha; o terreno está no datum do IBGE;
  - a ficha F-41 da DHN e o BDG do IBGE fecharam o desnível: **Δ = +0,821 m** (RN 2603F: 3,619 m acima do NR, contra
    2,7977 m de altitude);
  - falta medir o viés do MDT. Por isso só valem **cotas relativas**, e o "refluxo de maré" (maré mais alta que a rua)
    está **fora do escopo**.

### 10. O que aparece na tela (front)

- Mapa escuro com:
  - sensores, com ícone por tipo e indicação de "atualizado" ou "atrasando";
  - pontos críticos, com a cor do risco;
  - raio de 3 km.
- Barra à direita com três botões, cada um abrindo um painel: **Camadas** (ligar/desligar tipos de sensor), **Terreno**
  (liga a hipsometria, com a legenda de cotas) e **Legenda de risco** (o que significa Baixo, Moderado, Alto e Previsão
  indisponível).
- **Clique no mapa**: card "Análise de local", com temperatura, umidade, chuva, vento, pressão, radiação, nível de água,
  maré e sensores no raio.
- **Clique num ponto**: popup com probabilidade, classe, selo "experimental" e sensores. Dali abre o painel analítico
  completo.
- Funciona em celular (390 px) e em projeção. Horários mostrados no fuso do Recife.

### 11. Decisões principais (ADRs)

| ADR | Decisão |
|---|---|
| 0001 | Tudo em UTC no banco; exibição no horário do Recife |
| 0002 | Manter as camadas atuais do backend |
| 0003 | Esquema só por Flyway |
| 0004 | Testes com banco real em contêiner (Testcontainers) |
| 0005 | Ficar no PostgreSQL + TimescaleDB + PostGIS, em vez de MySQL, com planilha de critérios |
| 0006 | Pipeline bruto → quarentena → consolidado, com proveniência |
| 0007 | Contrato da previsão: risco LOW/MEDIUM/HIGH/UNKNOWN; falha nunca vira "0%"; motivo da falha declarado; selo "não validado" |
| 0008 | Atributos do terreno no banco; mapa por tiles pré-gerados |
| 0009 | Referência vertical: só cotas relativas; Δ medido em 2026-10-11; refluxo fora do escopo |

### 12. Limitações e pendências

- Modelo ao vivo experimental e não validado. A avaliação científica foi inconclusiva por falta de dado, não por erro
  de método.
- Rótulo fraco: 18 dias em 5 anos, sem hora, com resolução de bairro (distância mediana de 2 456 m até o ponto). "Dia
  sem registro" não significa "não alagou".
- A chuva usada na avaliação (reanálise) não alcança as intensidades que alagam ruas.
- Termos de uso pendentes: DHN, PE3D, APAC.
- Faltam dados da galeria (cadastro de drenagem da EMLURB) e registros com hora da Defesa Civil (pedidos via Lei de
  Acesso à Informação, ainda não enviados).
- Segurança: ambiente pensado só para uso local (segredo de login padrão no compose).

Próximos passos:
- pedir à Defesa Civil os registros com hora e endereço;
- conseguir pluviômetro com série confiável;
- obter o cadastro de drenagem;
- novo pré-registro com forçante pontual;
- depois disso, voltar à comparação de algoritmos.

### 13. Glossário

| Termo | Significado |
|---|---|
| ponto crítico | local monitorado onde costuma alagar |
| cota da rua | altura do chão da rua no terreno, em metros |
| soleira | altura a partir da qual a água entra num imóvel ou na galeria |
| lâmina | altura da água acumulada na rua |
| nível lá fora | o mais alto entre maré e rio, do lado de fora da galeria |
| preamar | maré alta |
| área contribuinte | área cuja chuva escorre para o ponto |
| galeria / microdrenagem | tubulação que leva a água da rua |
| reanálise | estimativa do tempo passado feita por modelo de computador em grade (não é medição no local) |
| pluviômetro | aparelho que mede a chuva no local |
| AUC | probabilidade de o modelo dar nota maior a um dia que alagou do que a um que não alagou (0,5 = chute) |
| IC 95% | faixa onde o valor verdadeiro provavelmente está |
| pré-registro | regras e hipóteses fixadas antes de ver o resultado, para não "ajustar" depois |
| proveniência | rastro de onde veio cada número |
| UNKNOWN / Previsão indisponível | não há previsão válida; não quer dizer risco baixo |

--- FIM DO PROMPT ---
