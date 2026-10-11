# Resultados da avaliação F1 (etapa 2 do pré-registro)

- Tarefa: F4-IA-15 · Data: 2026-10-11 · Agente 3 (nível Equilibrado)
- Protocolo: `docs/PRE-REGISTRO-AVALIACAO.md` (commit `77e41de`, emendas até `5195987`). Só H1 e H2 são confirmatórias (emenda de 2026-10-11 retirou H3, H4, M2 e o emulador; a rede de Petri está fora do escopo).
- Código: `projeto-mapi-ai`, branch `feat/avaliacao-f1`, commit `5f60b63` (sem push), `avaliacao/avaliar_h1_h2.py` e `avaliacao/tests/test_avaliar.py` (16 testes passam, incluindo os valores de Clopper–Pearson do Apêndice A do pré-registro).
- Saída bruta: `MAPI/avaliacao-f1/resultado_etapa2.json` (SHA-256 `dcf020cb57a3d1cce5e4a7c0efc36db0fc47d4841759dad5fb58be3e6c9fac14`).
- O script rodou **uma vez** sobre os rótulos reais (antes, uma vez com rótulos sintéticos sorteados, semente 7, só para ver se executava de ponta a ponta). Nenhum parâmetro, escore ou regra mudou depois de ver o resultado.

## 1. Verificação dos escores

`sha256sum avaliacao-f1/escores_cegos.csv` = `4615d416e788729a7e5d5b34b1e4df8abff84cfc7e84a79860f14ed2e826b1a5`, igual ao congelado no commit `f060a25` e no pré-registro. 1 371 dias, 49 357 linhas (49 356 mais o cabeçalho), conforme registrado.

## 2. Rótulos

Banco local (`mapi-db`, Postgres 16, imagem do `docker-compose` do worktree de integração), consulta do Apêndice B **sem alteração** (arquivo `avaliacao/dados/rotulos_apendice_b.sql`):

```sql
WITH canon AS (
  SELECT o.* FROM flood_occurrence o
  WHERE NOT EXISTS (SELECT 1 FROM flood_occurrence d
                    WHERE d.ckan_record_id = o.ckan_record_id
                      AND d.occurred_from = o.occurred_from AND d.id < o.id))
SELECT (occurred_from AT TIME ZONE 'America/Recife')::date AS dia, count(*) AS ocorrencias
FROM canon
WHERE (occurred_from AT TIME ZONE 'America/Recife')::date BETWEEN DATE '2021-01-01' AND DATE '2024-12-31'
GROUP BY 1 ORDER BY 1;
```

- Saída: 18 linhas, **idêntica** à tabela da §2.1 do pré-registro (mesmos dias e mesmas contagens de ocorrências). Não houve divergência a registrar na §8.
- O item em aberto do Apêndice B ("conferir se o filtro de tipo já está aplicado") foi conferido: `flood_occurrence` tem 619 linhas, todas com `temporal_precision = DAY`, e a consulta devolve exatamente a lista esperada; o `label_filter` cadastrado é `cobrade-alagamento` (só "alagamento"). Não foi preciso junção adicional.
- Lista de rótulos (`dia,ocorrencias`, uma linha por dia): `avaliacao/dados/rotulos_apendice_b.csv`, SHA-256 **`3753ada8218f77503f403f216d1d73038c63bcab1400379a993e6890872b25d3`**.
- O banco foi parado ao final (`docker compose ... stop`).

| Conjunto | Dias | Episódios (≤ 3 dias) | Observação |
|---|---|---|---|
| P_principal (2023–2024) | 14 | 13 | 6 em 2023, 8 em 2024; 2023-06-27 e 06-30 formam um episódio |
| P_extra (2021–2022) | 4 | 4 | 2021-05-13; 2022-05-28, 07-08, 07-12 |
| 18 positivos | 18 | 17 | |
| N (negativos) | **689** | blocos de semana ISO | 731 − 14 − 28 (véspera e dia seguinte), conta idêntica à do pré-registro |
| Dias excluídos por forçante incompleta | 0 | | todos os dias de 2023–2024 têm 24 h de chuva |

Aquecimento: os 30 primeiros dias (2021-04) não entram em métrica; 2021-05-13 está fora do aquecimento e tem forçante completa (backfill da F4-IA-13), então entrou.

## 3. Método aplicado (como no pré-registro)

- AUC de Mann–Whitney, empates = 0,5, 14 × 689. Escore de M1: ordenação lexicográfica (`S`, depois `R`); B2 e B3: o escore contínuo (normal mensal; chuva horária máxima do dia).
- IC: *bootstrap* percentil de 95%, 10 000 réplicas, semente `20261011`; positivos reamostrados por episódio (13), negativos em blocos de semana ISO; as **mesmas réplicas** para os dois escores de cada ΔAUC (pareado).
- Regra de decisão (§7): categoria pelo IC de ΔAUC; "positivo robusto" só se o IC for > 0 também em todas as variantes com parâmetro sem fonte (`f`, curva, snap).
- Regra de parada (§1): H2 só é testada se H1 for positiva.

## 4. Resultado de H1 (M1a × B2, climatologia sazonal)

| Escore | AUC (14 × 689) | IC 95% |
|---|---|---|
| M1a (central, lexicográfico `S`, `R`) | 0,891 | [0,747; 0,970] |
| B2 (normal mensal INMET 82900) | 0,777 | [0,641; 0,890] |
| **ΔAUC = M1a − B2** | **+0,113** | **[−0,010; +0,252]** |

O IC de ΔAUC **contém 0** (o limite inferior fica 0,010 abaixo). **H1: inconclusiva** (critério: "Inconclusivo, IC contém 0"). Diz-se só: "não distinguível de B2 com 14 positivos". Não se afirma equivalência nem superioridade.

**Condição de degeneração (§7):** vale. M1a alerta em 0 de 689 dias de N e `S = 0` em todos os 14 positivos (`S` máximo em N = 0). Portanto o escore de M1a é, na prática, só o desempate `R = máx. chuva horária / (f · i_T)`, uma transformação crescente da chuva horária máxima do dia. O 0,891 é o AUC de "ordenar os dias pela chuva horária máxima da reanálise", **não** de um balde que transbordou. Mesmo que o IC tivesse excluído 0, o que se poderia afirmar é que a chuva horária máxima ordena melhor que a sazonalidade, não que o balde físico agrega algo.

## 5. Resultado de H2 (M1a × B3, regra de chuva de IDF)

**Regra de parada: H1 não foi positiva, logo H2 não é testada.** Classificação: **H2 não testada** (e, de toda forma, a degeneração do §7 a tornaria "inconclusiva por degeneração"). Os números abaixo são descritivos e **não** são um teste confirmatório:

| Escore | AUC | IC 95% |
|---|---|---|
| M1a | 0,891 | [0,747; 0,970] |
| B3 (máx. horário nos 4 pontos) | 0,891 | [0,747; 0,970] |
| ΔAUC | 0,000 (exato) | [0,000; 0,000] |

O ΔAUC é exatamente 0 por construção, não por acaso: com `S ≡ 0`, M1a ordena por `R`, e `R` é a chuva horária máxima dividida por uma constante, o mesmo ordenamento de B3. Isso é o que o pré-registro previu em §2.4 e §7 ("M1 ≈ B3"; "inconclusivo ou degenerado em H2"). Também é o que o descritivo da F4-IA-13 mostrou: a reanálise Open-Meteo tem máximo horário de 16,7 mm/h em todo o período, 33% do limiar de 50,3 mm/h, então nenhum alerta de IDF dispara.

## 6. Robustez na faixa de sensibilidade (§6.3)

As 24 variantes de M1 (central, 5 níveis de `f` incluindo 0, curva ±30%, snap 5/20 m, 8 cantos, mais `C`, `i_T` e `h_crit` nos extremos) dão **o mesmo AUC (0,891) e o mesmo ΔAUC de H1 (+0,113; IC [−0,010; +0,252]) e de H2 (0; [0; 0])**. Todas caem em "inconclusivo" em H1. Nenhuma variante é escolhida como melhor.

Por que não variam: só `f = 0,2` (e os cantos com `f = 0,2`) e `f = 0` produzem `S > 0`. Em `f = 0,2` os dias com `S > 0` em 2023–2024 são 2023-02-06, 2023-02-22, 2024-03-09 e 2024-06-15; eles já são os quatro primeiros pela razão `R`, então a ordenação por `S` antes de `R` não os move em relação aos demais, e o AUC não muda. Em `f = 0`, `S` é constante e a ordenação volta a `R`.

Consequência pelo critério do §7: H1 já é inconclusiva na versão principal, então a pergunta "positivo robusto?" não se coloca. **A categoria "inconclusivo" se mantém em toda a faixa.** Não houve variante que levasse a "positivo" nem a "negativo".

## 7. Classificação final

| Hipótese | Classificação segundo o protocolo | Números |
|---|---|---|
| H1 (M1a > B2) | **Inconclusiva** (também marcada como degenerada: M1a nunca transborda) | ΔAUC +0,113 [−0,010; +0,252] |
| H2 (M1a > B3) | **Não testada** (regra de parada); descritivamente ΔAUC = 0 por construção, "inconclusiva por degeneração" | ΔAUC 0,000 [0,000; 0,000] |

Caso degenerado do protocolo: escores constantes dariam AUC = 0,5 por empate (todos os pares empatam). O caso atual é um pouco diferente e mais fraco do que "constante": o escore principal `S` é constante (0), mas o desempate lexicográfico `R` não é, então M1a **não** tem AUC 0,5; ele reproduz o AUC da chuva máxima horária (0,891). O pré-registro prevê exatamente esse desvio para `R`. B0 (nunca alaga) e as variantes com escore totalmente constante dariam 0,5. A leitura do protocolo é que as métricas binárias não informam (alertas = 0) e o resultado cai em "degenerado/inconclusivo", lido à luz da escala da forçante (§4.3).

## 8. Análises secundárias pré-registradas (descritivas, sem correção para multiplicidade)

**Binárias nos limiares congelados** (IC exato de Clopper–Pearson):

| Regra | Recall 14 | Recall 18 | Alerta em N (689) |
|---|---|---|---|
| M1a central | 0/14 [0; 0,232] | 0/18 [0; 0,185] | 0/689 [0; 0,0053] |
| B3 (50,3), B3-47,4, B3-55, B3-50, B3-70 | 0/14 | 0/18 | 0/689 |
| B2 (sem limiar; alerta 0) | 0/14 | 0/18 | 0/689 |
| B3-24h (30 mm em 24 h) | 4/14 [0,084; 0,581] | 5/18 [0,097; 0,535] | 4/689 [0,0016; 0,0148] |

- **McNemar exato M1a × B3:** nos positivos, discordantes 0 e 0, p = 1,0; em N, 0 e 0, p = 1,0. Nenhuma informação.
- **P_extra:** nenhum dos 4 dias de 2021–2022 é alertado por M1a nem por B3 (0/4). Posto percentil do escore (fração de N abaixo, empates 0,5), idêntico para M1a e B3: 2021-05-13 = 0,96; 2022-05-28 = 1,00; 2022-07-08 = 0,87; 2022-07-12 = 0,07. B2: 0,89 / 0,89 / 0,81 / 0,81.
- **AUC contra 0,5, todos os escores (14 × 689):** M1a, B3 (e variantes de limiar, que compartilham o escore) 0,891 [0,747; 0,970]; B3-24h 0,891 [0,735; 0,976]; B2 0,777 [0,641; 0,890]; as 24 variantes de M1 0,891. Todos os IC excluem 0,5. Atenção: B3 e M1 têm o mesmo escore, então não são 30 evidências independentes.
- **Análise com 18 positivos (14 + 4 de 2021–2022, 17 episódios; secundária):** M1a, B3 e as variantes (exceto `f = 0`) 0,854 [0,704; 0,962]; `f = 0` 0,800 [0,625; 0,946]; B3-24h 0,872 [0,730; 0,974]; B2 0,795 [0,680; 0,889]. O dia 2022-07-12 (posto 0,07) puxa o AUC para baixo: chuva horária máxima baixa na reanálise.
- Não executadas porque o pré-registro as condiciona a insumos que não existem: M1-ef/B3-ef, M1-pluv, forçante alternativa, Q3 por bairro (sem polígonos oficiais), estudo de caso por ponto (não é métrica) e a verificação de consistência com Fonseca Neto et al. (B3-50 não alerta em nenhum dia, ao contrário do que a literatura relata para pluviômetro, porque a forçante é de reanálise).

## 9. Seção exploratória (rotulada: NÃO confirmatória)

Itens a seguir não estão no pré-registro como testes e não são evidência de hipótese.

1. **Variante `f = 0,2` com limiar de alerta:** gera alertas em 2 dias de 2023–2024 (2024-06-15, que é positivo, e 2023-02-22, que é "sem registro"), isto é, 1/14 e 1/689. Contagem sobre 2 dias, sem teste. Não autoriza escolher `f = 0,2` (valor sem fonte).
2. **Comparação AUC de B3 > B2:** a diferença B3 − B2 é a mesma +0,113 (ver H1), com IC que contém 0. Ordenar por chuva horária máxima da reanálise tem AUC pontual maior que a sazonalidade, sem excluir 0.
3. **Leitura do 2022-07-12 e do 2024-01-15:** a chuva da reanálise nesses dias é baixa, o que confere com o §9 do pré-registro (2024-01-15 teve 0,2 mm). Não é novo achado.

## 10. O que NÃO pode ser concluído

1. Que o balde M1 (modelo físico parametrizado por fontes externas) tem habilidade de ordenar dias de alagamento: ele **nunca transbordou**; o AUC de 0,891 vem da chuva horária máxima (`R`), que B3 também usa.
2. Que M1a seja melhor ou pior que B2 ou B3. H1 é inconclusiva (IC inclui 0), H2 não foi testada, e ΔAUC(M1a, B3) = 0 por construção.
3. Equivalência entre M1a e as baselines (sem margem com fonte nem poder).
4. Que o modelo ou a regra de IDF "funcionam" como alerta: com os limiares congelados, nenhum alerta dispara em 2023–2024 (recall 0/14, alertas em N 0/689). A reanálise (máx. 16,7 mm/h) não alcança as intensidades de projeto (≥ 50,3 mm/h). O resultado é limitado pela escala da forçante (§4.3); a comparação com pluviômetro continua impossível (0 h comuns).
5. Qualquer afirmação sobre a mecânica de maré, galeria obstruída (`f`) ou curva cota-volume: a faixa inteira deu o mesmo resultado porque o modelo nunca saiu de `S = 0` (exceto `f ≤ 0,2`), e não há cadastro de microdrenagem.
6. Alagamento "real" por ponto, antecedência, calibração de probabilidade ou generalização: a unidade é dia × Recife, o negativo é "sem registro" (não "sem alagamento"; o AUC medido é conservador se os não registrados se parecerem com os registrados, mas pode favorecer escores de chuva se o registro depender da intensidade da chuva) e há 14 positivos em 13 episódios.
7. Qualquer resultado sobre a rede de Petri, o emulador, H3 ou H4: estão fora do escopo desta avaliação por decisão do autor.
8. O IC de bootstrap com 13 episódios é aproximado; um limite inferior de −0,010 está perto de 0 e uma análise com mais positivos poderia cruzá-lo. Isso não muda a classificação escrita no protocolo, mas indica que H1 está "no limite", não "claramente nula".

## 11. Reprodutibilidade

`python -m avaliacao.avaliar_h1_h2 escores_cegos.csv rotulos_apendice_b.csv saida.json` (numpy 1.26.4, pandas 2.2.3; venv da F4-IA-13). Determinístico (semente 20261011). Consulta e lista de rótulos: `avaliacao/dados/rotulos_apendice_b.{sql,csv}`.

> **Errata (2026-10-11):** versões anteriores deste documento diziam "30 variantes" de M1. O `resultado_etapa2.json` e o `escores_cegos.csv` têm **24** (central + 23: C 0,70/0,95; i_T 50,3/68,7; h_crit 10/30 cm; f 0,0–0,8; curva ±30%; snap 5/20 m; 8 cantos). A classificação (inconclusivo em toda a faixa) não muda. Divergência apontada em F4-IA-06.
