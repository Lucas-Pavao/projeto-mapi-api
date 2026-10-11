# F2-DADOS-10 — Critério do reteste de fuso da ANA_SOAP (fixado ANTES de calcular)

- Data: 2026-10-11 · Branch `feat/fuso-ana-reteste` (a partir de `integracao/fase-2`, 706aadf).
- Este arquivo é commitado antes de qualquer estatística do reteste. O relatório (`F2-DADOS-10.md`) cita o hash deste commit. Qualquer mudança posterior neste arquivo invalida o reteste e deve ser declarada.
- Convenção: `h` é o fuso do rótulo (`rótulo = UTC + h`; `UTC = rótulo − h`). Hipóteses plausíveis: **h = −3** (America/Recife) e **h = 0** (UTC). Os demais `h` de −6 a +6 entram como controle e não são plausíveis.

## 1. O que já se sabe (declarado para não fingir ingenuidade)

- F2-DADOS-07, teste A (Spearman contra Open-Meteo archive): argmax −4 h nas 4 estações, margem ≤ 0,009 sobre −3 h, ρ ≤ 0,327; UTC rejeitado (IC 95 % disjunto). Falhou o critério pré-fixado e **não é reinterpretado aqui**.
- F2-DADOS-07, teste C (pós-hoc, n = 7 estações): `h = −4` implicava leitura do futuro em 3 de 7; `h = −3` e `h = −2` não.
- Antes de escrever este critério, o autor deste reteste viu, sem calcular estatística: (i) no staging, as leituras de 2026-10-10 20:15 a 20:45 (rótulo) das estações 39084000, 39167000 e 39370100 coletadas às 23:53 UTC; (ii) uma sonda às 10:09 UTC de 2026-10-11 que mostrou rótulo mais recente 06:45 em 39370100 e 0 linhas em 39187800 para o dia. Isto é coerente com −3 h e é declarado como **indício prévio**; por isso o desenho abaixo exige dados **novos**, coletados depois deste commit, e não reaproveita aquelas leituras.
- O staging não tem leitura **ao vivo** da ANA: as 555 737 linhas são carga histórica (`collected_at` de 2026-10-10 23:53 a 2026-10-11 00:03, onze runs `REFETCH`), então `collected_at` não serve como relógio para elas.
- Sobreposição de chuva com o Cemaden (que está VERIFIED em UTC−3, mas só guarda ~1 dia, 2026-10-09 a 2026-10-11): nas três estações da ANA com dado em 2026-10-08 a 2026-10-10 há 5 horas com chuva em 39084000 e 0 nas demais. **O braço "início de chuva contra Cemaden" fica descartado a priori por falta de poder** (exigiria ≥ 8 inícios de chuva simultâneos; há menos de 2). Não é calculado.

## 2. Desenho: dois braços; os dois precisam aprovar

O erro do teste anterior foi pedir ao Open-Meteo (reanálise com viés de ~1 h na chuva convectiva) que separasse −3 de −4. O reteste tira essa separação da reanálise e a entrega a uma **restrição física de causalidade**: uma leitura não pode ser publicada antes de ter sido medida. Cada hora de erro no fuso desloca o instante real em 60 min, e a restrição não depende de calibração do pluviômetro.

### 2.1 Braço 1 — relógio sobre leituras ao vivo, coletadas depois deste commit

**Coleta.** Script de leitura (não grava no banco), `ServiceANA.asmx/DadosHidrometeorologicos`, que é o mesmo endpoint do adaptador (`AnaLegacyHttpClient`). Passos:

1. Descoberta única: inventário `ListaEstacoesTelemetricas` (PE, ativas); consultar cada estação para D−1..D+1 e manter as que têm leitura com rótulo dentro de 24 h do relógio (`utc_now − 24 h` até `utc_now + 6 h`, em qualquer fuso candidato). Conjunto fixado nesse passo e não alterado depois.
2. Sondagem a cada 5 min, por até 3 h (36 sondagens) ou até atingir o tamanho mínimo da amostra, o que vier depois, parando no máximo em 3 h. Instante de observação `t_obs` = relógio do cliente ao receber a resposta (conferido contra o cabeçalho HTTP `Date`; diferença > 60 s anula a coleta). Leitura = par (estação, rótulo). Aparece no conjunto de uma estação já sondada pelo menos uma vez e não estava antes = **leitura nova**; as presentes na primeira sondagem da estação são **leituras de base** (não entram no item de latência, mas entram na verificação de futuro).
3. `lag(h) = t_obs − (rótulo − h)` em minutos. Como `t_obs` é a primeira vez que a leitura foi vista, o `lag` verdadeiro é no máximo 5 min menor (resolução da sondagem); a tolerância abaixo absorve isso.

**Hipótese `h` viável** se, simultaneamente:

- (V1, causalidade) `p01` do `lag(h)` sobre **todas** as leituras (novas e de base) ≥ −10 min **e** nenhuma estação com mais de 5 % de suas leituras com `lag(h) < −10 min`;
- (V2, tempo quase real) `p05` do `lag(h)` sobre as **leituras novas** ≤ 30 min. Como V1 fixa o piso em −10 min e as hipóteses vizinhas diferem em 60 min, **no máximo um `h`** satisfaz V1 e V2 ao mesmo tempo (a janela [−10, 30] tem 40 min).

**Poder mínimo:** ≥ 30 leituras novas, de ≥ 3 estações distintas. Abaixo disso o braço é `INSUFFICIENT_DATA`.

**Resultado do braço:** `ACCEPT(h)` se existe exatamente um `h` viável e ele está em {−3, 0}; `IMPLAUSIBLE` se o único `h` viável está fora disso; `AMBIGUOUS` se nenhum ou mais de um é viável (com poder); `INSUFFICIENT_DATA` sem poder.

**Limite assumido:** o braço 1 prova o fuso **na janela observada** (outubro/2026), não 2021–2025; o braço 2 cobre isso.

### 2.2 Braço 2 — constância do fuso no histórico (2021–2026), em blocos por ano

Objetivo: mostrar que o histórico tem o mesmo relógio do ao vivo, ou seja, que nenhum ano tem deslocamento diferente (mudança de configuração do coletor, horário de verão etc.). **Não** tenta separar −3 de −4 (isso é do braço 1).

- Série da estação e referência: iguais às do F2-DADOS-07 §1.1 (soma da hora UTC rotulada pelo fim; ≥ 3 de 4 leituras; Open-Meteo archive `precipitation` horária, `timezone=GMT`, na coordenada da estação do inventário; nunca preencher lacuna). As quatro estações de `anaHistoricalStations`.
- Estatística: Spearman ρ_y(h) em cada **ano civil UTC** `y` de cada estação, para `h` ∈ −6..+6, nas horas comuns.
- **Ano-estação com poder:** ≥ 50 horas com chuva (referência ≥ 0,1 mm) em ≥ 5 dias UTC distintos. Anos sem poder são listados e não contam.
- **Ano-estação conforme** se: (a) ρ_y(−3) − ρ_y(0) ≥ 0,03; **e** (b) ρ_y(−3) ≥ max_h ρ_y(h) − 0,03.
- **Braço 2 aprovado** se há ≥ 8 anos-estação com poder, ≥ 80 % deles conformes, e **cada** estação tem ≥ 3 anos com poder e ≥ 60 % de seus anos conformes. Caso contrário `REJECTED` (se poder suficiente) ou `INSUFFICIENT_DATA`.
- Esse braço reaproveita a série do F2-DADOS-07 (mesmos dados), mas o recorte por ano e as regras (a) e (b) não foram calculados antes deste commit.

## 3. Decisão

| Braço 1 | Braço 2 | Decisão |
|---|---|---|
| `ACCEPT(−3)` | aprovado | **Aprovar −3 h**: `source_tz = 'America/Recife'`, `VERIFIED`, migração Flyway aditiva com o próximo número livre; REVALIDATE do bruto; correção cm → m do nível no parser antes da revalidação, com teste. |
| `ACCEPT(0)` | aprovado | Aprovar `UTC` pelo mesmo caminho. (Contraria F2-DADOS-07, onde UTC foi rejeitado; seria investigado antes de aplicar.) |
| qualquer outro | qualquer | **ANA continua em quarentena `TZ_UNVERIFIED`.** O relatório diz o que faltou (por exemplo: n de leituras novas, estações com transmissão, anos sem chuva). Resultado legítimo. |

Nenhuma escrita à mão em `observation` ou `raw_observation`; a saída da quarentena é só por `REVALIDATE`.

## 4. Fora do escopo e proibido

- Rótulos de alagamento e escores de avaliação: não usados.
- Os limiares acima são escolhas de método deste reteste (`[sem fonte]` na literatura); a justificativa está no texto de cada item.
