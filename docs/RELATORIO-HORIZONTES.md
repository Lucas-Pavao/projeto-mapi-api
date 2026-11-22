# Relatório — previsão oficial do INMET e "dia de risco" (prompt dos horizontes)

Data: 2026-11-22 · Branch `feat/onda15-horizontes` nos três repositórios · ADR 0029

## 1. O que a previsão do INMET por município entrega

`apiprevmet3.inmet.gov.br/previsao/{ibge}` responde sem chave (só com User-Agent de navegador):

- 5 dias, com turnos manhã, tarde e noite nos 2 primeiros dias;
- texto ("Poucas nuvens", "Muitas nuvens com possibilidade de chuva isolada"), ícone, temperatura, umidade e vento.

**Não traz quantidade de chuva:** nem milímetros, nem probabilidade.

**Não traz hora de emissão.** O campo `hora` acompanha a hora local da consulta. Em três consultas no dia 22/11 (13h56,
14h15 e 14h33), a previsão de amanhã mudou duas vezes, aos pedaços. A emissão fica aproximada pelo instante em que o
MAPi viu cada versão (`first_seen_at`). Detalhes: `docs/extratos/inmet-previsao-municipio.md`.

A coleta roda a cada 3 h desde 22/11. O primeiro ciclo ao vivo gravou 27 entradas (3 municípios × 9) e 3 brutos com
SHA-256.

## 2. O "dia de risco" contra a climatologia

Avaliação com critério escrito e commitado antes de baixar os dados (`projeto-mapi-ai`, `mapi_ai/dayrisk/config.py`
@ `3d9e5af`). BSS = Brier skill score contra a climatologia; IC 95% por bootstrap de dia. Teste de 01/11/2025 a
31/10/2026, olhado uma vez. Emissão às 06h:

| Modelo | H = 6 h | H = 12 h | H = 24 h |
|---|---|---|---|
| persistência | −0,007 [−0,018; 0,102] | 0,001 [−0,022; 0,119] | 0,005 [−0,040; 0,101] |
| ECMWF IFS 0,25° | 0,008 [−0,001; 0,092] | −0,019 [−0,303; 0,068] | 0,077 [−0,037; 0,184] |
| ICON Global | 0,112 [−0,011; 0,193] | 0,109 [−0,002; 0,195] | −0,006 [−0,258; 0,127] |
| best_match (o atual) | −1,419 [−13,46; 0,165] | −2,136 [−11,95; −0,234] | −0,781 [−2,017; −0,341] |
| média dos três | 0,093 [−1,289; 0,329] | −0,099 [−2,369; 0,380] | −0,169 [−0,658; 0,066] |
| máximo dos três | 0,104 [−0,204; 0,177] | −0,061 [−1,778; 0,304] | −0,353 [−1,022; −0,074] |
| logística completa | −0,004 [−0,017; 0,132] | 0,008 [−0,017; 0,154] | 0,004 [−0,049; 0,127] |

**Decisão pelo critério:** em H = 12 h, o melhor na validação foi o ICON (BSS 0,071, IC −0,081 a 0,260). O IC contém
o zero, então a regra escolhe a climatologia, e **não há o que adotar.** Fonte:
`projeto-mapi-ai/reports/day_risk/RELATORIO.md` e `evaluation.json`.

## 3. Eventos FORTE no teste, e o que isso quer dizer

- **28 eventos FORTE em 14 dias**, somando os 5 pontos (mínimo pré-registrado: 15). Às 06h com H = 12 h, só 10
  ponto-instantes positivos, em 6 dias.
- **Com tão poucos dias de evento, quase todo IC cruza o zero.** Um dia acertado a mais ou a menos muda o BSS em
  décimos. Este resultado tem o mesmo peso de um resultado positivo.
- O ICON chegou perto no teste (0,109 [−0,002; 0,195]). Isso não muda a decisão: escolher agora seria olhar o teste
  duas vezes. Além disso, o ICON Global é descontinuado pelo Open-Meteo em 30/11/2026.
- Com os avisos do INMET, não há período em comum: os avisos são arquivados desde 16/11/2026, e a série medida acaba
  em 31/10/2026.

## 4. O "dia de risco" foi para a tela?

**Não.** `GET /api/horizontes/{slug}` devolve `dayRisk.available = false`, com motivo `NAO_VALIDADO`. No front, a linha
não aparece no modo enxuto. Em Detalhes, aparece "Dia de risco: não validado", com o motivo no ⓘ. O serviço
`POST /v1/forecast/day-risk` (C5) não foi criado. O CPTEC WRF (C3) não entrou: não estava aprovado na lista do prompt.

## 5. A tela "Hoje"

Três linhas, nessa ordem e sem combinar: INMET (aviso e turno atual e próximo, "previsão oficial do INMET", "visto às
HHhMM", sem as cores do MAPi); dia de risco (só se existir); próxima hora (o alerta por ponto).

- Minha rua, com dado real do INMET (22/11, 14h36): `docs/horizontes/hoje-minha-rua-real.jpg`.
- Painel "Agora na RMR" e card do ponto, com os mocks do front (o app exige login, e a credencial não foi usada):
  `docs/horizontes/hoje-card-e-painel-mocks.jpg`.

## 6. O que ficou parado esperando decisão

1. **CIN_UFPE está sem município no cadastro.** O bloco "Hoje" desse ponto diz "município não reconhecido". Basta
   preencher "Recife" na tela Cadastro de pontos. O dado não foi mexido.
2. **Histórico do Open-Meteo no banco:** a rota `POST /api/admin/open-meteo/historico?desde=2024-01-15` está pronta, mas
   a carga não foi disparada (cerca de 45 requisições, uma por vez). A avaliação do bloco C usou os brutos versionados
   na IA.
3. **CPTEC WRF (C3):** continua fora até ser aprovado.
4. **Cadência da previsão do INMET:** fica em 3 h até a tabela mostrar quando ela muda ao longo do dia. A suspeita, por
   enquanto, é perto das 14h.
5. **Reavaliação do dia de risco:** depende de outro modelo (o ICON sai em 30/11) ou de mais uma estação chuvosa, sempre
   com critério escrito antes.

`DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC` · Previsão numérica: Open-Meteo (ECMWF/DWD/NOAA), CC BY 4.0 ·
INMET — Instituto Nacional de Meteorologia.
