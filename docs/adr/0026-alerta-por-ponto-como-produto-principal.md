# 0026 — Alerta por ponto como produto principal

- Status: proposto (implementado em modo sombra); **o nível foi substituído pelo ADR 0030** (emenda 1)
- Data: 2026-11-17
- Decisores: Claude (autor), Lucas
- Tarefa: ALERTA-E1 (prompt do alerta de chuva forte com 20 min de antecedência)

## Contexto

A tela misturava três coisas que não conversam (`docs/extratos/caminho-do-alerta-atual.md`): o risco em 6 h do `logreg_v3`
(rótulo sintético), a previsão de chuva `rain_v1` (que o `logreg_v3` não usa, `usedByModel = false`) e o balde M1a, que só
rodava no simulador. O número de laboratório do `rain_v1` é de 20 min de antecedência mediana para chuva FORTE
(IC 95%: 10 a 40 min, `projeto-mapi-ai/reports/v4/rain/RELATORIO.md`), sempre com a chuva já caindo no ponto.

## Opções consideradas

### A — Manter o risco do `logreg_v3` como destaque e acrescentar a chuva prevista

A favor: nenhuma mudança de tela. Contra: o destaque continua sendo um modelo treinado com rótulo sintético, que não usa a
previsão de chuva; nenhum número dele foi validado contra alagamento real.

### B — Alerta por ponto, sem rótulo de alagamento, com dois campos

A favor: cada nível tem critério com fonte documentada (limiar de CSI do `rain_v1`, IDF do Recife, balde físico). Contra:
o nível do ponto depende de terreno, que falta em 2 dos 5 pontos.

## Decisão

Opção B. O produto principal é um alerta por ponto com três níveis, guardado em dois campos.

| Nível | Campo | Critério | Fonte |
|---|---|---|---|
| `ATENCAO` | `rain_alert` | probabilidade de FORTE em 1 h do `rain_v1` ≥ `L_liga` | limiar de maior CSI da validação (`models/v4/rain/metadata.json`, 1 h: 0,1055); a IA devolve o valor |
| `ALERTA` | `rain_alert` | chuva medida no ponto na duração Te ≥ i(TR, Te) × Te / 60 | IDF de Silva e Araújo (2013), Te = 10 min, TR 5 anos: 20,9 mm (`PARAMETROS-MODELO-FISICO.md`) |
| `ALAGAMENTO_PROVAVEL` | `point_flood = PROVAVEL` | lâmina do balde a 10 min ≥ 15 cm agora ou, com a chuva prevista q50, em até 60 min | ADR 0027 |

- `rain_alert`: `NENHUM`, `ATENCAO`, `ALERTA`, `SEM_DADO`. `point_flood`: `NAO`, `PROVAVEL`, `SEM_DADO`.
- `SEM_DADO` sempre com motivo: `SEM_ESTACAO_VALIDA`, `LEITURA_VELHA` (mais de 15 min), `FONTE_PARADA`, `IA_INDISPONIVEL`,
  `PREVISAO_RECUSADA` (HTTP 422), `SEM_PREVISAO`, `SEM_TERRENO`, `TERRENO_EM_REVISAO`, `SERIE_INCOMPLETA`, `BALDE_INDISPONIVEL`.
  Falta de dado nunca vira `NENHUM`, `NAO` nem risco baixo. Um `ALERTA` medido sai mesmo com a IA fora.
- Os dois primeiros níveis valem igual para todos os pontos; só o terceiro diferencia o lugar.
- Ciclo próprio, a cada coleta ao vivo da APAC/Cemaden (3 min), separado do ciclo de alagamento. Relógio injetado.
- Estado (`alert_point_state`), uma linha por ponto e ciclo (`alert_point_cycle`), transições (`alert_transition`) com
  identificador de evento, V34. Rotas `GET /api/alerta/pontos`, `/transicoes`, `/latencia`; SSE `alert-state` e
  `alert-transition`.
- **Modo sombra** (`mapi.alerta.shadow`, ligado por padrão): tudo é gravado e conferido; a rota e o SSE só servem ADMIN,
  e a tela marca "Em teste". **Só o Lucas desliga**, depois de semanas de conferência.
- O risco do `logreg_v3` sai do destaque e vai para a aba "Experimental", com o aviso de rótulo sintético.

## Consequências

- Melhor: cada nível tem origem rastreável e nenhum depende do rótulo sintético.
- Pior: Jaboatão (sem PE3D) e Derby (área de 62 m², em revisão) ficam com `point_flood = SEM_DADO`.
- Medir: antecedência e falsos por evento na conferência diária (Etapa 4.2) e atraso por trecho (`/api/alerta/latencia`).

## Condições que reabririam a decisão

- Antecedência na simulação do dado como chega de 5 min ou menos (Etapa 2.3).
- Rótulo real de alagamento por ponto com precisão horária, que permita validar o nível do ponto.

## Emenda 1 (2026-11-28) — deixa de gravar nível (onda 16)

Desde a Fase 2 da onda 16 (ADR 0030, alerta único por ponto) o nível mostrado, validado e gravado vem só de
`POST /v1/flood/forecast`. O ciclo deste ADR continua rodando para medir a latência da estação ao SSE e para a conferência da
chuva forte, mas **não grava nível** (`point_flood = DESLIGADO`, V47; reversível por `mapi.alerta.ponto-balde.enabled`), o SSE
`alert-state` parou e o `useAlertaPontos` do front foi removido. O refluxo de maré e a limpeza, que só existiam aqui, passaram
para o alerta único como fatores por ponto, com a contribuição de cada um na resposta.
