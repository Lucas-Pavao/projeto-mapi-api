# 0009 — Referência vertical entre o MDT do PE3D e a tábua de maré

- Status: **aprovado** (2026-10-10, Lucas, pergunta 23): opção C agora (só cotas relativas) + B em paralelo; pedidos institucionais redigidos em `docs/pedidos/`.
- Data: 2026-10-10
- Decisores: Agente 5 (Engenheiro Geoespacial, autor), checkpoint Máximo (pendente), Lucas
- Tarefa: F3-GEO-03

## Contexto

O MAPi quer comparar o **nível lá fora** (maré prevista + resíduo, ou nível do rio) com a **soleira** e a **cota da rua** (`docs/GLOSSARIO.md`). As duas pontas estão em referências verticais diferentes:

| | Cota do MDT do PE3D | Altura da tábua de maré |
|---|---|---|
| Referência | Datum vertical do SGB (altitude), provavelmente Imbituba. **Não confirmado** (ver abaixo) | Nível de Redução (NR) da DHN, Porto do Recife |
| Natureza | Superfície ~ geoide / nível médio do mar de Imbituba, 1949–1957 | Plano "tão baixo que a maré raramente cai abaixo dele"; ligado à baixa-mar de sizígia |
| Fonte | Voo de 2016, erro altimétrico especificado < 25 cm (1:5 000) e < 10 cm (1:1 000) (extrato 8) | Tábua DHN via TabuaMare, `pe02`, `mean_level` = 1,28 m (extrato 3) |

Sem o desnível entre as duas, "nível lá fora − cota da rua" é um número sem significado físico. Um erro de 0,3 m aqui desloca todas as regras de refluxo: em Recife, a excursão da maré em 2026-10-10 foi de 2,3 m (preamar 2,45 m, baixa-mar 0,15–0,20 m em 2026-10-10; extrato 3), então 0,3 m é mais de 10% dessa excursão.

### Referência 1 — MDT do PE3D

- **Datum vertical: não confirmado por fonte primária.** Nenhum documento do PE3D consultado declara datum ou geoide (extrato 8). Os metadados do GeoTIFF não trazem nem o CRS (skill `mapi-geo-pe3d`).
- Evidência indireta: em 9 pontos de controle GNSS do IBGE em Cabo de Santo Agostinho, MDT − GNSS tem média +0,012 m e desvio-padrão 0,114 m (intervalo −0,178 a +0,139 m) (fonte: cálculo sobre as Tabelas 1 e 3 de Lins, 2018, extrato 8). É compatível com MDT na referência altimétrica do IBGE, mas não prova qual datum nem qual geoide.
- Altitudes oficiais do SGB: Datum de Imbituba (NMM 1949–1957) e Santana; Pernambuco usa Imbituba (BDG, RN 393Y no Recife: "Datum Imbituba", altitude normal 2,2398 m, REALT 2018; extrato 5). O realizado oficial atual é a REALT 2018 (altitude normal). Modelo geoidal usual para converter altura elipsoidal de LiDAR: MAPGEO2015 (IBGE/USP, hoje sucedido por hgeoHNOR2020). **Que o PE3D tenha usado MAPGEO2015 (ou MAPGEO2010) é hipótese**, não achado.
- Pergunta em aberto 1: obter o relatório técnico do contrato PSHPE 001/2014 (consórcio Esteio/Engefoto/Aeroimagem/Fiducial/Lactec) ou os metadados no portal pe3d.pe.gov.br.

### Referência 2 — NR da DHN no Porto do Recife

- A tábua do Porto do Recife (DHN, carta 902, id `pe02`) é referida ao NR: "se forem previsões de maré, estão referenciados ao NR" (CHM, extrato 3). A TabuaMare repassa a tábua da DHN (`data_collection_institution":"DHN"`); **não achei declaração de que o campo `level` está no NR** além disso, mas é o padrão da tábua da DHN.
- `mean_level` = 1,28 m: interpretado como NMM local acima do NR (confirmar na F-41). O NR/NMM do Recife foi calculado pela DHN sobre série de 1978–1986 (extrato 7); não representa 2016 nem 2026.
- A F-41 da estação (que traz a altura da RN da DHN acima do NR, item D da Figura 4 do IBGE) **não foi obtida**: a página do CHM respondeu HTTP 403.

### O desnível NR ↔ datum: não foi fechado

Cadeia documentada pelo IBGE (extrato 1, Figura 4): zero do marégrafo → zero da régua → RN primária (altura acima do NR, vinda da DHN; altitude no SGB, vinda do BDG). O que existe publicamente:

1. Recife aparece na Figura 3 da nota técnica do IBGE (RN do marégrafo ligada à RAAP): NMM local ≈ **+0,14 m** acima do Datum de Imbituba no ajustamento em blocos; a interpolação visual do ajustamento global (1993) entre Salvador (+0,15 m) e Fortaleza (+0,55 m) dá ≈ +0,35 m (leitura gráfica, sem tabela; extratos 1 e 2). Sem valor numérico publicado para Recife.
2. NMM local acima do NR: 1,28 m (DHN, extrato 3).
3. Recife **não** é estação da RMPG (Imbituba, Macaé, Salvador, Fortaleza, Santana; extrato 1). Não há relatório de níveis de referência do IBGE para Recife nos moldes dos de Imbituba/Belém/Salvador.
4. Nenhum documento consultado dá a altitude (SGB) da RN do marégrafo do Recife nem a altura dela sobre o NR.

**Estimativa de trabalho (não aprovada, não usar em código):** a altitude do plano NR no SGB é h_NR = NMM_alt − 1,28 m. Com NMM_alt entre +0,14 e +0,35 m, h_NR fica entre **−1,14 m e −0,93 m**; ponto central −1,04 m. O erro de leitura do gráfico é ≈ ±0,03 m; sobre isso somam-se: NMM de série antiga (1978–1986) × nível médio atual [sem fonte para a tendência local]; incerteza vertical do MDT (especificação < 0,25 m; observado σ ≈ 0,11 m em Cabo); diferença altitude normal × ortométrica (centimétrica) [sem fonte]. Incerteza plausível de Δ: **±0,3 m**, ordem de grandeza que não substitui a medida.

**Condição (ressalva 1):** o ±0,3 m só vale se o MDT estiver no SGB (altitude normal/ortométrica). Se o PE3D tiver usado altura elipsoidal, o erro é de **metros** (a ondulação geoidal no Recife é de metros; valor a obter no MAPGEO2015 [sem fonte]). A evidência de Lins (2018) sustenta a hipótese do SGB, mas não a fecha: são 9 pontos em um município, e os pontos "H" do IBGE têm datum não declarado na tabela.

**Duas incertezas distintas (ressalva 2):**
- Incerteza de **Δ** (plano NR↔datum): fica presa à RN (altura sobre o NR e altitude no SGB) e à ligação entre elas. Não inclui o MDT ponto a ponto.
- Incerteza da **folga** (por ponto): Δ + σ de H do MDT (≈ 0,11 m ponto a ponto, observado em Cabo) + resíduo meteorológico + erro da previsão da tábua.
- A subida do nível do mar desde 1978–1986 enviesa a **altura prevista pela tábua** (o NMM de 1,28 m é antigo), não o plano NR, que é um plano fixo da DHN. Isso entra na folga, não em Δ.
- **Aviso:** a frente B1 mistura épocas (NMM do IBGE × 1,28 m da DHN de 1978–1986); por isso B1 é só consistência, não fecha Δ.

## Convenção de conversão

Seja H a cota ortométrica/normal do MDT (m, datum do SGB) e h_NR a altitude do plano NR nesse mesmo datum (negativa em Recife, porque o NR fica abaixo do NMM).

```
Δ = − h_NR                 (Δ > 0 em Recife; estimativa de trabalho Δ ≈ +1,04 ± 0,3 m, NÃO aprovada)
cota_NR = H + Δ            (cota de um ponto expressa na mesma régua da tábua)
H = altura_tábua + h_NR    (altura_tábua em m acima do NR → altitude no datum do MDT)
```

Exemplo numérico ilustrativo (com Δ = 1,04): rua a H = 2,00 m no MDT → cota_NR = 3,04 m; preamar 2,45 m da tábua não alaga; folga 0,59 m. Com Δ = 0,74 ou 1,34 (±0,3) a folga vai de 0,29 a 0,89 m, ou seja, a conclusão depende do desnível.

Sinal: somar Δ à cota do MDT converte para a régua do NR. Comparar sempre as duas pontas na mesma régua. Nunca subtrair Δ da altura de maré sem checar esta convenção.

## Opções consideradas

### A — Usar o desnível documentado
Pegar Δ de fonte primária (F-41 do Recife + altitude da RN na BDG/REALT 2018) e aplicar.
- A favor: rastreável, citável na dissertação, incerteza declarada pela fonte; resolve o problema de uma vez.
- Contra: **hoje não há fonte** (F-41 inacessível, sem relatório RMPG para Recife). Se a RN da DHN não estiver ligada ao SGB, a opção morre. Mesmo com a fonte, o NMM de 1978–1986 pode estar desatualizado, e é preciso conferir que o MDT está no mesmo datum.
- Custo: baixo se a DHN entregar a F-41 (e-mail ao CHM); médio se precisar de nivelamento novo.

### B — Procedimento empírico verificável
Fechar Δ com medidas, em três frentes independentes cujo acordo vira a incerteza:
1. **Pelo NMM**: Δ_NMM = 1,28 − NMM_alt, com NMM_alt do IBGE (Luz e Guimarães; solicitar o valor numérico de Recife à CGED/IBGE) ou da altitude do marégrafo atual. Compatível com a estimativa de trabalho acima.
2. **Por RN próxima**: pegar uma RN do IBGE (BDG; p.ex. RN 393Y, 2,2398 m, ou outra mais próxima do cais) e a RN da DHN da F-41; Δ = altura da RN acima do NR − altitude IBGE, se as duas forem a mesma RN ou estiverem ligadas por nivelamento. Fazer **também** a checagem do MDT: amostrar o MDT no pixel da RN (calçada) e comparar com a altitude do BDG para estimar o viés do MDT; o viés entra na incerteza.
3. **Por GNSS no marégrafo**: estações SONEL RECF/PERC (listadas no PSMSL, extrato 6) dão altura elipsoidal; converter com MAPGEO2015/hgeoHNOR2020 para altitude e ligar à régua pela F-41.
4. **Por linha d'água**: comparar a cota do MDT numa borda de cais/maré cheia (ortofoto/imagem de 2016 em preamar conhecida, com a tábua do dia) e em baixa-mar: a cota do MDT na lâmina de água em instante conhecido dá Δ por diferença com a altura da tábua nesse instante. Útil só como checagem grosseira (erro de maré, resíduo, voo em dias diferentes).
- A favor: fecha com dado verificável e dá incerteza; avança mesmo sem F-41.
- Contra: custa tempo; o método 4 tem erro de decímetros; depende de dados externos (BDG, CHM, voo).

### C — Só cotas relativas, sem comparação absoluta com a maré
Usar o MDT apenas para diferenças entre pontos (profundidade da depressão, declividade, área contribuinte, curva cota-volume) e a maré só como feature temporal (altura da tábua, defasagem, fase), sem converter.
- A favor: zero risco de erro vertical; **já é a regra da skill** `mapi-geo-pe3d` §2.4; permite continuar a Fase 3.
- Contra: impede calcular "nível lá fora − soleira/cota da rua", que é o mecanismo físico do refluxo; o modelo teria de aprender o efeito da maré só por correlação, o que perde explicabilidade e valor para a dissertação.

## Decisão

**Recomenda-se C agora e B em paralelo, com A se a F-41 trouxer a RN ligada ao SGB.** Até a aprovação de um Δ com incerteza: nenhuma regra, feature ou camada usa cota em valor absoluto contra a maré.

Passos, nesta ordem:
1. Pedir ao CHM/DHN a F-41 do Porto do Recife (RN-DHN, altura sobre o NR, data do nivelamento) e o nível médio do ano da tábua.
2. Pedir ao IBGE (CGED) a altitude da RN do marégrafo do Recife no REALT 2018 e o NMM local em relação ao Datum.
3. Obter o relatório técnico do PE3D (datum vertical e geoide) e comparar o MDT com a altitude da RN no pixel.
4. Registrar Δ ± incerteza em um novo ADR (ou emenda a este) com status aprovado; só então liberar as regras de refluxo.

## Consequências

Pode ser calculado já (referência relativa, sem Δ): `cota_rua_m` relativa entre pontos, profundidade da depressão, declividade, área contribuinte, curva cota-volume, diferença de cota entre rua e soleira se ambas vêm do MDT, e features temporais da maré (altura da tábua, fase, hora desde a preamar).

**Não pode ser calculado até Δ aprovado:** `nível lá fora − cota da rua`, `folga até a soleira` com nível lá fora vindo da tábua, qualquer limiar de "maré supera a cota da rua", e o recorte de pontos "sujeitos a refluxo por maré". **Soleira (ressalva 5):** a diferença rua−soleira só vale com soleira de fonte própria (levantamento de campo ou cadastro de drenagem). Não derivar soleira do MDT de 1 m (terreno nu, sem a galeria nem o canal). Sem essa fonte, a folga até a soleira fica indefinida. `soleira` e `cota da rua` seguem no datum do MDT; `outsideLevel` pelo rio (régua da ANA/APAC) tem referência própria, a ser tratada em ADR à parte.

O que muda no código quando aprovado: coluna de Δ e incerteza em tabela de parâmetros (não constante no código), e a conversão `cota_NR = H + Δ` num único ponto do pipeline. Antes: nada muda em código, banco ou compose.

Como medir: (i) concordância entre as frentes de B (alvo: diferença entre métodos < 0,15 m); (ii) a incerteza da folga entra como um **indicador de baixa confiança, separado do `riskLevel`**: marcar `baixaConfianca = true` quando |folga| < incerteza da folga. Não usar `UNKNOWN` para isso: no ADR 0007, UNKNOWN significa sem previsão válida.

### Como validar

Eventos de alagamento de **maré em dia sem chuva** no CKAN (chuva 24 h ≈ 0 na estação APAC/Cemaden mais próxima): para cada ponto crítico com cota H e evento registrado, o nível lá fora previsto, convertido com Δ, deve ter superado a soleira/cota da rua no horário do evento (preamar de sizígia). Contagem de acertos e de erros por valor de Δ ± incerteza; também o inverso (preamar de sizígia sem registro). **Circularidade (ressalva 4), mecanismo adotado:** Δ vem só de A/B e os eventos CKAN apenas **checam** esse Δ; não há varredura de Δ. Se alguma varredura for feita, vale partição fixa: os eventos usados em qualquer varredura de Δ nunca entram na avaliação do modelo (lista congelada por id antes de treinar). **Limite do teste:** eventos do CKAN com precisão DAY não têm horário; o teste usa a preamar de sizígia do dia, o que é indício fraco, não prova. [Quantidade de eventos disponíveis no CKAN: sem fonte; levantar antes.]

## Condições que reabririam a decisão

- A F-41 ou o IBGE entregar o desnível numérico (A passa a valer; reduz a incerteza).
- O relatório do PE3D mostrar datum diferente de Imbituba (p.ex. altura elipsoidal, ou geoide diferente).
- Novo MDT (obra, atualização do voo) ou nova tábua com NMM/NR atualizado pela DHN.
- Mudança do porto de referência da TabuaMare (hoje `pe02`, Porto do Recife; Suape `pe03` tem NMM 1,27 m e DHN/SUAPE como instituições diferentes).
- Evidência no CKAN de que o refluxo de maré ocorre com folga estimada acima da incerteza.

## Fontes

Ver `docs/extratos/referencia-vertical.md` (acessos em 2026-10-10): IBGE, relacao_dvsgb_nr_zh.pdf; IBGE, 10anos.pdf; TabuaMare/DHN `pe02`; IBGE BDG RN 393Y; PSMSL estação 556; Fuchs et al. (2021), IHR; Lins (2018), UFPE; Cirilo et al., ABRHidro.
