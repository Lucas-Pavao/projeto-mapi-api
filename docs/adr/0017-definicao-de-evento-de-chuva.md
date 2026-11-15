# 0017 — Definição de evento de chuva para o modelo da galeria

- Status: proposto (implementado; aguarda aprovação do Lucas para congelar)
- Data: 2026-11-08
- Decisores: Claude (autor), Lucas
- Tarefa: F6-ANALISE-01

## Contexto

O modelo estocástico simplificado da galeria trata a drenagem como sistema reparável: disponível enquanto escoa a água, em falha quando não escoa (alagamento), reparada quando escoa o acumulado. As taxas do modelo (chegada de eventos, duração, intensidade, tempo em sobrecarga) saem de uma tabela de eventos de chuva por ponto crítico, em 5 anos.

A série já existe no pipeline (ADR 0006): `observation` em UTC, grade horária `observation_hourly_v` rotulada pelo fim da hora, proveniência por valor. Falta fixar o que é um "evento". Três escolhas mudam as taxas: o intervalo seco que separa dois eventos, o total mínimo para não ser ruído e a regra que diz se o evento é fraco, médio ou forte. Elas entram na dissertação e precisam ficar congeladas antes de se calcular qualquer métrica de disponibilidade.

Números de partida:

- Limiar local de 60 min, TR 2 anos: 50,3 mm/h (Silva e Araújo 2013, Eq. 16; `docs/PARAMETROS-MODELO-FISICO.md`, linha 6).
- Chuva de projeto da galeria, TR 5 anos, 60 min: 60,1 mm/h (mesmo arquivo, linha 8; é o `iT` do balde M1a, ADR 0014).
- Coeficiente de escoamento C = 0,86 (mesmo arquivo, linha 1).
- Maior intensidade horária observada em 2021–2024: 36 mm/h no pluviômetro da ANA e 16,7 mm/h na reanálise (`.claude/mapi/PROGRESSO.md`, medidas F2-DADOS-10 e F4-IA-13).

## Opções consideradas

### A — Evento por dia civil (chuva diária > X)
A favor: simples; casa com o rótulo diário do CKAN. Contra: corta eventos na meia-noite local, junta duas chuvas do mesmo dia e não dá duração nem intervalo entre eventos, que são o insumo do modelo. Descartada.

### B — Evento por intervalo seco mínimo (IETD) sobre a grade horária
A favor: definição usual em hidrologia urbana para separar eventos independentes; dá duração, intensidade e intervalo entre chegadas; funciona igual nas duas fontes. Contra: o resultado depende do intervalo escolhido (sensibilidade obrigatória).

### C — Evento por limiar de intensidade (horas acima de X mm/h)
A favor: liga direto à falha. Contra: mistura a definição do evento com a da falha; o evento deixa de existir abaixo do limiar, e a taxa de chegada passa a depender da capacidade, que é `[sem fonte]` por ponto.

## Decisão

Opção B, sobre a grade horária, com as regras abaixo. Implementada em `RainEventDetector` (lógica pura) e exposta em `GET /api/analise/eventos-chuva` e `/resumo`; detalhes em `docs/EVENTOS-DE-CHUVA.md`.

1. **Intervalo seco: 6 h.** Um evento termina na última hora molhada antes de 6 horas seguidas sem chuva. Duas chuvas separadas por 6 h são dois eventos; por 5 h, um só. Escolha metodológica `[sem fonte]` local: 6 h é valor usual de IETD em estudos de drenagem urbana e é maior que o tempo de entrada da rede (5 a 15 min, linha 10 de `PARAMETROS-MODELO-FISICO.md`), de modo que a galeria já escoou quando a chuva seguinte começa. Sensibilidade: 3 h e 12 h.
2. **Mínimo: 1,0 mm por evento.** Abaixo disso o evento é descartado como ruído (garoa ou basculamento isolado do pluviômetro); as horas dele contam no intervalo entre os vizinhos. A resolução do pluviômetro é 0,2 mm por leitura (`AnaChuvaHorariaIntegrationTest`), então 1 mm são pelo menos 5 basculamentos. Sensibilidade: 0,2 mm e 2,0 mm.
3. **Classificação pelo pico horário.** Regra padrão `IDF`: FORTE a partir de 0,5 × 50,3 = 25,15 mm/h (menor nível de sensibilidade testado por Fonseca Neto et al. 2026 contra ocorrências em Recife, [E3] em `docs/extratos/hidrologia-recife.md`); MÉDIA a partir de 0,1 × 50,3 = 5,03 mm/h; FRACA abaixo. A fronteira de 0,1 é escolha metodológica, alinhada à faixa meteorológica de 5 mm/h. Regra alternativa `METEOROLOGICA`: 5 e 25 mm/h fixos `[sem fonte normativa citada]`.
4. **Lacuna não é zero.** Hora incompleta fica vazia; na ANA a hora exige as 4 leituras de 15 min (V15). Lacuna nunca prolonga um evento; lacuna dentro dele ou nas 6 h antes ou depois marca `hasGap`. Nada é imputado.
5. **Borda.** Evento que começa antes de `from`, termina depois de `to` ou encosta na borda da série consultada é `truncated`: fica na tabela e sai do resumo.
6. **Falha candidata.** `hoursAboveCapacity` e `excessOverCapacityMm` comparam a chuva horária com `capacityMmH` (padrão 60,1 mm/h, chuva equivalente de projeto). Não mudam a definição do evento.
7. **Coluna de validação.** `ckanOccurrenceSameDay` serve só para conferir o modelo. Nenhum parâmetro desta lista (intervalo, mínimo, limites de tipo, capacidade) pode ser ajustado olhando para ela.

Todos os parâmetros entram no manifesto e no `eventId`; o `methodVersion` (`eventos-chuva-v1`) muda se qualquer regra mudar.

## Consequências

- Melhor: as taxas do modelo vêm de uma regra única, reproduzível pelo `X-Dataset-Id` (SHA-256 do CSV) e auditável até o `ingestion_run`.
- Pior: o pluviômetro da ANA mais próximo fica a quilômetros do ponto (`stationDistanceM`), e a reanálise suaviza o pico; nenhuma das duas é chuva no ponto. Com capacidade de 60,1 mm/h e picos observados de 36 mm/h (ANA) e 16,7 mm/h (reanálise), a sobrecarga por chuva sozinha deve ser rara ou nula; a falha observada terá de vir de outra parte do modelo (maré, obstrução, capacidade efetiva menor).
- Precisa mudar: nada no schema (só leitura). OpenAPI ganha 2 operações.
- Como medir: contagem de eventos por tipo e por ano nas duas fontes; sensibilidade do `countPerYear` e do `meanDryHoursBefore` a 3/6/12 h.

## Condições que reabririam a decisão

- Série de pluviômetro a menos de 2 km de algum ponto (por exemplo, CEMADEN com 5 anos), que permita resolução de 10 a 15 min no próprio evento.
- Cadastro de microdrenagem com capacidade por ponto (pedido à EMLURB/SESAN, `PARAMETROS-MODELO-FISICO.md`): a capacidade única de 60,1 mm/h deixa de valer.
- Norma municipal de IETD ou de classificação de intensidade.
- Sensibilidade mostrar que a taxa de chegada muda mais de 30% entre 3 h e 12 h `[limiar a fixar antes de rodar]`: aí o intervalo precisa de justificativa física por ponto (tempo de esvaziamento da depressão, curva cota-volume do ADR 0008).
