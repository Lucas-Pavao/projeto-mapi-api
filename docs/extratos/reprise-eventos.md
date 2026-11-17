# Reprise de eventos reais (Etapa 4.1)

DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC. Gerado por `projeto-mapi-ai/mapi_ai/reprise/extrato.py` a partir de
`ReprisaEventosIT` (banco de teste, IA real, relógio simulado) e `mapi_ai/reprise/check.py`. Horas em America/Recife.

Como foi feito: as leituras da PED de cada período entram no banco no formato da coleta ao vivo (bloco com início = fim)
e no instante em que a coleta as teria recebido (atraso sorteado da simulação do item 2.3, cenário estável, semente 42).
O ciclo do alerta roda a cada 3 min de relógio simulado, chamando a IA de verdade (`rain_v1` e `balde-M1a-10min`).
Depois da janela, segue até o alerta desligar ou expirar (no máximo 6 h). Eventos fixados antes (`mapi_ai/reprise/prepare.py`).

## Resultado dos quatro testes

| Evento | Ciclos (+ cauda) | Ligou no instante esperado (±1 ciclo) | Transições: SSE / banco | Terminou desligado | NENHUM ou NAO com dado faltando | Balde fora: chamadas, maior diferença |
|---|---|---|---|---|---|---|
| 2022-05-28 | 127 (+58) | sim, nos 5 pontos | 104 / 104 | sim | 0 | 555, 0.0 mm |
| forte1-20260206 | 121 (+0) | sim, nos 5 pontos | 115 / 115 | sim | 0 | 341, 0.0 mm |
| forte2-20260906 | 147 (+0) | sim, nos 5 pontos | 121 / 121 | sim | 0 | 441, 0.0 mm |
| forte3-20260423 | 180 (+0) | sim, nos 5 pontos | 144 / 144 | sim | 0 | 540, 0.0 mm |
| moderada-20260804 | 120 (+0) | sim, nos 5 pontos | 30 / 30 | sim | 0 | 360, 0.0 mm |

Falhas registradas pelo teste: nenhuma (`target/reprise/falhas.txt` vazio). Tempo do ciclo com a IA real: 2022-05-28 1.33 s, forte1-20260206 0.62 s, forte2-20260906 0.84 s, forte3-20260423 1.63 s, moderada-20260804 2.03 s por ciclo (5 pontos).

## 2022-05-28

Janela 27/05 23:00 a 28/05 05:20. Cruzamentos FORTE (25,15 mm em 60 min) no ponto: 28/05 02:00, 28/05 02:10, 28/05 02:20.

| Ponto | Ligou (sistema = simulação) | Antecedência até o 1º cruzamento | Pico da chuva em 10 min | Maior lâmina estimada | point_flood |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 28/05 00:15 | 105 min | 14.6 mm | 0.0 cm | SEM_DADO (TERRENO_EM_REVISAO) |
| AV_RECIFE_IBURA | 28/05 00:24 | 96 min | 13.2 mm | 0.0 cm | NAO |
| CIN_UFPE | 28/05 00:24 | 96 min | 20.8 mm | 0.0 cm | NAO |
| JABOATAO_CENTRO | 28/05 00:48 | 72 min | 19.8 mm | 0.0 cm | SEM_DADO (SEM_TERRENO) |
| MASCARENHAS_IMBIRIBEIRA | 28/05 00:24 | 96 min | 12.0 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |

Linha do tempo (a cada 30 min; chuva medida em 10 min, probabilidade de FORTE em 1 h, estado, lâmina):

| Hora | AGAMENON_DERBY | AV_RECIFE_IBURA | CIN_UFPE | JABOATAO_CENTRO | MASCARENHAS_IMBIRIBEIRA |
|---|---|---|---|---|---|
| 27/05 23:00 | 1.8 mm · p 0.08 · NENHUM · — cm | 0.6 mm · p 0.05 · NENHUM · 0 cm | 2.0 mm · p 0.08 · NENHUM · 0 cm | 0.4 mm · p 0.03 · NENHUM · — cm | 0.2 mm · p 0.04 · NENHUM · 0 cm |
| 27/05 23:30 | 0.4 mm · p 0.02 · NENHUM · — cm | 0.2 mm · p 0.02 · NENHUM · 0 cm | 1.0 mm · p 0.02 · NENHUM · 0 cm | 0.0 mm · p 0.01 · NENHUM · — cm | 0.0 mm · p 0.02 · NENHUM · 0 cm |
| 28/05 00:00 | 0.2 mm · p 0.02 · NENHUM · — cm | 0.4 mm · p 0.03 · NENHUM · 0 cm | 0.6 mm · p 0.01 · NENHUM · 0 cm | 0.4 mm · p 0.01 · NENHUM · — cm | 0.2 mm · p 0.02 · NENHUM · 0 cm |
| 28/05 00:30 | 3.2 mm · p 0.19 · ATENCAO · — cm | 4.5 mm · p 0.15 · ATENCAO · 0 cm | 3.4 mm · p 0.20 · ATENCAO · 0 cm | 4.3 mm · p 0.10 · NENHUM · — cm | 1.4 mm · p 0.13 · ATENCAO · 0 cm |
| 28/05 01:00 | 2.6 mm · p 0.18 · ATENCAO · — cm | 0.6 mm · p 0.09 · NENHUM · 0 cm | 3.5 mm · p 0.11 · NENHUM · 0 cm | 1.4 mm · p 0.12 · ATENCAO · — cm | 0.6 mm · p 0.11 · ATENCAO · 0 cm |
| 28/05 01:30 | 5.4 mm · p 0.19 · ATENCAO · — cm | 2.8 mm · p 0.16 · ATENCAO · 0 cm | 2.6 mm · p 0.07 · NENHUM · 0 cm | 0.8 mm · p 0.06 · NENHUM · — cm | 2.8 mm · p 0.18 · ATENCAO · 0 cm |
| 28/05 02:00 | 7.5 mm · p 0.26 · ATENCAO · — cm | 6.1 mm · p 0.30 · ATENCAO · 0 cm | 6.8 mm · p 0.37 · ATENCAO · 0 cm | 5.5 mm · p 0.26 · ATENCAO · — cm | 4.5 mm · p 0.23 · ATENCAO · 0 cm |
| 28/05 02:30 | 3.3 mm · p 0.07 · NENHUM · — cm | 4.5 mm · p 0.10 · NENHUM · 0 cm | 6.1 mm · p 0.18 · ATENCAO · 0 cm | 5.1 mm · p 0.09 · NENHUM · — cm | 4.5 mm · p 0.11 · ATENCAO · 0 cm |
| 28/05 03:00 | 1.6 mm · p 0.04 · NENHUM · — cm | 1.4 mm · p 0.04 · NENHUM · 0 cm | 2.8 mm · p 0.04 · NENHUM · 0 cm | 1.0 mm · p 0.02 · NENHUM · — cm | 0.8 mm · p 0.03 · NENHUM · 0 cm |
| 28/05 03:30 | 3.4 mm · p 0.08 · NENHUM · — cm | 5.8 mm · p 0.07 · NENHUM · 0 cm | 4.9 mm · p 0.09 · NENHUM · 0 cm | 5.6 mm · p 0.07 · NENHUM · — cm | 4.6 mm · p 0.05 · NENHUM · 0 cm |
| 28/05 04:00 | 3.7 mm · p 0.09 · NENHUM · — cm | 5.4 mm · p 0.11 · ATENCAO · 0 cm | 4.0 mm · p 0.07 · NENHUM · 0 cm | 4.0 mm · p 0.08 · NENHUM · — cm | 3.2 mm · p 0.05 · NENHUM · 0 cm |
| 28/05 04:30 | 1.6 mm · p 0.06 · NENHUM · — cm | 2.6 mm · p 0.08 · NENHUM · 0 cm | 5.5 mm · p 0.15 · ATENCAO · 0 cm | 1.8 mm · p 0.06 · NENHUM · — cm | 0.4 mm · p 0.04 · NENHUM · 0 cm |
| 28/05 05:00 | 3.3 mm · p 0.18 · ATENCAO · — cm | 2.8 mm · p 0.14 · ATENCAO · 0 cm | 3.0 mm · p 0.12 · ATENCAO · 0 cm | 2.2 mm · p 0.07 · NENHUM · — cm | 2.8 mm · p 0.08 · NENHUM · 0 cm |
| 28/05 05:30 | 5.1 mm · p 0.58 · ATENCAO · — cm | 1.0 mm · p 0.22 · ATENCAO · 0 cm | 3.2 mm · p 0.45 · ATENCAO · 0 cm | 2.2 mm · p 0.20 · ATENCAO · — cm | 1.0 mm · p 0.17 · ATENCAO · 0 cm |
| 28/05 06:00 | 5.8 mm · p 0.58 · ATENCAO · — cm | 10.0 mm · p 0.71 · ATENCAO · 0 cm | 8.0 mm · p 0.68 · ATENCAO · 0 cm | 8.3 mm · p 0.66 · ATENCAO · — cm | 10.0 mm · p 0.69 · ATENCAO · 0 cm |
| 28/05 06:30 | 5.6 mm · p 0.32 · ATENCAO · — cm | 10.0 mm · p 0.65 · ATENCAO · 0 cm | 6.5 mm · p 0.42 · ATENCAO · 0 cm | 6.0 mm · p 0.60 · ATENCAO · — cm | 8.9 mm · p 0.64 · ATENCAO · 0 cm |
| 28/05 07:00 | 9.2 mm · p 0.48 · ATENCAO · — cm | 9.8 mm · p 0.56 · ATENCAO · 0 cm | 8.8 mm · p 0.58 · ATENCAO · 0 cm | 10.6 mm · p 0.62 · ATENCAO · — cm | 3.3 mm · p 0.40 · ATENCAO · 0 cm |
| 28/05 07:30 | 14.6 mm · p 0.31 · ATENCAO · — cm | 2.8 mm · p 0.21 · ATENCAO · 0 cm | 8.6 mm · p 0.49 · ATENCAO · 0 cm | 5.5 mm · p 0.27 · ATENCAO · — cm | 7.5 mm · p 0.37 · ATENCAO · 0 cm |
| 28/05 08:00 | 0.8 mm · p 0.05 · NENHUM · — cm | 5.3 mm · p 0.24 · ATENCAO · 0 cm | 14.1 mm · p 0.39 · ATENCAO · 0 cm | 3.8 mm · p 0.12 · ATENCAO · — cm | 2.0 mm · p 0.14 · ATENCAO · 0 cm |

## forte1-20260206

Janela 05/02 21:30 a 06/02 03:30. Cruzamentos FORTE (25,15 mm em 60 min) no ponto: 06/02 00:30.

| Ponto | Ligou (sistema = simulação) | Antecedência até o 1º cruzamento | Pico da chuva em 10 min | Maior lâmina estimada | point_flood |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 05/02 23:54 | 36 min | 13.6 mm | 0.0 cm | SEM_DADO (TERRENO_EM_REVISAO) |
| AV_RECIFE_IBURA | não ligou | — | 1.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA), SEM_DADO (SEM_ESTACAO_VALIDA) |
| CIN_UFPE | não ligou | — | 0.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| JABOATAO_CENTRO | não ligou | — | 4.4 mm | 0.0 cm | SEM_DADO (SEM_TERRENO) |
| MASCARENHAS_IMBIRIBEIRA | não ligou | — | 1.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA), SEM_DADO (SEM_ESTACAO_VALIDA) |

Linha do tempo (a cada 30 min; chuva medida em 10 min, probabilidade de FORTE em 1 h, estado, lâmina):

| Hora | AGAMENON_DERBY | AV_RECIFE_IBURA | CIN_UFPE | JABOATAO_CENTRO | MASCARENHAS_IMBIRIBEIRA |
|---|---|---|---|---|---|
| 05/02 21:30 | 0.0 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 05/02 22:00 | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.1 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 05/02 22:30 | 0.0 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 05/02 23:00 | 0.1 mm · p 0.01 · SEM_DADO · — cm | 0.0 mm · p 0.05 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.01 · SEM_DADO · — cm | 0.0 mm · p 0.01 · SEM_DADO · 0 cm |
| 05/02 23:30 | 0.0 mm · p 0.02 · NENHUM · — cm | 0.8 mm · p 0.04 · NENHUM · 0 cm | 0.0 mm · p 0.02 · SEM_DADO · 0 cm | 0.0 mm · p 0.01 · SEM_DADO · — cm | 0.8 mm · p 0.01 · NENHUM · 0 cm |
| 06/02 00:00 | 12.6 mm · p 0.18 · ATENCAO · — cm | 1.2 mm · p 0.07 · NENHUM · 0 cm | 0.0 mm · p 0.02 · SEM_DADO · 0 cm | 0.0 mm · p 0.01 · SEM_DADO · — cm | 1.2 mm · p 0.05 · NENHUM · 0 cm |
| 06/02 00:30 | 4.5 mm · p 0.03 · NENHUM · — cm | 0.2 mm · p 0.03 · NENHUM · 0 cm | 0.2 mm · p 0.04 · NENHUM · 0 cm | 4.4 mm · p 0.03 · NENHUM · — cm | 0.8 mm · p 0.02 · NENHUM · 0 cm |
| 06/02 01:00 | 10.6 mm · p 0.08 · NENHUM · — cm | 0.1 mm · p 0.05 · NENHUM · 0 cm | 0.2 mm · p 0.01 · SEM_DADO · 0 cm | 1.4 mm · p 0.01 · NENHUM · — cm | 0.1 mm · p 0.04 · NENHUM · 0 cm |
| 06/02 01:30 | 7.5 mm · p 0.04 · NENHUM · — cm | 1.2 mm · p 0.06 · NENHUM · 0 cm | 0.0 mm · p 0.01 · SEM_DADO · 0 cm | 0.2 mm · p 0.00 · NENHUM · — cm | 1.2 mm · p 0.05 · NENHUM · 0 cm |
| 06/02 02:00 | 3.5 mm · p 0.00 · NENHUM · — cm | 0.4 mm · p 0.01 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.1 mm · p 0.00 · NENHUM · — cm | 0.4 mm · p 0.01 · SEM_DADO · 0 cm |
| 06/02 02:30 | 0.8 mm · p 0.00 · NENHUM · — cm | — mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/02 03:00 | 0.8 mm · p 0.00 · SEM_DADO · — cm | — mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.1 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/02 03:30 | 0.0 mm · p 0.00 · NENHUM · — cm | — mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | — mm · p 0.00 · SEM_DADO · — cm |

## forte2-20260906

Janela 06/09 00:20 a 06/09 07:40. Cruzamentos FORTE (25,15 mm em 60 min) no ponto: 06/09 03:20, 06/09 04:40.

| Ponto | Ligou (sistema = simulação) | Antecedência até o 1º cruzamento | Pico da chuva em 10 min | Maior lâmina estimada | point_flood |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 06/09 03:45 | -25 min | 11.2 mm | 0.0 cm | SEM_DADO (TERRENO_EM_REVISAO) |
| AV_RECIFE_IBURA | não ligou | — | 11.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| CIN_UFPE | não ligou | — | 11.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| JABOATAO_CENTRO | 06/09 03:57 | -37 min | 7.9 mm | 0.0 cm | SEM_DADO (SEM_TERRENO) |
| MASCARENHAS_IMBIRIBEIRA | não ligou | — | 11.0 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |

Linha do tempo (a cada 30 min; chuva medida em 10 min, probabilidade de FORTE em 1 h, estado, lâmina):

| Hora | AGAMENON_DERBY | AV_RECIFE_IBURA | CIN_UFPE | JABOATAO_CENTRO | MASCARENHAS_IMBIRIBEIRA |
|---|---|---|---|---|---|
| 06/09 00:30 | 0.8 mm · p 0.00 · NENHUM · — cm | 0.8 mm · p 0.00 · NENHUM · 0 cm | 1.0 mm · p 0.00 · NENHUM · 0 cm | 1.0 mm · p 0.01 · NENHUM · — cm | 0.4 mm · p 0.01 · NENHUM · 0 cm |
| 06/09 01:00 | 0.2 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.2 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm |
| 06/09 01:30 | 0.0 mm · p 0.00 · NENHUM · — cm | 0.1 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · NENHUM · 0 cm | 0.1 mm · p 0.00 · SEM_DADO · — cm | 0.1 mm · p 0.00 · NENHUM · 0 cm |
| 06/09 02:00 | 0.6 mm · p 0.00 · NENHUM · — cm | 0.6 mm · p 0.01 · NENHUM · 0 cm | 0.6 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.6 mm · p 0.01 · NENHUM · 0 cm |
| 06/09 02:30 | 1.2 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · SEM_DADO · 0 cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.6 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/09 03:00 | 1.4 mm · p 0.01 · NENHUM · — cm | 7.6 mm · p 0.05 · NENHUM · 0 cm | 1.4 mm · p 0.01 · NENHUM · 0 cm | 0.1 mm · p 0.00 · NENHUM · — cm | 7.6 mm · p 0.05 · NENHUM · 0 cm |
| 06/09 03:30 | 3.8 mm · p 0.03 · NENHUM · — cm | 11.0 mm · p 0.04 · NENHUM · 0 cm | 0.6 mm · p 0.01 · NENHUM · 0 cm | 4.0 mm · p 0.03 · NENHUM · — cm | 11.0 mm · p 0.05 · NENHUM · 0 cm |
| 06/09 04:00 | 1.0 mm · p 0.05 · NENHUM · — cm | 1.0 mm · p 0.01 · NENHUM · 0 cm | 6.0 mm · p 0.09 · NENHUM · 0 cm | 7.9 mm · p 0.13 · ATENCAO · — cm | 0.2 mm · p 0.02 · NENHUM · 0 cm |
| 06/09 04:30 | 0.8 mm · p 0.03 · NENHUM · — cm | 0.1 mm · p 0.01 · NENHUM · 0 cm | 1.4 mm · p 0.01 · NENHUM · 0 cm | 6.2 mm · p 0.05 · NENHUM · — cm | 0.1 mm · p 0.01 · NENHUM · 0 cm |
| 06/09 05:00 | 0.8 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.01 · SEM_DADO · 0 cm | 1.6 mm · p 0.01 · NENHUM · 0 cm | 0.0 mm · p 0.02 · SEM_DADO · — cm | 0.0 mm · p 0.01 · SEM_DADO · 0 cm |
| 06/09 05:30 | 0.6 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.6 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/09 06:00 | 0.4 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.4 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/09 06:30 | 0.2 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/09 07:00 | 0.2 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 06/09 07:30 | 0.1 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.1 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |

## forte3-20260423

Janela 23/04 15:10 a 24/04 00:10. Cruzamentos FORTE (25,15 mm em 60 min) no ponto: 23/04 18:10, 23/04 18:40, 23/04 21:10.

| Ponto | Ligou (sistema = simulação) | Antecedência até o 1º cruzamento | Pico da chuva em 10 min | Maior lâmina estimada | point_flood |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 23/04 18:45 | -35 min | 11.2 mm | 0.0 cm | SEM_DADO (TERRENO_EM_REVISAO) |
| AV_RECIFE_IBURA | 23/04 18:27 | -17 min | 16.9 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| CIN_UFPE | 23/04 20:36 | -146 min | 14.1 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| JABOATAO_CENTRO | 23/04 18:36 | -26 min | 13.4 mm | 0.0 cm | SEM_DADO (SEM_TERRENO) |
| MASCARENHAS_IMBIRIBEIRA | 23/04 17:24 | 46 min | 10.6 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |

Linha do tempo (a cada 30 min; chuva medida em 10 min, probabilidade de FORTE em 1 h, estado, lâmina):

| Hora | AGAMENON_DERBY | AV_RECIFE_IBURA | CIN_UFPE | JABOATAO_CENTRO | MASCARENHAS_IMBIRIBEIRA |
|---|---|---|---|---|---|
| 23/04 15:30 | 0.0 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm |
| 23/04 16:00 | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.4 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.8 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 23/04 16:30 | 0.0 mm · p 0.00 · NENHUM · — cm | 0.0 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · NENHUM · 0 cm | 0.2 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · SEM_DADO · 0 cm |
| 23/04 17:00 | 0.7 mm · p 0.01 · NENHUM · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm | 0.2 mm · p 0.00 · SEM_DADO · — cm | 0.0 mm · p 0.00 · SEM_DADO · 0 cm |
| 23/04 17:30 | 3.2 mm · p 0.03 · NENHUM · — cm | 3.2 mm · p 0.10 · NENHUM · 0 cm | 3.2 mm · p 0.03 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 2.6 mm · p 0.12 · ATENCAO · 0 cm |
| 23/04 18:00 | 1.0 mm · p 0.02 · SEM_DADO · — cm | 3.3 mm · p 0.03 · NENHUM · 0 cm | 2.0 mm · p 0.03 · NENHUM · 0 cm | 1.2 mm · p 0.02 · NENHUM · — cm | 2.2 mm · p 0.01 · SEM_DADO · 0 cm |
| 23/04 18:30 | 0.0 mm · p 0.06 · NENHUM · — cm | 13.4 mm · p 0.12 · ATENCAO · 0 cm | 1.0 mm · p 0.04 · NENHUM · 0 cm | 3.4 mm · p 0.05 · NENHUM · — cm | 4.6 mm · p 0.04 · NENHUM · 0 cm |
| 23/04 19:00 | 2.2 mm · p 0.07 · NENHUM · — cm | 1.6 mm · p 0.06 · NENHUM · 0 cm | 1.0 mm · p 0.05 · NENHUM · 0 cm | 4.6 mm · p 0.10 · NENHUM · — cm | 1.6 mm · p 0.27 · ATENCAO · 0 cm |
| 23/04 19:30 | 2.2 mm · p 0.06 · NENHUM · — cm | 0.2 mm · p 0.02 · NENHUM · 0 cm | 1.0 mm · p 0.03 · NENHUM · 0 cm | 1.2 mm · p 0.06 · NENHUM · — cm | 1.2 mm · p 0.04 · NENHUM · 0 cm |
| 23/04 20:00 | 4.9 mm · p 0.06 · NENHUM · — cm | 4.9 mm · p 0.08 · NENHUM · 0 cm | 4.9 mm · p 0.05 · NENHUM · 0 cm | 0.2 mm · p 0.03 · SEM_DADO · — cm | 3.8 mm · p 0.04 · NENHUM · 0 cm |
| 23/04 20:30 | 0.0 mm · p 0.02 · NENHUM · — cm | 10.6 mm · p 0.11 · ATENCAO · 0 cm | 0.2 mm · p 0.02 · NENHUM · 0 cm | 0.6 mm · p 0.02 · NENHUM · — cm | 10.6 mm · p 0.09 · NENHUM · 0 cm |
| 23/04 21:00 | 11.2 mm · p 0.28 · ATENCAO · — cm | 3.0 mm · p 0.07 · NENHUM · 0 cm | 7.2 mm · p 0.30 · ATENCAO · 0 cm | 2.0 mm · p 0.12 · ATENCAO · — cm | 1.2 mm · p 0.07 · NENHUM · 0 cm |
| 23/04 21:30 | 0.4 mm · p 0.03 · NENHUM · — cm | 4.3 mm · p 0.15 · ATENCAO · 0 cm | 0.8 mm · p 0.03 · NENHUM · 0 cm | 1.2 mm · p 0.11 · ATENCAO · — cm | 0.0 mm · p 0.07 · SEM_DADO · 0 cm |
| 23/04 22:00 | 0.6 mm · p 0.02 · NENHUM · — cm | 2.0 mm · p 0.03 · SEM_DADO · 0 cm | 0.3 mm · p 0.02 · NENHUM · 0 cm | 1.8 mm · p 0.04 · NENHUM · — cm | 2.4 mm · p 0.05 · NENHUM · 0 cm |
| 23/04 22:30 | 5.9 mm · p 0.08 · NENHUM · — cm | 4.0 mm · p 0.06 · NENHUM · 0 cm | 3.3 mm · p 0.13 · ATENCAO · 0 cm | 0.0 mm · p 0.02 · SEM_DADO · — cm | 4.0 mm · p 0.07 · NENHUM · 0 cm |
| 23/04 23:00 | 0.2 mm · p 0.04 · NENHUM · — cm | 0.1 mm · p 0.07 · NENHUM · 0 cm | 0.6 mm · p 0.04 · NENHUM · 0 cm | 0.6 mm · p 0.09 · NENHUM · — cm | 1.0 mm · p 0.06 · NENHUM · 0 cm |
| 23/04 23:30 | 0.1 mm · p 0.01 · NENHUM · — cm | 0.0 mm · p 0.01 · NENHUM · 0 cm | 0.1 mm · p 0.01 · NENHUM · 0 cm | 0.0 mm · p 0.01 · SEM_DADO · — cm | 0.4 mm · p 0.01 · SEM_DADO · 0 cm |
| 24/04 00:00 | 0.6 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.8 mm · p 0.00 · NENHUM · 0 cm | 0.0 mm · p 0.00 · SEM_DADO · — cm | 0.4 mm · p 0.00 · SEM_DADO · 0 cm |

## moderada-20260804

Janela 03/08 20:40 a 04/08 02:40. Cruzamentos FORTE (25,15 mm em 60 min) no ponto: nenhum (dia sem FORTE).

| Ponto | Ligou (sistema = simulação) | Antecedência até o 1º cruzamento | Pico da chuva em 10 min | Maior lâmina estimada | point_flood |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 03/08 23:15 | — | 8.0 mm | 0.0 cm | SEM_DADO (TERRENO_EM_REVISAO) |
| AV_RECIFE_IBURA | 03/08 21:36 | — | 3.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |
| CIN_UFPE | não ligou | — | 6.8 mm | 0.0 cm | NAO |
| JABOATAO_CENTRO | não ligou | — | 5.4 mm | 0.0 cm | SEM_DADO (SEM_TERRENO) |
| MASCARENHAS_IMBIRIBEIRA | não ligou | — | 3.2 mm | 0.0 cm | NAO, SEM_DADO (LEITURA_VELHA) |

Linha do tempo (a cada 30 min; chuva medida em 10 min, probabilidade de FORTE em 1 h, estado, lâmina):

| Hora | AGAMENON_DERBY | AV_RECIFE_IBURA | CIN_UFPE | JABOATAO_CENTRO | MASCARENHAS_IMBIRIBEIRA |
|---|---|---|---|---|---|
| 03/08 21:00 | 4.9 mm · p 0.07 · NENHUM · — cm | 0.2 mm · p 0.01 · NENHUM · 0 cm | 3.2 mm · p 0.05 · NENHUM · 0 cm | 0.2 mm · p 0.01 · NENHUM · — cm | 0.2 mm · p 0.02 · NENHUM · 0 cm |
| 03/08 21:30 | 3.7 mm · p 0.03 · NENHUM · — cm | 3.2 mm · p 0.04 · NENHUM · 0 cm | 3.0 mm · p 0.03 · NENHUM · 0 cm | 1.6 mm · p 0.02 · NENHUM · — cm | 3.2 mm · p 0.05 · NENHUM · 0 cm |
| 03/08 22:00 | 1.0 mm · p 0.01 · NENHUM · — cm | 1.0 mm · p 0.01 · NENHUM · 0 cm | 2.8 mm · p 0.01 · NENHUM · 0 cm | 5.4 mm · p 0.03 · NENHUM · — cm | 0.4 mm · p 0.01 · NENHUM · 0 cm |
| 03/08 22:30 | 0.6 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.6 mm · p 0.00 · NENHUM · 0 cm | 1.0 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · SEM_DADO · 0 cm |
| 03/08 23:00 | 4.3 mm · p 0.09 · NENHUM · — cm | 0.1 mm · p 0.01 · NENHUM · 0 cm | 2.2 mm · p 0.02 · NENHUM · 0 cm | 0.8 mm · p 0.01 · NENHUM · — cm | 0.1 mm · p 0.01 · NENHUM · 0 cm |
| 03/08 23:30 | 2.8 mm · p 0.03 · NENHUM · — cm | 0.4 mm · p 0.02 · NENHUM · 0 cm | 3.2 mm · p 0.06 · NENHUM · 0 cm | 0.1 mm · p 0.01 · NENHUM · — cm | 0.4 mm · p 0.02 · NENHUM · 0 cm |
| 04/08 00:00 | 3.9 mm · p 0.03 · NENHUM · — cm | 0.6 mm · p 0.04 · NENHUM · 0 cm | 5.0 mm · p 0.07 · NENHUM · 0 cm | 0.6 mm · p 0.04 · NENHUM · — cm | 0.6 mm · p 0.03 · NENHUM · 0 cm |
| 04/08 00:30 | 1.0 mm · p 0.01 · NENHUM · — cm | 0.2 mm · p 0.01 · NENHUM · 0 cm | 1.0 mm · p 0.01 · NENHUM · 0 cm | 1.0 mm · p 0.03 · NENHUM · — cm | 0.2 mm · p 0.01 · NENHUM · 0 cm |
| 04/08 01:00 | 1.4 mm · p 0.01 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 1.0 mm · p 0.00 · NENHUM · 0 cm | 0.4 mm · p 0.01 · NENHUM · — cm | 0.2 mm · p 0.01 · NENHUM · 0 cm |
| 04/08 01:30 | 0.4 mm · p 0.00 · NENHUM · — cm | 0.8 mm · p 0.00 · NENHUM · 0 cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.4 mm · p 0.00 · NENHUM · — cm | 0.8 mm · p 0.01 · NENHUM · 0 cm |
| 04/08 02:00 | 0.4 mm · p 0.00 · NENHUM · — cm | 0.6 mm · p 0.00 · NENHUM · 0 cm | 0.4 mm · p 0.00 · NENHUM · 0 cm | 0.4 mm · p 0.00 · NENHUM · — cm | 0.6 mm · p 0.00 · NENHUM · 0 cm |
| 04/08 02:30 | 0.6 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm | 0.6 mm · p 0.00 · NENHUM · 0 cm | 0.4 mm · p 0.00 · NENHUM · — cm | 0.2 mm · p 0.00 · NENHUM · 0 cm |

## Leitura

- O instante em que o alerta liga no sistema é o mesmo da simulação em todos os pontos e eventos: o caminho
  (banco, vizinhança, âncora da previsão, regras) reproduz o que a avaliação offline mede.
- O balde a 10 min do sistema e o rodado fora dão a mesma lâmina em todas as chamadas.
- Nenhum ponto chegou a ALAGAMENTO_PROVAVEL (15 cm), nem em 28/05/2022. Coerente com o registro do item 1.4.6:
  com capacidade da IDF TR 5 em 10 min e a chuva de bloco espalhada, a lâmina raramente passa de alguns cm.
- No dia sem FORTE (140 mm no dia, sem hora acima de 25,15 mm), o alerta ligou em 2 pontos: alarme falso esperado.
