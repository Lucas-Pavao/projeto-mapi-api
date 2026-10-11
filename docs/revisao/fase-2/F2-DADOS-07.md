# F2-DADOS-07 — Verificação do fuso de ANA, APAC meteorologia24h e Cemaden

- Data: 2026-10-10 · Branch: `feat/verificacao-fuso` (a partir de `integracao/fase-2`, 0e307b2)
- Decisão do autor: tirar as fontes da quarentena `TZ_UNVERIFIED` **com evidência**, por correlação cruzada com o Open-Meteo (UTC confirmado: `utc_offset_seconds = 0` conferido a cada resposta, `OpenMeteoArchiveAdapter`).
- Convenção de sinal: `h` é o fuso do rótulo da fonte (`rótulo = UTC + h`); portanto `UTC = rótulo − h`. Dado em UTC−3 tem `h = −3`.

## 1. Critério de decisão (fixado ANTES de calcular)

Este bloco foi commitado antes de qualquer correlação ser calculada sobre os dados reais (ver `git log` deste arquivo: o commit "critério pré-fixado" precede o commit dos resultados). Os limiares abaixo são escolhas de método do autor desta tarefa, `[sem fonte]` na literatura; a justificativa de cada um está na coluna "Por quê".

### 1.1 Teste A — correlação cruzada de chuva (fontes com histórico: ANA)

| Item | Regra | Por quê |
|---|---|---|
| Série da estação | leituras de 15 min (`Chuva`) somadas em hora UTC rotulada pelo **fim** `(H−1h, H]`; hora válida só com ≥ 3 de 4 leituras; nunca preencher lacuna | mesma convenção do Open-Meteo (`precipitation` = soma da hora anterior) e do ADR 0006 §5 |
| Referência | Open-Meteo archive, `precipitation` horária, `timezone=GMT`, na coordenada da própria estação (inventário ANA) | UTC confirmado; a grade mais próxima da estação, não do ponto crítico, evita somar distância |
| Hipóteses | `h` inteiro em −6..+6 (13), incluindo `h = 0` (UTC) e `h = −3` (UTC−3) | passo de 1 h; −3 e 0 são as fisicamente plausíveis |
| Estatística principal | Spearman ρ(h) nas horas comuns (robusta à calibração do pluviômetro e a caudas pesadas); Pearson r(h) reportado ao lado | chuva é assimétrica, com muitos zeros |
| Defasagem de máxima correlação cruzada | `argmax_h ρ(h)` (a correlação cruzada em passos de 1 h é exatamente ρ(h)) | |
| Incerteza | bootstrap por blocos de dias UTC, B = 1000, semente 20261010, IC 95 % percentil; os mesmos dias sorteados valem para as 13 hipóteses | dias consecutivos de chuva são correlatos; sortear horas subestimaria o IC |
| Poder mínimo | ≥ 50 horas com chuva (referência ≥ 0,1 mm) em ≥ 5 dias UTC distintos | sem chuva suficiente a correlação não discrimina fuso |

**Estação aprovada (ACCEPT)** se, simultaneamente: (a) poder mínimo atendido; (b) `argmax ρ ∈ {−3, 0}`; (c) ρ(vencedor) ≥ 0,30; (d) ρ(vencedor) − ρ(segundo colocado) ≥ 0,05; (e) em ≥ 95 % das réplicas do bootstrap ρ(vencedor) > ρ(segundo). Resultados possíveis quando falha: `INSUFFICIENT_DATA` (a), `WEAK_CORRELATION` (c), `IMPLAUSIBLE_OFFSET` (argmax fora de {−3, 0} com poder e correlação suficientes), `AMBIGUOUS` (d ou e).

**Fonte aprovada** se ≥ 2 estações forem ACCEPT, todas no mesmo `h`, e nenhuma estação tiver `IMPLAUSIBLE_OFFSET`. Caso contrário, a fonte **continua em quarentena** e o motivo é registrado.

### 1.2 Teste B — causalidade e latência contra o relógio (fontes só ao vivo: APAC meteorologia24h e Cemaden)

Os endpoints da APAC devolvem só uma janela rolante de ~24 h, sem consulta por intervalo e sem credencial (seção 2); não há meio de reunir ≥ 6 meses de chuva. Para essas fontes o teste A não tem poder e vale o teste B, que usa o instante em que o coletor ao vivo baixou a leitura (`collected_at`, relógio do servidor em UTC):

- Para cada registro de execução `LIVE`, `lag(h) = collected_at − (rótulo − h)`.
- Hipótese `h` aprovada se: **p01 do lag(h) ≥ −10 min** (nenhuma leitura vem do futuro; tolerância de 10 min para relógio e atraso de publicação) **e** **0 ≤ mediana do lag(h) ≤ 30 min** (telemetria em tempo quase real; hipóteses vizinhas diferem em 60 min, então no máximo uma satisfaz as duas).
- Poder mínimo: ≥ 200 registros de ≥ 20 estações distintas.
- Aprovada se existir exatamente um `h` aprovado e ele estiver em {−3, 0}.
- O teste A é calculado também para essas fontes, só como evidência exploratória (sem peso na decisão), contra o Open-Meteo `forecast` com `past_days` (archive tem atraso de 2 dias, `openMeteoArchiveLagDays`).

Limite assumido desde já: o teste B prova o fuso **da janela observada**, não que a APAC nunca tenha mudado de fuso. Só o bruto coletado a partir de agora sai da quarentena por ele.

### 1.3 O que acontece com o resultado

- Fonte aprovada com `h = −3` → `source_registry.source_tz = 'America/Recife'`, `verification_state = 'VERIFIED'`; com `h = 0` → `'UTC'`. Mudança de dado de configuração via migração Flyway; o bruto sai da quarentena por `REVALIDATE` (nunca por `UPDATE` em `observation`).
- Fonte não aprovada → continua `UNVERIFIED`, nota com o motivo.

### 1.4 Aditamento (posterior ao resultado do teste A da ANA; declarado como pós-hoc)

O teste A da ANA foi calculado antes deste aditamento e **não aprovou nenhuma estação** (seção 3.1). Este item foi escrito depois de ver esse resultado e **antes** de calcular o teste abaixo; por isso ele é registrado como pós-hoc e **não** altera a decisão pré-fixada da seção 1.1. Serve de evidência independente para o autor decidir.

Teste C — relógio da ANA: para cada estação telemétrica ativa de PE do inventário `ListaEstacoesTelemetricas`, baixar `DadosHidrometeorologicos` de D−1 a D+1 e tomar o rótulo mais recente; `lag(h) = instante do download (UTC) − (rótulo − h)`. Hipótese `h` viável se **≤ 2 % das estações** tiverem `lag < −10 min` (leitura do futuro) **e** a estação mais fresca tiver `lag ≤ 30 min` (a telemetria tem alguma estação em dia com transmissão recente). Poder: ≥ 50 estações com leitura. Exatamente um `h` viável, em {−3, 0}, é a conclusão do teste. Limite: estações satelitais transmitem em blocos e ficam atrasadas; por isso o limite superior usa a estação mais fresca, não a mediana.

<!-- RESULTADOS: seções 2 em diante entram no commit seguinte -->
