# F2-DADOS-11 — Critério de verificação da janela da chuva da ANA (fixado antes de qualquer cálculo)

- Data: 2026-10-11 · Branch `feat/ana-chuva-horaria` (a partir de `integracao/fase-2`, 2e2e5bf).
- Este arquivo é commitado **antes** de qualquer estatística sobre o bruto de chuva. Depois do commit ele não é alterado; mudança vira nota datada ao fim.
- Pergunta: o que cada `Chuva` do serviço legado da ANA (`ServiceANA.asmx/DadosHidrometeorologicos`, fonte `ANA_SOAP`) representa e em que instante é carimbada? Hoje ela entra com resolução `EVENT` e qualificador `WINDOW_UNVERIFIED` (`SensorEnvelopeAdapter.extract`), então não entra em `observation_hourly_v` (resolução fora de PT5M/PT10M/PT15M/PT1H).

## 1. Hipóteses

| id | semântica da leitura carimbada em `t` |
|---|---|
| H1 | incremento (mm) da janela de 15 min que **termina** em `t`, ou seja `(t−15 min, t]` |
| H2 | incremento da janela de 15 min que **começa** em `t`, ou seja `(t, t+15 min]` |
| H3 | acumulado corrente (desde a leitura anterior de um zerador fixo: hora ou dia) |
| H4 | total da última hora, repetido a cada 15 min (janela deslizante de 1 h) |

## 2. Evidência documental (colhida antes do bruto; WebFetch/WebSearch em 2026-10-11)

1. Página da operação, `https://telemetriaws1.ana.gov.br/ServiceANA.asmx?op=DadosHidrometeorologicos`: descreve só "Listar os dados Hidrometeorológicos por estação", com parâmetros `codEstacao`, `dataInicio`, `dataFim`. **Não define `Chuva`, `DataHora`, unidade nem janela.** O serviço legado não tem semântica documentada por si.
2. Manual do Hidro-WebService (`https://www.gov.br/ana/pt-br/assuntos/monitoramento-e-eventos-criticos/monitoramento-hidrologico/orientacoes-manuais/manuais/manual-hidrowebservice_publica.pdf`, anexo I): `"Chuva_Adotada": "0.00", [Precipitação (mm)]` e `"Data_Hora_Medicao": "2024-01-01 23:00:00.0", [DataHora da medição/coleta do dado]`; exemplo com leituras às 23:00 e 23:15. Diz unidade (mm) e grade de 15 min; **não diz se é incremento nem se o carimbo é início ou fim**.
3. Norma da ANA para quem envia dado telemétrico, "Procedimentos para envio dos dados hidrológicos em tempo real das estações telemétricas", versão março/2012, seção 4.2 (`https://arquivos.ana.gov.br/infohidrologicas/cadastro/ProcedimentosParaEnvioDosDadosHidrologicosEmTempoRealDasEstacoesTelemetricas_RevisaoMar12.pdf`, sha256 `39ff1c96…67dd2`; o mesmo texto está nas "Orientações para envio…"):
   - item (a): coleta em "1 hora, 30 minutos ou quarto de hora […] tendo como referência a hora cheia (Exemplo: […] para quartos de hora 1:00, 1:15, 1:30, 1:45, 2:00)"; fora disso exige justificativa à ANA;
   - item (b): "Os dados de chuva deverão ser acumulados no intervalo de transmissão. Por exemplo, para uma coleta com intervalo horário a chuva registrada às 14:00h deverá corresponder ao valor total da precipitação ocorrida entre as 13:00h e 14:00h";
   - seção 4.1(e): "Chuva acumulada no período de coleta na referida data/hora em milímetros com 01 casa decimal"; 4.1(d): data/hora em "hora de Brasília sem correção do horário de verão" (coerente com o UTC−3 verificado em F2-DADOS-10).
   - Leitura: a norma dá H1 (incremento do intervalo de transmissão, carimbo no **fim**). É norma para o operador, de 2012, e não uma descrição do endpoint legado. Por isso o bruto precisa corroborar, e a conclusão fica **condicional** a isto (limitação a declarar no relatório).

## 3. Testes sobre o bruto (`observation`, `ANA_SOAP`, `precipitation`, após o REVALIDATE do F2-DADOS-10, estações 39084000, 39167000, 39187800, 39370100)

Calculados por estação, com o rótulo já em UTC (UTC−3 aplicado) e não sobre o texto de origem. Nada de rótulo de alagamento nem de escore de avaliação entra.

- **C1 (grade).** Entre as leituras da estação: ≥ 99 % têm instante em múltiplo de 15 min com segundos zero; entre intervalos consecutivos de ≤ 1 h, ≥ 90 % são exatamente 15 min. Conta de leituras fora da grade é reportada (regra de borda, seção 5).
- **C2a (não é acumulador, H3).** Entre pares consecutivos de leituras com 15 min de distância e ambas > 0, a fração em que a segunda é menor que a primeira é ≥ 25 % (um acumulador com zerador só diminui no zerador; incremento oscila).
- **C2b (zerador).** Entre as transições "> 0 para 0", o quarto de hora do dia com mais transições concentra < 20 % delas (um zerador fixo concentraria quase todas num horário).
- **C2c (não é hora deslizante, H4).** Fração de horas UTC com as 4 leituras idênticas e > 0 é < 2 % das horas com alguma chuva.
- **C2d (ordem de grandeza).** Por estação-ano com ≥ 70 % das leituras de 15 min possíveis no ano civil (ou ≥ 70 % do período entre a primeira e a última leitura do ano), a soma das leituras como incrementos fica entre 800 e 4 000 mm/ano e nenhuma leitura excede 100 mm (catálogo: máximo 300 mm; ADR 0006 `variable_catalog`). A faixa é conservadora e `[sem fonte]` como as do catálogo.
- **C3 (carimbo início ou fim, H1 contra H2).** Sem referência de 15 min com poder no domínio (F2-DADOS-10 §6.3 já descartou Cemaden por falta de chuva coincidente; a reanálise Open-Meteo é horária com viés de ~1 h e não separa 15 min). Portanto **C3 não é testável pelo bruto** e H1 contra H2 se apoia **só na norma** (seção 2, item 3). Consequência já fixada: aceito H1 apenas se nada no bruto o contradiz (C1 aprova: a primeira leitura de cada dia/hora cheia existe como carimbo `:00`, compatível com fim de janela que fecha na hora cheia, porém igualmente compatível com início).

## 4. Regra de decisão

- **Verificada (H1)**: norma da seção 2 item 3 encontrada (feito) **e** C1, C2a, C2b, C2c e C2d aprovam em **todas** as 4 estações. Efeito: o adaptador passa a emitir `PT15M` sem `WINDOW_UNVERIFIED` para o legado (origem `ServiceANA.DadosHidrometeorologicos`); o coletor ao vivo continua `EVENT`/`WINDOW_UNVERIFIED`.
- **Não verificada**: qualquer estação reprova qualquer teste. Não se força, não se exclui estação para passar: a chuva da ANA continua fora da série horária e o relatório registra qual teste reprovou.
- A restrição C3 é declarada como limitação em qualquer dos casos; ela não é critério de reprovação porque não há dado que a teste, mas a conclusão é rotulada "verificada por norma + consistência do bruto", não "verificada empiricamente".

## 5. Regras de agregação fixadas (valem se verificada)

- Leitura carimbada em `t` pertence à janela `(t−15 min, t]` e à hora que contém `t` na convenção `(T−1h, T]` do ADR 0006 §5; leitura em `hh:00:00` exata pertence à hora que **termina** em `hh:00` (a view já faz isso com `ts_utc − 1 µs`). Com grade de 15 min alinhada à hora cheia nenhuma janela cruza a borda.
- Leitura fora da grade de 15 min (norma 4.2(a) admite coleta defasada com justificativa): cai inteira na hora do seu carimbo (sem rateio) e conta em `n_readings`; a janela dela é desconhecida, por isso a contagem de leituras fora da grade é reportada e C1 limita a 1 %.
- Lacuna: hora com menos de `4 × 0,8` leituras (ou seja, < 4, pois `count < 3,2`) vira `NULL`, `incomplete = true`. Nunca preenche com 0. Observação: isso é mais estrito que o "≥ 3 de 4" usado em F2-DADOS-07/10 §7; a diferença é medida e reportada.
- Valor 0 é leitura válida (sem chuva), distinta de ausência.
