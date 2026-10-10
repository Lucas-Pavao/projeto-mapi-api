# Memorando de decisão: escassez de rótulo e desenho da dissertação

- Data: 2026-10-10 · Autor: Agente 3 (nível Máximo, desenho de pesquisa) · Decisão: Lucas (e orientador, onde indicado)
- Status: **para decisão**. Só documentação; nada foi alterado em código, banco ou export.
- Lê: `docs/MODELAGEM-IA.md` (A.1.4, A.2.4, A.3, "Respostas do autor"), `docs/snapshots/2026-10-10-primeira-carga.md`
  (abaixo, "snapshot"), `docs/revisao/fase-2/F2-BACK-16.md` (abaixo, "F2-BACK-16"), ADR 0009,
  `docs/TERRENO-PE3D.md` (ramo `feat/terreno-cog3857`), `docs/GLOSSARIO.md`, skill `mapi-ml-pipeline`.
- Números novos deste memorando: (i) contas binomiais (método no Apêndice A); (ii) uma leitura descritiva da chuva
  nos dias com alagamento, feita sobre os exports `AGAMENON_DERBY.csv` (SHA-256 `0c0ab1d8…`) e
  `MASCARENHAS_IMBIRIBEIRA.csv` (SHA-256 `94356c8b…`) listados no snapshot (método no Apêndice B).
  **Aviso de pré-registro:** essa leitura olhou a chuva nos dias positivos. Por isso nenhum limiar de chuva pode,
  daqui em diante, ser escolhido "olhando" esses números; o limiar do baseline e da opção E tem de vir de fonte
  externa ou do treino (seção 3).

## Resumo em cinco linhas

1. Há **15 dias com alagamento registrado na cidade em 5 anos** (14 dentro da janela do export, 2021-10 a 2026-10),
   concentrados em 2023–2024 (F2-BACK-16 §3). Nenhuma análise supervisionada consegue, com isso, demonstrar que
   um classificador vence um baseline: o intervalo de 95% de um recall de 0,5 com 8 positivos de teste vai de
   0,16 a 0,84 (Apêndice A).
2. A unidade "ponto" não existe no rótulo: a geocodificação é de bairro (77 coordenadas para 197 ocorrências;
   distância mediana ao ponto 2.456 m; F2-BACK-16, resumo item 3 e §2a). A 3 km, quatro pontos têm os mesmos
   13 dias (F2-BACK-16 §2c).
3. O rótulo parece medir pouco do fenômeno: dos 12 dias com ≥ 20 mm na grade do Open-Meteo no Derby em 2023–2024,
   só 3 têm alagamento registrado; e 2 dos 14 dias positivos têm ≤ 3,5 mm (Apêndice B).
4. A pergunta original ("ML vence o baseline físico na previsão por ponto com 3–6 h") **não é respondível** com o
   rótulo atual. A dissertação precisa mudar de pergunta, ou conseguir outra fonte de rótulo antes de treinar.
5. Recomendação: o modelo físico-estocástico vira o modelo principal, verificado nos dias reais sem ajuste aos
   rótulos; o ML entra como emulador (rótulo sintético) e como experimento diário exploratório; ampliação do rótulo
   corre em paralelo com uma data-limite que reabre o desenho supervisionado (seção 3).

---

## 1. Diagnóstico em números

### 1.1 O que existe

| Quantidade | Valor | Fonte |
|---|---|---|
| Ocorrências "alagamento" distintas no CKAN | 197 (591 linhas, 3 recursos duplicados) | F2-BACK-16, resumo e §1 |
| Precisão temporal | 197 `DAY`, 0 `HOUR` | F2-BACK-16 §1; snapshot, resumo item 3 |
| Ocorrências por ano | 2021: 1 · 2022: em quarentena (28 candidatas em 3 dias) · 2023: 75 · 2024: 121 · 2025: 0 · 2026: sem recurso | F2-BACK-16 §1 e "2022" |
| Dias com ≥ 1 ocorrência na cidade | **15** (1 em 2021, 6 em 2023, 8 em 2024) | F2-BACK-16 §3 |
| Dias positivos dentro da janela do export (2021-10-01 a 2026-10-01) | **14** (2021-05-13 fica fora) | F2-BACK-16 §3 × período do snapshot |
| Pares bairro-dia | 146 (soma da coluna "Bairros" da série diária) em 39 bairros | cálculo sobre F2-BACK-16 §3 |
| Máximo de dias por bairro | 14 (nenhum bairro passa de 21 ocorrências) | F2-BACK-16 §3 |
| Coordenadas distintas | 77 para 197 ocorrências; até 13 na mesma | F2-BACK-16 §1 |
| Casamento com os 5 pontos (eventos-dia; dias distintos) | 500 m: 3; 1 km: 6; 2 km: 34 (13 dias); 3 km: 52 (14 dias) | F2-BACK-16 §2a |
| Maré prevista no export | presente só de 2026-01 a 2026-10 (6.560 de 43.825 horas); **ausente em 2023–2024** | contagem de `tideHeight` no export (Apêndice B); snapshot ("`tideHeight` ausente em 37 272") |
| Terreno | 4 de 5 pontos; área contribuinte instável (Ibura 208 m² no Whitebox × 573.223 m² no motor próprio) | `TERRENO-PE3D.md` §3, §7 |

Duas consequências diretas, que a MODELAGEM-IA não previa:

- **A maré não está disponível no período rotulado.** O fato "há maré prevista" vale para 2026, não para 2023–2024.
  Qualquer modelo (ML ou físico) que use maré precisa de *backfill* da tábua 2021–2025 antes de existir.
- **O período útil é 2 anos, não 5.** Os 5 anos de clima horário (1.559.880 linhas em `observation`, snapshot,
  "Cargas") têm rótulo informativo só em 2023–2024. 2021-10 a 2022-12 e 2025–2026 viram negativos por ausência de
  registro, não por ausência de alagamento (snapshot, "Conta de MODELAGEM-IA A.3.7", último item).

### 1.2 Taxa de base e dependência entre positivos

- Dia × cidade em 2023–2024: 14 positivos em 731 dias = **1,9%**. Com 2021 incluído: 15/1.096 = 1,4% (cálculo
  sobre F2-BACK-16 §3).
- Os positivos se agrupam na quadra chuvosa: 4 dias entre 2023-06-14 e 2023-07-07 e 2 dias em 2024-06 (série
  diária, F2-BACK-16 §3). Dias com 3 dias de intervalo (2023-06-27 e 2023-06-30) compartilham umidade antecedente;
  juntando dias a ≤ 3 dias de distância, os 14 viram 13 episódios. O tamanho efetivo da amostra é **13 a 15**, não
  mais.
- Por ponto, a dependência é quase total: a 3 km, AGAMENON_DERBY, AV_RECIFE_IBURA, MASCARENHAS_IMBIRIBEIRA e CIN_UFPE
  têm os mesmos 13 dias (F2-BACK-16 §2c). Os 52 pares ponto-dia a 3 km carregam a informação de ~14 dias.

### 1.3 Intervalos de confiança com n pequeno

Intervalo exato de Clopper-Pearson, 95% (cálculo do Apêndice A). `k/n` = acertos sobre positivos (recall) ou
acertos sobre alertas (precisão).

| `k/n` | Estimativa | IC 95% | Largura | Leitura |
|---|---|---|---|---|
| 1/3 | 0,33 | [0,01; 0,91] | 0,90 | 500 m, todos os pontos juntos: qualquer resultado é compatível com qualquer verdade |
| 4/8 | 0,50 | [0,16; 0,84] | 0,69 | teste = 2024 (8 dias), treino = 2023 (6): divisão temporal natural |
| 6/8 | 0,75 | [0,35; 0,97] | 0,62 | um bom resultado em 2024 ainda não exclui recall de 0,35 |
| 8/8 | 1,00 | [0,63; 1,00] | 0,37 | o melhor resultado possível em 2024 |
| 7/14 | 0,50 | [0,23; 0,77] | 0,54 | todos os dias de 2023–2024 como teste (só possível sem ajuste aos rótulos, opção D) |
| 12/15 | 0,80 | [0,52; 0,96] | 0,44 | os 15 dias da cidade |
| 15/15 | 1,00 | [0,78; 1,00] | 0,22 | idem, recall perfeito |
| 10/20 | 0,50 | [0,27; 0,73] | 0,46 | o `N_min = 20` aprovado (MODELAGEM-IA A.1.4) dá ±0,23, como previsto lá |

Quantos positivos independentes seriam necessários para um recall de 0,5 com meia-largura de 95% de ±0,15:
`n ≈ 1,96² · 0,25 / 0,15² ≈ 43`; para ±0,10, `n ≈ 96` (aproximação normal; cálculo deste memorando). Hoje há 14–15.

**Precisão.** O denominador é o número de alertas, não de positivos, então pode ser maior. Exemplo descritivo do
Apêndice B: a regra "chuva diária ≥ 10 mm" na grade do Derby, em 2023–2024, dispara em 52 dias e cobre 8 dos 14
positivos: precisão 8/52 = 0,15, IC [0,07; 0,28]; recall 8/14 = 0,57, IC [0,29; 0,82]. A precisão tem intervalo
mais estreito, mas é pessimista por construção: um dia de chuva forte sem registro no CKAN pode ser alagamento não
notificado (MODELAGEM-IA A.3.3).

### 1.4 O que um classificador pode e não pode demonstrar

**Comparar com o baseline.** Comparação pareada nos mesmos dias positivos (McNemar exato): só os dias em que um
acerta e o outro erra informam. Com todos os discordantes a favor do modelo, o p bilateral é 0,25 com 3, 0,125 com
4, 0,0625 com 5 e 0,031 com 6 (`2 · 0,5^m`; Apêndice A). Com 8 positivos de teste, o modelo precisa acertar **pelo
menos 6 dias que a regra de chuva erra, sem errar nenhum que ela acerta**, para p < 0,05. Como a regra de ≥ 10 mm já
cobre 8 dos 14 (Apêndice B), sobram ~6 dias "difíceis" no total de 2023–2024, e parte deles cai no treino. A
superioridade é praticamente indemonstrável, mesmo que exista. Diferença de PR-AUC com bootstrap em blocos de evento
(MODELAGEM-IA A.3.9) sofre do mesmo limite: reamostrar 8 positivos produz intervalos que incluem zero em quase
qualquer cenário realista.

**Sobreajuste.** A tabela de features da MODELAGEM-IA (A.2.3) tem 25 linhas "disponíveis" e 38 no total. Eventos
por variável: 14/25 = 0,56; 14/38 = 0,37. A regra prática para regressão logística pede ≥ 10 eventos por variável
(Peduzzi et al., 1996, *J Clin Epidemiol* 49:1373) e o cálculo de tamanho de amostra de Riley et al. (2020, *BMJ*
368:m441) pede mais quando a taxa de base é baixa. Com 14 positivos, um modelo honesto suporta **1 a 2
preditores**. XGBoost com 25 features vai separar os 6 dias de treino perfeitamente e não dirá nada sobre 2024.

**Calibração e limiares.** A.1.5 exige calibração em validação separada e limiares escolhidos na validação. Com 14
positivos, a divisão treino/validação/teste deixa ~4–5 positivos por bloco. Isotônica com 5 positivos é uma escada
de 5 degraus; Platt com 5 positivos estima 2 parâmetros com erro maior que o efeito. A curva de confiabilidade terá
1 a 2 positivos por faixa. **Probabilidade calibrada não é demonstrável**, e `LOW/MEDIUM/HIGH` (ADR 0007) perde a
justificativa de A.1.5.

**Métricas por ponto (skill §6).** A 500 m: 1 evento-dia no Ibura, 2 no Derby, 0 nos demais (snapshot, resumo
item 4). A 3 km: ~13 por ponto, mas os mesmos 13 dias em quatro pontos (F2-BACK-16 §2c), então a "métrica por
ponto" mede quatro vezes o mesmo rótulo da cidade, deslocado por qual centróide de bairro caiu mais perto. Recall
por mecanismo (chuva × maré) também é impossível: sem maré em 2023–2024 e com 2 dias positivos quase sem chuva
(Apêndice B), não há como classificar mecanismo. **Métricas por ponto e por mecanismo ficam impossíveis**, e a skill
§6 precisa registrar a exceção.

### 1.5 O rótulo mede o alagamento ou o registro?

Leitura descritiva (Apêndice B; grade Open-Meteo, que é reanálise e suaviza chuva convectiva, ADR 0006 limitação 2):

| Medida (Derby, 2023–2024, 731 dias) | Valor |
|---|---|
| Chuva diária nos 14 dias positivos | mínimo 0,2 mm · mediana 11,3 mm · máximo 38,2 mm |
| Posto da chuva do dia positivo entre 731 dias | de 2º a 659º; mediana ≈ 45º |
| Dias com ≥ 20 mm / desses, com alagamento registrado | 12 / 3 |
| Dias com ≥ 10 mm / desses, com alagamento registrado | 52 / 8 |
| Dias positivos com ≤ 3,5 mm no dia e ≤ 0,6 mm no dia seguinte | 2024-01-15 (0,2 mm; véspera 0,3 mm) |
| Maior chuva diária da grade em 2023–2024 | 41,1 mm (Imbiribeira); nenhum dia ≥ 50 mm |

Três leituras, não exclusivas, que a dissertação precisa declarar:

1. **Baixa sensibilidade do registro.** A categoria "alagamento" nos atendimentos da Defesa Civil soma ~100
   ocorrências/ano em 65–90 mil registros (snapshot, tabela de rótulos). Em 2021 há 1 em 64.855. Alagamento de rua
   provavelmente é atendido por outros órgãos (drenagem, trânsito) e não chega a esse banco `[hipótese; sem fonte]`.
   O 0 da classe negativa é "sem registro", e o problema é de positivos e não rotulados (MODELAGEM-IA A.3.3).
2. **Chuva da grade ruim para extremos.** Uma reanálise de dezenas de km com máximo de 41 mm/dia em dois anos não
   representa as chuvas convectivas que alagam o Recife `[comparar com os pluviômetros APAC/Cemaden, hoje em
   quarentena de fuso; ADR 0006 §4]`. Isso afeta todas as opções que usam chuva, inclusive o modelo físico.
3. **Outros mecanismos.** Um dia positivo com 0,2 mm pode ser maré, rompimento de adutora, registro tardio ou erro de
   data `[a verificar no payload das 3 ocorrências de 2024-01-15]`.

**Conclusão do diagnóstico.** Com 14–15 positivos independentes, de um registro de sensibilidade desconhecida e
provavelmente baixa, na resolução de bairro e sem hora: (a) não se demonstra superioridade de um classificador sobre
um baseline; (b) não se demonstra calibração; (c) não existem métricas por ponto nem por mecanismo; (d) o que se pode
fazer com rigor é **verificação** (o modelo acerta os dias conhecidos? com que taxa de alarme?) de um modelo que **não
foi ajustado a esses rótulos**, com intervalos largos e declarados.

---

## 2. Opções de desenho

Legenda de esforço: P = até 2 semanas; M = 2 a 6 semanas; G = mais de 6 semanas ou dependente de terceiros.

### A — Manter supervisionado por ponto, com raio maior

- **Pergunta.** A mesma: "haverá alagamento no ponto `p` no dia `d`?" (tarefa (b) já acionada pela regra
  pré-registrada; MODELAGEM-IA A.1.4, regra 3; snapshot item 3).
- **Dados.** Já existem: raio de 2 ou 3 km (`linkage_config` 5 e 2, F2-BACK-16 §2).
- **Positivos.** 34 pares em 13 dias (2 km) ou 52 em 14 dias (3 km) (F2-BACK-16 §2a).
- **Validade interna.** Nula para "ponto". A 3 km, o rótulo do ponto é o rótulo da região: AV_RECIFE_IBURA e
  MASCARENHAS_IMBIRIBEIRA distam 2.695 m e os círculos se sobrepõem (MODELAGEM-IA A.3.1, item 4); 22 ocorrências caem
  em 2 ou mais círculos (F2-BACK-16 §2c); a distância mediana ao ponto é 2.456 m, logo o casamento mede onde caiu o
  centróide do bairro (F2-BACK-16, resumo item 3). Os 52 pares parecem 3,7 vezes mais dados que os 14 dias, mas não
  são independentes: tratar como 52 infla a evidência, e o `GroupKFold` por evento os colapsa de volta a ~14.
- **Validade externa.** Nenhuma: o modelo aprende "dia de alagamento em alguma parte do Recife" e o rotula com o nome
  de uma rua.
- **Risco na banca.** Alto. A primeira pergunta será "a quantos metros do ponto ocorreu o alagamento que o senhor
  chama de positivo?", e a resposta (mediana 2,5 km, coordenada de centróide) derruba o rótulo. Mudar o raio depois
  de ver as contagens também é escolha guiada pelos dados: a MODELAGEM-IA fixou 500 m antes (A.3.1, item 4; resposta
  do autor 4).
- **Esforço.** P.
- **Aproveita.** Todo o pipeline e o export.
- **Veredito.** **Não se sustenta.** É a opção B disfarçada de A, com pseudo-replicação.

### B — Mudar a unidade de análise: dia × cidade ou dia × bairro, com os pontos como estudo de caso

Duas perguntas diferentes cabem aqui, e convém não misturá-las.

**B1 — "Quando": classificação diária de evento na cidade.** "Com dados disponíveis às 06:00, o dia `d` terá
alagamento registrado no Recife?"

- **Dados.** Existem: clima horário, solo ERA5-Land, rótulo agregado por dia das 197 ocorrências canônicas. Falta: maré
  2021–2025 (backfill da tábua) e um export diário (hoje o export é por ponto-hora).
- **Positivos.** 14 (janela do export) ou 15; 17–18 se 2022 for recuperado (3 dias, F2-BACK-16 "2022").
- **Validade interna.** Honesta quanto à unidade (o rótulo tem resolução de cidade). Continua com todos os limites
  da seção 1.3–1.4: n = 14, superioridade indemonstrável, calibração frágil.
- **Validade externa.** Recife, quadra chuvosa de 2023–2024, registro da Defesa Civil.
- **Risco na banca.** Médio. A banca aceita "cidade-dia" se o resultado for apresentado como inconclusivo quando for.
  Perde-se o produto "risco por ponto com 3–6 h" (ADR 0007), que deixa de ter validação.
- **Esforço.** P a M (agregação diária e backfill da maré).
- **Aproveita.** Pipeline, export horário (agregado no projeto-mapi-ai), features de chuva e solo, regra de corte das
  06:00 (resposta do autor 3).

**B2 — "Onde": suscetibilidade por bairro condicionada ao dia de evento.** "Num dia em que há alagamento registrado
no Recife, quais bairros registram?"

- **Dados.** O campo `Bairro` existe no payload bruto para as 197 ocorrências, 0 sem bairro (F2-BACK-16 §3), mas
  `flood_occurrence` não tem coluna de bairro (mudança de código). Atributos por bairro: o mosaico do PE3D cobre a
  área (48 folhas; `TERRENO-PE3D.md` §2), então cota, fração em depressão e declividade por bairro são calculáveis;
  falta o polígono oficial dos bairros `[fonte a obter: base cartográfica da Prefeitura do Recife]`.
- **Positivos.** 146 pares bairro-dia em 39 bairros e 15 dias (F2-BACK-16 §3, soma da coluna "Bairros").
- **Validade interna.** Melhor que B1 para efeitos espaciais (terreno ganha variação real, dezenas de bairros em vez
  de 4 pontos; resolve a crítica de A.2.4, item 1). Ameaça forte: notificação desigual entre bairros (renda, densidade,
  presença de morro, atenção da Defesa Civil; MODELAGEM-IA A.3.3, "Heterogeneidade"). Os 146 pares estão em 15 dias,
  então a validação cruzada deve agrupar por dia e por bairro.
- **Validade externa.** Média: a pergunta "onde" é a que a Defesa Civil usa para posicionar equipe num dia de alerta.
- **Risco na banca.** Médio. É outra pergunta (suscetibilidade, não previsão temporal) e precisa ser defendida como
  tal.
- **Esforço.** M (bairro no banco, polígonos, atributos de terreno por bairro).
- **Aproveita.** Pipeline de terreno (`tools/terreno/run.sh`), rótulo canônico, metodologia de casamento.

Os pontos críticos entram como **estudo de caso** nas duas variantes: séries do balde/Petri nos 4 pontos com
terreno nos dias positivos e negativos, sem métrica estatística por ponto. MASCARENHAS_IMBIRIBEIRA fica no bairro com
mais dias registrados (Imbiribeira, 20 ocorrências em 13 dias; F2-BACK-16 §3) `[a confirmar a qual bairro cada ponto
pertence, com o polígono oficial]`.

### C — Ampliar a fonte de rótulo

É a única opção que preserva a pergunta supervisionada original. Para chegar ao critério de ±0,15 (≈ 43 positivos
independentes, seção 1.3), faltam ~30 dias independentes de alagamento em 2021–2026, com local e, de preferência,
hora. Fontes possíveis, com viabilidade realista:

| Fonte | O que traria | Viabilidade / custo | Positivos esperados | Vazamento / viés |
|---|---|---|---|---|
| **Defesa Civil do Recife, registro interno** (sistema que alimenta o CKAN), por pedido formal ou Lei de Acesso à Informação (Lei 12.527/2011) | hora do chamado, endereço ou coordenada, 2016–2026, inclusive 2025–2026 que o portal não publica | pedido escrito: P; resposta: prazo legal de 20 dias prorrogáveis por 10 (art. 11), na prática incerto; G se depender de convênio | mesmos tipos de evento do CKAN, com hora; só ganha se o interno tiver mais registros que o portal `[desconhecido]` | mesmo viés de notificação do CKAN; nenhum vazamento novo |
| **Central 199 (Defesa Civil) e central de serviços da Prefeitura (drenagem, Emlurb)** | chamados de "alagamento", "bueiro entupido", "canal transbordando", com hora e endereço | pedido formal: G (terceiros, sistemas diferentes) | potencialmente muito mais registros, se alagamento de rua for atendido ali (hipótese da seção 1.5) `[sem fonte]` | chamados de manutenção não são alagamento; precisa de filtro textual validado à mão; viés de renda e de canal |
| **Notícias com data e local** (jornais locais, portal G1 PE, TV) | dia, rua ou bairro; às vezes hora e lâmina em foto | M a G: busca nos acervos, curadoria manual com protocolo escrito (dupla codificação de uma amostra para medir concordância) | dezenas de dias de chuva forte em 5 anos `[estimativa sem fonte; medir com um piloto de 1 ano]` | **seleção por intensidade**: a imprensa cobre dia de chuva forte, então o rótulo fica correlacionado com o preditor e infla o recall do baseline de chuva; cobertura maior em bairros centrais. A notícia cita chuva da APAC: o texto não pode entrar como feature |
| **Redes sociais** (posts de moradores, perfis da Defesa Civil e da Prefeitura) | hora, foto, local aproximado | G: API do X tem custo alto desde 2023 `[verificar preço atual]`; Instagram sem API de busca histórica para pesquisa; raspagem esbarra em termos de uso e LGPD | incerto | viés de idade, renda e bairro; duplicação; mesma seleção por intensidade |
| **Alertas de trânsito (Waze for Cities, CTTU)** | coordenada e hora de "via alagada" | G: acesso via parceria com o órgão municipal `[verificar se o Recife participa e se o histórico é cedido]` | potencialmente o melhor rótulo espacial-temporal `[sem fonte]` | só vias com tráfego; subnotifica à noite e em ruas locais |
| **S2iD / Atlas Digital de Desastres (governo federal)** | município-dia, código Cobrade (alagamento, inundação, enxurrada), para toda a RMR | P: base pública `[confirmar cobertura 2021–2026 para Recife, Olinda, Jaboatão, Paulista, Camaragibe]` | poucos por município; só eventos com registro formal ou decretação | só eventos grandes; unidade = município. Útil para **B1 em várias cidades da RMR** (mais unidades independentes), não para pontos |
| **Alertas da Cemaden e avisos da APAC** | dia e área de alerta | P | muitos | **circular**: o alerta é disparado por limiar de chuva; usar como rótulo é rotular com o preditor. Serve como baseline ou como comparação operacional, nunca como rótulo |
| **CKAN 2022 com mapeamento de colunas** | 28 registros em 3 dias, só rua sem número | P a M (parser alternativo, F2-BACK-16 pendência 1) | +3 dias, incluindo 2022-05-28 | mesmo viés do CKAN; endereço corrompido |
| **Outras categorias do próprio CKAN** (inundação, enxurrada, vistoria ligada a alagamento) | mais dias | P (contagem no banco) | `[desconhecido; medir]` | **reabre a decisão do autor** de usar só "alagamento" (MODELAGEM-IA, cabeçalho; skill §4) |

- **Pergunta.** A original pode ser mantida, se a fonte trouxer local e hora.
- **Validade.** Juntar fontes muda o significado do rótulo ("registrado na Defesa Civil" vira "reportado por
  qualquer canal"); a dissertação precisa de um protocolo de fusão e de deduplicação escrito antes de ver o
  desempenho.
- **Risco na banca.** Baixo se a fonte for institucional e documentada; alto se for curadoria manual sem protocolo.
- **Esforço.** G e **com prazo fora do controle do autor**. Não dá para planejar a defesa contando com a resposta.
- **Aproveita.** Todo o pipeline: `flood_occurrence` aceita qualquer fonte com precisão `HOUR`/`DAY` (ADR 0006 §6).

### D — Inverter o papel: o modelo físico como modelo principal, o ML como emulador ou correção

- **Pergunta.** "Um modelo físico-estocástico de balanço de massa (balde + rede de Petri), parametrizado *a priori*
  por terreno, chuva e maré, distingue os dias com alagamento registrado dos demais? Um emulador de ML reproduz o
  modelo estocástico com fidelidade mensurável e custo menor?"
- **Por que o n de avaliação melhora.** Um modelo com parâmetros fixados **sem olhar os rótulos** (literatura,
  terreno, norma de drenagem) não precisa de treino nem de validação: os 14 dias de 2023–2024 inteiros viram teste
  (recall 7/14, IC [0,23; 0,77], em vez de 4/8, IC [0,16; 0,84]; seção 1.3). Com no máximo 1 parâmetro global
  ajustado (por exemplo, a lâmina crítica `h_crit`), ainda cabe validação cruzada deixando um episódio de fora.
- **Papel do ML.**
  1. **Emulador (surrogate):** a rede de Petri gera trajetórias para combinações de chuva, maré e terreno; o ML
     aprende `p_exceed` (MODELAGEM-IA A.3.8, tabela do B3). Rótulo sintético ilimitado; métrica = fidelidade ao
     simulador (Brier, erro de `p_exceed`, tempo de inferência). Demonstrável com rigor; contribuição de computação
     clara para quem desenvolve a ferramenta Mercury.
  2. **Correção de viés da entrada:** ML que corrige a chuva da reanálise contra pluviômetros (APAC/Cemaden). Rótulo
     abundante (chuva medida, não alagamento). Depende de liberar a quarentena de fuso (ADR 0006 §4).
  3. **Correção do resíduo do modelo físico com os eventos reais:** **não** demonstrável com 14 positivos (mesmo
     limite da seção 1.4). Fica fora ou como exploratório.
- **O que acontece com "ML vence o baseline físico" (A.3.9).** O argumento central **deixa de existir** na forma
  original. Ele é substituído por dois: (i) "o modelo físico tem habilidade acima da climatologia e da regra de
  chuva nos dias reais?" (verificação, com IC); (ii) "o emulador reproduz o físico?" (fidelidade). A comparação com
  um classificador supervisionado continua como experimento secundário, reportado como inconclusivo se for.
- **Dados.** Chuva (existe; qualidade ruim para extremos, seção 1.5), solo (existe), maré 2021–2025 (**backfill
  necessário**), terreno de 4 pontos (existe, com área contribuinte instável: Derby 62 m², Ibura 208 m² vs. 573.223 m²
  entre motores; `TERRENO-PE3D.md` §5 e §7), coeficiente de escoamento (MapBiomas, não coletado; MODELAGEM-IA A.2.3
  linha 34), capacidade da galeria (sem cadastro de drenagem; `[sem fonte]`), soleira (sem fonte; ADR 0009). O bloqueio
  pela maré na forma absoluta continua proibido até o Δ do ADR 0009.
- **Ponto fraco honesto.** O modelo físico é de **ponto**, o rótulo é de **cidade/bairro**. A ponte possível: a
  série do ponto como índice do bairro onde ele está, ou um balde agregado por bairro com o terreno do PE3D. Sem essa
  ponte declarada, a verificação compara grandezas diferentes. E nos dois pontos com bacia de dezenas de m² (Derby,
  Ibura no Whitebox), o balde quase não recebe água por construção (MODELAGEM-IA A.3.8, "Limitação já visível").
- **Validade interna.** Boa quanto a vazamento (nenhum parâmetro vê o rótulo). Fraca quanto à identificação da
  física: 14 dias não distinguem "modelo certo" de "modelo de chuva disfarçado".
- **Validade externa.** Melhor que ML: um modelo físico transfere para outros pontos com terreno, o ML não
  (MODELAGEM-IA A.2.4, item 3).
- **Risco na banca.** Médio. Pergunta provável: "o senhor ajustou o modelo físico olhando os eventos?". A resposta
  precisa ser um protocolo escrito antes. Segunda pergunta: "onde está o aprendizado de máquina?": no emulador.
- **Esforço.** M a G (backfill da maré, ponte ponto↔bairro, emulador, export do Petri no formato da A.3.8).
- **Aproveita.** Balde (A.3.8), rede de Petri do autor (resposta do autor 11), terreno, export horário, contrato do
  ADR 0007 (`riskLevel` passa a vir do físico ou do emulador, com `modelVersion` próprio).

### E — Detecção de condição meteorológica extrema (proxy), validada contra os dias reais

- **Pergunta.** "Os dados abertos e o modelo antecipam dias de chuva extrema (≥ N mm em X h)?", com verificação
  secundária: "que fração dos dias de alagamento registrado é dia de chuva extrema?"
- **Deixar explícito:** proxy não é alagamento. Prever chuva extrema é previsão do tempo, não de alagamento urbano;
  2 dos 14 dias positivos têm ≤ 3,5 mm na grade (Apêndice B), e 9 dos 12 dias de ≥ 20 mm não têm registro.
- **Dados.** Precisa do **rótulo de chuva medido por pluviômetro** (APAC/Cemaden), não da reanálise, porque prever a
  reanálise com a reanálise é circular. Isso exige resolver a quarentena de fuso (ADR 0006 §4). Também precisa de
  previsão de chuva arquivada para ter antecedência (o pipeline não arquiva; MODELAGEM-IA A.1.4 (b)).
- **Positivos.** Dependem de N e X. Na reanálise do Derby, ≥ 30 mm/dia ocorre em 22 dias em 5 anos e ≥ 50 mm em 6
  (Apêndice B); no pluviômetro deve ser mais `[medir]`.
- **Limiar.** **N e X precisam vir de fonte externa** (limiares de alerta da APAC ou da Cemaden, ou chuva de projeto
  da curva IDF do Recife `[fonte a anexar]`), porque este memorando já olhou a chuva dos dias positivos.
- **Validade.** Interna boa para a pergunta meteorológica; externa irrelevante para alagamento.
- **Risco na banca.** Alto se vendido como previsão de alagamento; baixo como baseline operacional.
- **Esforço.** M.
- **Aproveita.** Pipeline de clima, B1 da A.3.8.
- **Uso recomendado:** como **baseline** fixo (regra de chuva com limiar externo) e como análise de quanto do rótulo
  se explica só por chuva; não como pergunta principal.

### F — Combinações

| Combinação | O que é | Positivos para a afirmação principal | Risco |
|---|---|---|---|
| **F1 = D + B1 + E como baseline** | físico-estocástico principal, verificado em dia × cidade (ou bairro do ponto) sem ajuste aos rótulos; ML como emulador; E como baseline; classificador diário supervisionado como exploratório | 14–15 (todos como teste) | médio |
| **F2 = B1/B2 + C** | supervisionado diário (cidade) e espacial (bairro), com rótulo ampliado (Defesa Civil interna, S2iD na RMR, 2022) e portão de decisão por contagem | 14 hoje; ≥ 43 se C entregar | alto no prazo (C depende de terceiros) |
| F3 = B2 sozinho | suscetibilidade espacial por bairro | 146 pares, 39 bairros, 15 dias | médio; muda a pergunta para "onde" |
| F4 = A + qualquer coisa | — | — | não recomendado (seção A) |

---

## 3. Recomendação

### 3.1 Principal: F1 — físico-estocástico como modelo principal, verificação em dia × cidade, ML como emulador

**Nova pergunta de pesquisa (proposta de redação):** "Um modelo físico-estocástico de alagamento (balanço de massa
e rede de Petri), alimentado só por dados abertos (reanálise de chuva, umidade do solo, maré prevista e terreno
LiDAR), parametrizado sem os registros de alagamento, distingue os dias com alagamento registrado no Recife em
2023–2024 melhor que a climatologia e que uma regra de chuva com limiar externo? E um emulador de aprendizado de
máquina reproduz esse modelo com fidelidade e custo que permitam uso operacional?"

**Por que esta e não outra.**
1. É a única que usa **todos** os 14–15 dias como teste (seção 1.3), porque o modelo não é ajustado a eles.
2. A afirmação de ML (emulação) é demonstrável com rigor, porque o rótulo é sintético e abundante.
3. Aproveita o que é próprio do autor (Mercury) e o terreno, que no ML seria só identidade do ponto
   (MODELAGEM-IA A.2.4, item 1).
4. Aceita honestamente o resultado provável ("habilidade não distinguível da regra de chuva, com IC largo") sem
   invalidar a dissertação, porque a contribuição não depende de vencer.

**O que muda em `docs/MODELAGEM-IA.md`** (edição cirúrgica, depois da decisão):
- Cabeçalho e A.1.2: tarefa principal passa a ser "verificação diária do modelo físico-estocástico"; tarefa (b)
  supervisionada fica **exploratória**; (a) horária fica **suspensa** por `N_HOUR = 0` (snapshot, item 3).
- Nova A.1.6 "Resultado da regra de troca e escassez de rótulo": registrar N_HOUR = 0, 14–15 dias, 146 pares
  bairro-dia, e as contas da seção 1 deste memorando.
- A.2.4: os pontos viram estudo de caso; nenhuma métrica estatística por ponto. Registrar a exceção à skill §6.
- A.3.1 item 4: raio de 500 m deixa de ser critério de rótulo para treino (a resolução real é o bairro).
- A.3.8–A.3.9: o balde e o Petri deixam de ser baselines e viram o modelo; os baselines passam a ser climatologia,
  "nunca alaga" e regra de chuva com limiar **externo**. A.3.9 é reescrita (o argumento "vencer o físico" sai).
- Protocolo de parametrização *a priori* do balde e do Petri, **escrito e versionado antes de rodar contra os dias
  positivos** (quais parâmetros, de que fonte, e no máximo 1 parâmetro global ajustado com validação deixando um
  episódio de fora).
- "Riscos para a validade", item 1: de risco para fato.

**O que muda no export e nos dados** (cada item é tarefa de código, não feita aqui):
1. **Backfill da maré prevista 2021–2025** (hoje só 2026; Apêndice B). Bloqueante para qualquer uso de maré.
2. Gravar `Bairro` em `flood_occurrence` e o nível da geocodificação (F2-BACK-16, pendência 3).
3. Export diário (dia local × cidade, opcionalmente × bairro): features até 06:00 local, rótulo = existe ocorrência
   canônica no dia, número de ocorrências e de bairros. Pode ser agregação no projeto-mapi-ai a partir do horário.
4. Export do Petri no formato da A.3.8, com granularidade diária.
5. Decidir 2022 (parser alternativo: +3 dias) e marcar 2025–2026 como **sem rótulo** (não como negativo) no export.
6. Prioridade alta para liberar a quarentena de fuso dos pluviômetros (ADR 0006 §4): sem chuva medida, o modelo
   físico roda com uma chuva que não vê os extremos (seção 1.5).

**O que muda nos experimentos.**
- Pré-registro (documento versionado, com data e hash) antes de qualquer execução contra os rótulos: unidade
  (dia × cidade), período (2023–2024; 2021 e 2022 como análise de sensibilidade), limiar externo da regra de chuva,
  definição de episódio (dias a ≤ 3 dias de distância), métricas.
- Métricas com n pequeno: recall e taxa de alarme com IC exato de Clopper-Pearson; curva de recall × alarmes por mês;
  comparação pareada por McNemar exato; Brier skill score contra climatologia com bootstrap por episódio. Reportar o
  poder: "com 14 positivos, só uma diferença de ≥ 6 dias discordantes seria detectável".
- Emulador: treino em simulações, teste em simulações de período disjunto e nas condições dos 731 dias reais;
  métrica de fidelidade e de tempo.
- Classificador supervisionado diário: no máximo 2–3 features (EPV, seção 1.4), validação deixando um episódio de
  fora, resultado rotulado **exploratório**.

**Limitações que devem ser declaradas.**
1. O rótulo é o registro de atendimento da Defesa Civil: sensibilidade desconhecida e provavelmente baixa
   (seção 1.5); o negativo é "sem registro".
2. 14–15 dias positivos, 2 anos úteis; nenhum IC de recall terá largura menor que ~0,4 (seção 1.3).
3. Resolução de bairro, sem hora: o produto "risco por ponto com 3–6 h" (ADR 0007) **não é validado**; o `riskLevel`
   em produção passa a ser saída de modelo físico ou emulador, sem calibração empírica.
4. Chuva de reanálise sem extremos convectivos (máximo de 41 mm/dia em 2023–2024 na grade; Apêndice B).
5. Terreno: área contribuinte instável e cega a galerias (`TERRENO-PE3D.md` §7–8); sem soleira e sem Δ vertical
   (ADR 0009).
6. Ponte ponto ↔ cidade/bairro: o modelo físico é de ponto e o rótulo não.

### 3.2 Alternativa: F2 — supervisionado diário com rótulo ampliado, com portão de decisão

Se o autor e o orientador considerarem essencial manter a pergunta "ML vence baseline":

- Disparar **já** os pedidos da opção C com maior chance: Defesa Civil do Recife (registro interno com hora e
  endereço, 2016–2026), S2iD/Atlas para os municípios da RMR, e a contagem das outras categorias do CKAN.
- **Portão pré-registrado:** numa data fixada agora `[data a definir pelo autor e orientador]`, contar os dias
  positivos independentes com local. Se ≥ 43 (IC de ±0,15 para recall 0,5; seção 1.3), o supervisionado diário
  (B1, cidade ou município) volta a ser principal e a comparação com o físico vira A.3.9 na escala da cidade. Se
  < 43, vale a recomendação principal (F1), sem renegociar.
- B2 (bairro) entra como segunda pergunta, porque tem 146 pares e dá ao terreno um papel que 4 pontos não dão.
- Risco: o prazo. Toda a semana de espera por terceiros é semana sem experimento; por isso o F1 deve avançar em
  paralelo, e não depois.

### 3.3 O que não recomendo

- **A** (raio maior por ponto): pseudo-replicação e rótulo de outro lugar (seção 2, A).
- **E como pergunta principal**: troca alagamento por meteorologia.
- **Treinar XGBoost com as 25 features nos 14 dias e reportar PR-AUC**: vai parecer resultado e não é (seção 1.4).

---

## 4. Perguntas ao autor

1. Aceita que a pergunta da dissertação mude? Se sim, a redação da seção 3.1 serve como ponto de partida?
   **(Depende do orientador e possivelmente do programa, se a pergunta constar do projeto qualificado.)**
2. Prefere a recomendação principal (F1, físico + emulador) ou a alternativa (F2, supervisionado com rótulo ampliado
   e portão)? **(Orientador.)**
3. Um resultado "inconclusivo" ou negativo (modelo não distinguível da regra de chuva, com IC largo) é aceitável
   como resultado principal para o orientador e a banca? **(Orientador.)**
4. O emulador de ML da rede de Petri é aceito como a contribuição de aprendizado de máquina? **(Orientador.)**
5. Se F2: qual a data-limite do portão e o critério (proposta: ≥ 43 dias positivos independentes com local)?
6. Autoriza enviar os pedidos à Defesa Civil do Recife (registro interno com hora e endereço) e consultar o
   S2iD/Atlas para a RMR? Há contato institucional (orientador, convênio) que encurte o prazo?
7. Reabre a decisão "só alagamento" para medir quantos dias as categorias inundação e enxurrada acrescentariam,
   mesmo que só como análise de sensibilidade?
8. Recupera o CKAN 2022 (parser com mapeamento de colunas; +3 dias, endereço só com rua)?
9. 2025–2026 entram no export como "sem rótulo" (excluídos da avaliação) em vez de negativos? E 2021 (1 registro em
   64.855) entra como período rotulado ou como sem rótulo?
10. O balde e a rede de Petri podem ser parametrizados sem olhar os eventos (fontes de coeficiente de escoamento,
    capacidade de galeria e lâmina crítica)? Qual o único parâmetro, se algum, que precisaria de ajuste?
11. Qual a ponte ponto ↔ rótulo: (a) cidade-dia com "algum dos 4 pontos excede"; (b) bairro do ponto; (c) balde
    agregado por bairro com o PE3D? Isso define se o terreno por bairro (opção B2) entra.
12. Prioriza o backfill da maré prevista 2021–2025 e a liberação da quarentena de fuso dos pluviômetros como as duas
    próximas tarefas de dados?
13. A pergunta B2 ("onde", 146 pares bairro-dia) interessa como segunda pergunta, ou fica fora do escopo?
14. Mantém o contrato do ADR 0007 com `riskLevel` vindo do modelo físico/emulador, declarando que não há calibração
    empírica, ou suspende o `riskLevel` por ponto até haver rótulo?
15. Confirma que este memorando "queimou" a inspeção da chuva nos dias positivos e que, por isso, o limiar da regra
    de chuva virá de fonte externa (APAC/Cemaden ou curva IDF)?

---

## Apêndice A — Contas binomiais

- IC exato de Clopper-Pearson a 95%: limite inferior = maior `p` com `P(X ≥ k | n, p) ≤ 0,025`; superior = menor `p`
  com `P(X ≤ k | n, p) ≤ 0,025`, resolvidos por bisseção sobre a CDF binomial exata (Python 3 sem bibliotecas,
  `math.comb`). Conferido contra o intervalo de Wilson (ex.: 4/8 → Wilson [0,22; 0,79]; 7/14 → [0,27; 0,73]).
- Tamanho para meia-largura `w` com recall 0,5: `n = 1,96² · 0,5 · 0,5 / w²` → 42,7 (w = 0,15) e 96,0 (w = 0,10).
- McNemar exato: com `m` pares discordantes, todos a favor de um modelo, `p bilateral = 2 · 0,5^m`.
- EPV: positivos / número de features candidatas (14/25 e 14/38, contagem de status da MODELAGEM-IA A.2.3).

## Apêndice B — Leitura descritiva da chuva nos dias positivos

- Entrada: `MAPI/exports/AGAMENON_DERBY.csv` e `MASCARENHAS_IMBIRIBEIRA.csv` (SHA-256 em `MAPI/exports/SHA256SUMS.txt`,
  iguais aos do snapshot), coluna `weatherPrecipitation` (Open-Meteo archive, mm/h).
- Dia local: cada linha `timestampUtc = t` cobre `(t − 1 h, t]` (ADR 0006 §5); dia local America/Recife do
  intervalo = data de `t − 4 h`. Dia completo = ≥ 23 horas com chuva (1.825 dias completos em cada arquivo).
- Dias positivos: os 14 de 2023–2024 da série diária de F2-BACK-16 §3.
- Resultados (Derby / Imbiribeira): mediana da chuva nos positivos 11,3 / 13,8 mm; máximo 38,2 / 41,1 mm; dias
  ≥ 10 mm em 2023–2024: 52 / 60, cobrindo 8 / 10 positivos; ≥ 20 mm: 12 / 15, cobrindo 3 / 4; ≥ 30 mm: 3 / 6,
  cobrindo 2 / 3; dias ≥ 30 mm em 5 anos: 22 / 28; ≥ 50 mm em 5 anos: 6 / 7. 2024-01-15: 0,2 mm no dia, 0,3 mm na
  véspera, 0,6 mm no dia seguinte (Derby). 2024-02-16 (21 ocorrências em 14 bairros): 3,5 mm no dia, 17,5 mm na
  véspera (Derby).
- Maré: `tideHeight` não vazio em 6.560 horas, todas em 2021-10-01 (7 h) e 2026-01 a 2026-10; 0 horas em 2023–2024.
- Nada disso é avaliação de modelo; é contagem descritiva para o diagnóstico. Consequência registrada no cabeçalho:
  o limiar da regra de chuva não pode mais ser escolhido a partir destes números.
