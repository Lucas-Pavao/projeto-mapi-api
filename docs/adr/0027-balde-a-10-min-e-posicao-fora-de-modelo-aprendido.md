# 0027 — Balde a 10 min em tempo real; posição e altitude fora de modelo aprendido

- Status: proposto (implementado em modo sombra)
- Data: 2026-11-17
- Decisores: Claude (autor), Lucas
- Tarefa: ALERTA-E1, item 1.4

## Contexto

O balde M1a (ADR 0014) roda em passo de 1 h contra 60,1 mm/h e não alaga nenhum ponto em 5 anos (`plano-fase4.json`).
A microdrenagem é projetada para 10 a 15 min (`PARAMETROS-MODELO-FISICO.md`, item 10), e o passo horário esconde o pico:
houve 26,9 mm em 10 min (161 mm/h). No v2, latitude e longitude como entrada viraram identificador do ponto; tirá-las
melhorou a PR-AUC de 0,764 para 0,792.

## Opções consideradas

### A — Manter o balde horário

Contra: não enxerga o pico de 10 min, que é o que satura a galeria.

### B — Balde M1a com passo de 10 min, mesma estrutura

A favor: mesma física, capacidade na duração de projeto. Contra: a PED entrega blocos de até 60 min, que precisam ser
espalhados.

## Decisão

Opção B, versão `balde-M1a-10min` (`projeto-mapi-ai/mapi_ai/bucket10.py`; `avaliacao/balde.py` congelado não muda).

- Passo de 10 min. Capacidade = C × i(TR, Te) × A, com i da IDF de Silva e Araújo (2013) na duração Te = 10 min, TR 5
  anos (125,4 mm/h), a mesma do nível `ALERTA` (ADR 0026). C = 0,86; h_crit = 15 cm. **Nenhum parâmetro ajustado para
  acertar frequência de alagamento.**
- Série de entrada: chuva do ponto em blocos de 10 min com a regra da série horária (ADR 0022): maior valor entre as
  estações válidas a até 5 km, reprovada no mês fora, pico isolado fora, qualificação 0002 em quarentena. Bloco longo
  é espalhado por igual no seu intervalo e marcado (`PointRain10MinComposer`). Passo sem estação = nulo.
- Sem estado escondido: a cada ciclo o balde roda de novo sobre as últimas 24 h, a partir da lacuna mais recente; exige
  6 h contínuas, senão `SEM_DADO (SERIE_INCOMPLETA)`.
- Projeção: a chuva prevista de 1 h do `rain_v1` distribuída por igual nos 6 passos (declarado), com q50 e com q80.
  `PROVAVEL` se a lâmina medida chega a 15 cm agora ou a projeção com q50 chega em até 60 min. q80 vai como faixa.
- Ponto sem terreno confiável vira `SEM_DADO`, nunca `NAO`: Jaboatão (fora do PE3D, `SEM_TERRENO`); Derby (área de
  62 m², `TERRENO_EM_REVISAO`, lista em `mapi.alerta.flood.terrain-review`).
- **Latitude, longitude e altitude absoluta não entram como entrada de modelo aprendido.** Servem para ligar estação e
  ocorrência ao ponto. O terreno do ponto entra só pelo modelo físico. Hoje: `logreg_v3` e `rain_v1` não usam nenhuma
  das três; o `xgb_v2` (carregado, não servido) usa latitude e longitude e não pode voltar sem retreino.

## Consequências

- O nível do ponto passa a reagir a pico de 10 min.
- A comparação com a frequência de alagamento da cidade mistura unidades (ponto contra cidade): é registro, não calibração.
- O balde roda na IA (`POST /v1/bucket/point10`); a reprise confere a lâmina do sistema contra o balde rodado fora.

## Condições que reabririam a decisão

- Cadastro da galeria (diâmetro, cota de fundo) para os pontos, que substitua a capacidade pela IDF.
- Decisão sobre a regra da área de contribuição (Derby).

## Emenda proposta A — regra da área de contribuição (2026-11-21)

- Status: **proposto** (não aprovado; nada mudou no alerta)
- Base: `docs/PROPOSTA-REGRA-AREA-CONTRIBUICAO.md` e `docs/terreno/area-contribuicao/area_regras.csv`

Contexto. Com o snap de 10 m, Derby e Ibura têm 62 e 208 m²; com 20 m, 14.875 e 10.880 m². A razão A(20 m)/A(5 m) é 572 no
Derby e 389 no Ibura, contra 1,0 a 1,3 nos outros três pontos. Além disso, o ponto do Derby fica a cerca de 100 m da Av. Agamenon
e o de Jaboatão numa encosta a 64,8 m, sem via a 20 m.

Proposta. (1) Conferir a posição dos pontos Derby e Jaboatão antes de qualquer regra de área. (2) Trava imediata, igual para
todos os pontos: se A(20 m)/A(5 m) > 10, o ponto fica `SEM_DADO (TERRENO_EM_REVISAO)` — hoje Derby e Ibura; o Ibura sai do
alerta. (3) Candidata a regra definitiva: exutório no pixel de maior acumulação a até 20 m do ponto, só a até 6 m do eixo de uma
via do OSM (cópia fixa com hash), com a faixa 5–20 m registrada. Nenhum valor foi escolhido olhando dia de alagamento.

Consequência se aprovada: `mapi.alerta.flood.terrain-review` passa a ser calculada pela trava (2), não escrita à mão; o
`terreno.json` da IA ganha a mesma marcação.
