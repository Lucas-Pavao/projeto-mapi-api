# Pré-registro — rain_v2 com satélite sobre o mar (onda 16, Fase 6.2)

- Data: 2026-11-28 · Status: **fixado antes de rodar**. Escrito com o download do histórico do GOES ainda em andamento e
  sem olhar nenhum número dele. O piloto de 30/04 a 02/05/2026 só conferiu a extração, sem cruzar com chuva medida.
- Tarefa: O16-F6 · ADR 0033.
- Pergunta: a chuva vista pelo satélite sobre o mar, a leste do Recife, melhora a previsão do rain_v1? Em especial: faz o
  aviso de chuva FORTE sair **antes de começar a chover no ponto**? Hoje isso nunca acontece: 0 dos 20 eventos avisados no
  teste (fonte: `projeto-mapi-ai/reports/v4/rain/RELATORIO.md`, linha 11).

## 1. Dados

- Satélite: `projeto-mapi-ai/data/satelite/rrqpe_caixas.csv.gz`, de 31/08/2021 23h a 31/10/2026 (UTC), extraído por
  `mapi_ai/satelite/rrqpe.py`. GOES-16 até 06/04/2025 e GOES-19 depois.
- Chuva medida, alvos e cortes: o dataset do rain_v1 sem mudança, com as mesmas linhas, os mesmos alvos e os mesmos cortes
  (treino até 03/07/2024 14h, validação até 31/07/2025, teste até 31/10/2026, folga de 6 h; `mapi_ai/rain/config.py`).

## 2. Entradas novas (10, iguais para os 5 pontos)

- **Quando a imagem conta:** no instante t, vale a imagem cujo arquivo foi criado até t (campo `_c` do nome do arquivo).
  Nenhuma imagem criada depois de t entra.
- Para cada caixa (C1 = 0 a 25 km, C2 = 25 a 60 km, C3 = 60 a 120 km a leste da costa):
  - `sat_Cn_frac`: fração dos pixels com mais de 1 mm/h na imagem mais recente;
  - `sat_Cn_p90`: percentil 90 da taxa na imagem mais recente;
  - `sat_Cn_frac_max60`: maior `frac` entre as imagens criadas nos últimos 60 min.
- `sat_idade_min`: idade da imagem mais recente, em minutos.
- **Ausente vira NaN, nunca zero:** sem imagem criada nos últimos 30 min, todas as entradas do satélite ficam ausentes;
  caixa com mais de 50% dos pixels sem dado fica ausente.

## 3. Modelos

Os 5 do rain_v1, retreinados do mesmo jeito (mesma semente, mesmos hiperparâmetros, mesma parada antecipada), mais:

- `gb_rede_sat` = entradas da rede + satélite;
- `gb_rede_om_sat` = entradas da rede + Open-Meteo + satélite.

## 4. Escolha, na validação, por horizonte (1, 3 e 6 h)

A regra do rain_v1: menor erro de quantil (pinball médio de 50%, 80% e 95%); fica o modelo mais simples cuja diferença para
o melhor tem IC 95% contendo zero (bootstrap pareado por bloco de dia, 1.000 reamostras).

Ordem de simplicidade: climatologia < persistência < open_meteo < gb_rede < gb_rede_om < gb_rede_sat < gb_rede_om_sat.
Os modelos com satélite são os mais complexos, porque a fonte deixou de existir em 02/11/2026 (seção 7). **Critério A:** o
satélite ajuda a prever a quantidade num horizonte se a regra escolher um modelo com satélite nesse horizonte.

## 5. Aviso de chuva FORTE (teste, lido uma vez, no fim)

Mesmo procedimento do rain_v1: cada modelo usa o limiar de probabilidade com o maior CSI na validação; evento = a chuva do
ponto em 60 min chega a 25,15 mm. Comparação principal: `gb_rede_om_sat` contra `gb_rede_om`, nos horizontes de 1, 3 e 6 h.

Relatado para os dois: eventos avisados, eventos avisados antes de começar a chover no ponto, antecedência mediana (IC por
bootstrap de evento), precisão e recall no regime "ainda não chove", falsos por evento. Proporções com IC exato de
Clopper–Pearson.

**Critério B:** o satélite ajuda o aviso se o `gb_rede_om_sat` avisar **pelo menos 3 eventos do teste antes de começar a
chover no ponto**, em algum horizonte, com falsos por evento **no máximo o dobro** dos do `gb_rede_om` no mesmo horizonte.

## 6. Explicação e conferências (não escolhem nada)

- TreeSHAP como no rain_v1 (`mapi_ai/rain/explain.py`): fração do |SHAP| médio que vem das entradas do satélite, no total e
  no regime "ainda não chove".
- GOES-16 × GOES-19: distribuição de `sat_C1_frac` por satélite nos mesmos meses do ano, e fração ausente por ano. A troca
  cai no meio da validação (07/04/2025), e o teste é todo GOES-19.

## 7. Consequências já decididas

- O RRQPE legado foi aposentado pela NOAA a partir de 02/11/2026 00h UTC (aviso da NESDIS, emitido em 01/09/2026,
  https://www.nesdis.noaa.gov/news/retirement-of-goes-r-rainfall-ratequantitative-precipitation-estimation-grrqpe,
  acesso em 2026-11-28). No bucket público, a última pasta é a das 16h UTC de 02/11/2026 (listagem de
  `noaa-goes19/ABI-L2-RRQPEF/2026/306/` em 2026-11-28). O substituto (Enterprise Rainfall Rate,
  `ABI-L2-FLDK-RRQPE-AWIPS`) sai pelo PDA da NOAA, que exige cadastro, e não está no bucket público.
- Por isso, **o rain_v2 não vai para o ar nesta onda**, passe ou não nos critérios.
- Se passar em A ou B: entra na dissertação e abre pendência (cadastro no PDA, quebra de série porque o algoritmo é outro,
  novo treino com o produto novo).
- Se não passar: o satélite sai do escopo (ADR 0033, "Condições que reabririam a decisão"). O resultado é relatado do mesmo
  jeito.

## 8. Limitações conhecidas antes de rodar

- O RRQPE usa só infravermelho: é bom para chuva convectiva e fraco para chuva rasa de nuvem quente, comum na chuva que vem
  do mar no Recife.
- O teste tem 28 eventos FORTE (fonte: relatório do rain_v1). Qualquer diferença no aviso terá IC largo.
- Treino todo com GOES-16 e teste todo com GOES-19.
