# Regra da área de contribuição: diagnóstico e proposta

- Data: 2026-11-21 · Prompt 2, Parte B · Status: **proposta, nada implementado no alerta**
- Para: Lucas (e orientador). Decisão pendente no ADR 0027 ("Condições que reabririam a decisão").
- Regra seguida: nenhum dia de alagamento foi aberto. As regras são defendidas só pela física e pelo terreno.
- Números: `docs/terreno/area-contribuicao/area_regras.csv` (gerado por `tools/terreno/run.sh area_regras`, mesma janela de
  2 km e mesmo motor WhiteboxTools v2.4.0 da etapa `atributos`, mosaico com as folhas de Jaboatão, VRT `100e44ae…`).
  Mapas: `docs/terreno/area-contribuicao/<PONTO>.png`. Vias: OpenStreetMap via Overpass, baixado em 2026-11-21
  (`tools/terreno/vias_osm/`, licença ODbL). Bocas de lobo: ESIG/EMLURB (prompt 1, `docs/extratos/esig-drenagem/`).

## 1. O problema em uma linha

A área contribuinte é a acumulação D8 do pixel de maior acumulação a até 10 m do ponto (TERRENO-PE3D §5.1). Em dois pontos,
mudar esse raio de 5 para 20 m multiplica a área por centenas, e a área pequena faz a lâmina quase não subir.

| Ponto | snap 0 m | 5 m | **10 m (atual)** | 20 m | Razão 20 m / 5 m |
|---|---|---|---|---|---|
| AV_RECIFE_IBURA | 4 | 28 | **208** | 10.880 | 388,6 |
| CIN_UFPE | 1 | 82.297 | **82.377** | 82.487 | 1,0 |
| AGAMENON_DERBY | 1 | 26 | **62** | 14.875 | 572,1 |
| JABOATAO_CENTRO | 12 | 145 | **148** | 161 | 1,1 |
| MASCARENHAS_IMBIRIBEIRA | 23 | 9.616 | **10.103** | 12.364 | 1,3 |

Áreas em m², contadas pelos pixels da bacia D8 de cada exutório. Diferem de `ponto_terreno.csv` em até 4 m² (lá a área sai do
valor de acumulação do exutório; a diferença vem de células de borda e não muda nenhuma conclusão).

## 2. Diagnóstico por ponto

Legenda dos mapas (400 m × 400 m, 2 px por metro): relevo sombreado; vias do OSM em branco; contorno da bacia D8 por regra
(snap 0 m vermelho, 5 m laranja, 10 m azul, 20 m verde, regra b magenta, regra c ciano; as de 0 e 5 m estão pintadas por
dentro porque são minúsculas); exutório em quadrado da mesma cor; ponto em cruz amarela; boca de lobo do ESIG em quadrado preto.
Quando duas regras dão a mesma bacia, aparece só a cor desenhada por último (ciano cobre o verde no Ibura, no Derby e na Imbiribeira).

- **Ibura:** o ponto fica no cruzamento da Av. Recife, mas nenhum eixo de drenagem do MDT passa a menos de 10 m dele:
  com 10 m o snap pega uma cabeceira de 208 m² a 9,1 m do ponto, e só com 20 m alcança o eixo que drena a área a oeste (10.880 m²).
- **CIn:** o ponto está sobre um eixo de drenagem do MDT; de 5 a 20 m o exutório anda pela mesma linha e a área quase não muda.
- **Derby:** o ponto **não está sobre a Av. Agamenon Magalhães**: fica num miolo de quadra a cerca de 100 m da avenida (mapa) e
  a 54,5 m do elemento de drenagem mais próximo do ESIG (prompt 1); ali o MDT não tem eixo de drenagem até 20 m.
- **Jaboatão:** o ponto está numa encosta a 64,8 m, sem depressão e sem via a 20 m (TERRENO-PE3D §3.1); a área é estável
  (145–161 m²) porque é a de um filete de encosta, não a de um ponto de acúmulo.
- **Imbiribeira:** o exutório de 5 a 20 m fica junto à Av. Mascarenhas de Morais e a área é estável, mas a bacia ao sul
  atravessa uma superfície lisa em triângulos (interpolação do MDT, provavelmente área sem retorno do LiDAR); a área existe,
  a forma dela é incerta.

Causa comum no Ibura e no Derby: o MDT de 1 m é terreno nu. Ele não vê bueiro, galeria, meio-fio rebaixado nem edificação, então
um ponto marcado à mão na rua pode cair fora de qualquer eixo de drenagem do MDT. No Derby e em Jaboatão o problema é anterior à
regra: **a posição do ponto não marca a rua que alaga** (achado novo deste trabalho).

## 3. Regras candidatas

Ilustração usada nas colunas "lâmina" abaixo (não é rodada sobre série nenhuma, nem sobre dia de alagamento): um passo de 10 min
com 20,9 mm (IDF de Silva e Araújo, TR 5, Te 10 min; PARAMETROS-MODELO-FISICO) e **galeria totalmente obstruída** (f = 0):
V = C·P·A com C = 0,86, lâmina = altura na curva cota-volume da própria bacia menos (cota da rua − fundo). Mostra só o efeito da
área. Com a galeria livre, a lâmina seria 0 em todos os casos, porque entrada e saída usam o mesmo C e a mesma área (prompt 1).

| Ponto | (a) razão 20/5 m | (b) BL do ESIG | (c) via do OSM | (d) faixa 5–20 m | Lâmina ilustrativa (mm): 5 m / 10 m / 20 m / (b) / (c) |
|---|---|---|---|---|---|
| Ibura | 388,6 → **SEM_DADO** | 68 m² (BL 3603, a 32 m do ponto baixo) | 10.879 m² | 28 – 10.880 | 16 / 123 / 0 / 94 / 0 |
| CIn | 1,0 → mantém 82.377 | sem dado (BL a 105 m) | 82.487 m² | 82.297 – 82.487 | 625 / 620 / 613 / — / 613 |
| Derby | 572,1 → **SEM_DADO** | sem dado (BL a 85 m) | 14.875 m² | 26 – 14.875 | 0 / 20 / 0 / — / 0 |
| Jaboatão | 1,1 → mantém 148 | sem dado (sem cadastro) | sem dado (sem via a 20 m) | 145 – 161 | 0 / 0 / 0 / — / — |
| Imbiribeira | 1,3 → mantém 10.103 | sem dado (0 BL a 300 m) | 12.364 m² | 9.616 – 12.364 | 326 / 316 / 263 / — / 263 |

A lâmina não cresce sempre com a área: quando a bacia maior inclui um fundo mais baixo, longe do ponto (Ibura e Derby com 20 m),
a água se acumula lá e não chega à cota da rua no ponto. Isso reforça que, nesses dois pontos, o MDT não descreve o acúmulo na rua.

### (a) Manter 10 m e marcar como SEM_DADO o ponto instável

- Regra: se A(20 m) / A(5 m) > N, o ponto fica `SEM_DADO (TERRENO_EM_REVISAO)`. Proposta: **N = 10**.
- Por que 10: num eixo de drenagem definido, aumentar o raio de 5 para 20 m só acrescenta o trecho de 15 m da mesma linha de fluxo
  e as encostas laterais dele; a área cresce algumas vezes, não uma ordem de grandeza. Crescer mais de 10 vezes significa que o
  snap pulou para outro eixo (outra bacia). Os valores observados ficam em dois grupos (1,0 a 1,3 e 389 a 572), então qualquer N
  entre 2 e 380 dá a mesma classificação: o resultado não depende do valor exato de N.
- A favor: regra única e igual para os 5 pontos; não inventa área; segue a regra do projeto (ponto sem terreno confiável fica `SEM_DADO`).
- Contra: tira o Ibura do alerta (hoje OK) além do Derby; não resolve nada, só marca. Não detecta Jaboatão (estável, mas em encosta):
  precisa de um segundo critério, por exemplo profundidade de depressão = 0 e nenhuma via a 20 m.
- Aplicação: **igual nos 5 pontos**.

### (b) Exutório na boca de lobo do ESIG mais próxima do ponto baixo

- Regra: ponto baixo = pixel de menor cota a até 20 m do ponto; BL = a mais próxima do ponto baixo a até 50 m; exutório = pixel de
  maior acumulação a até 3 m da BL; área = bacia D8 até ele.
- A favor: fisicamente é o lugar para onde a água da rua vai, e liga a área à capacidade de entrada do prompt 1.
- Contra: só funciona no Ibura (68 m², ainda menor que os 208 m² de hoje). Derby e CIn têm BL a 85 e 105 m, Imbiribeira não tem BL
  cadastrada e Jaboatão não tem cadastro. A BL fica no meio-fio, e o D8 de 1 m nem sempre passa pelo pixel dela. Depende de um
  cadastro de 2018–2024 sem verificação de campo.
- Aplicação: igual na regra, mas **na prática só se aplica a 1 dos 5 pontos**. Não recomendo como regra principal agora.

### (c) Área do trecho de via que drena para o ponto

- Regra: exutório = pixel de maior acumulação a até 20 m do ponto, **só entre pixels a até 6 m do eixo de uma via de veículo do OSM**
  (meia-largura de uma avenida de 3 faixas; premissa declarada). A água que alaga a rua chega pela própria rua.
- A favor: regra única e física; nos pontos que estão de fato na rua (Ibura, Imbiribeira, CIn) dá a bacia que a via recebe; não
  depende do cadastro.
- Contra: no Ibura e no Derby ela coincide com o snap de 20 m (10.879 e 14.875 m²), então é, na prática, "usar 20 m quando há
  via". No Derby a via mais próxima é uma via de serviço do OSM, não a Agamenon. O OSM não é levantamento oficial e muda com o
  tempo (precisa de cópia fixa, já salva com hash). Não resolve Jaboatão (sem via a 20 m).
- Aplicação: **igual nos 5 pontos**, com `SEM_DADO` quando não houver via a 20 m.

### (d) Carregar a incerteza como faixa

- Regra: rodar o balde com A(5 m) e A(20 m) e mostrar a lâmina como faixa; o nível do alerta usa o limite inferior (ou o superior,
  a decidir antes de rodar).
- A favor: não escolhe valor; mostra a incerteza que existe.
- Contra: no Ibura e no Derby a faixa vai de dezenas a dezenas de milhares de m², e a lâmina ilustrativa vai de 0 a 123 mm sem ordem
  (mais área nem sempre dá mais lâmina, ver acima). A faixa fica tão larga que não informa. Dobra o custo do balde no ciclo.
- Aplicação: **igual nos 5 pontos**. Serve como complemento de (a) ou (c), não sozinha.

## 4. Recomendação (minha leitura, para você e o orientador decidirem)

1. **Antes da regra, conferir a posição dos pontos Derby e Jaboatão.** É informação que só você tem (onde exatamente alaga). Sem
   isso, nenhuma regra de área é defensável nesses dois pontos. Enquanto isso, os dois ficam `SEM_DADO` (já estão em
   `terrain-review`).
2. **Adotar (a) com N = 10 já**, como trava: ela é única, não olha rótulo e pega os dois pontos instáveis. Consequência declarada: o
   Ibura sai do alerta até a regra definitiva.
3. **Avaliar (c) como regra definitiva** depois da conferência das posições, com (d) para registrar a faixa 5–20 m no relatório.
4. Deixar (b) para quando o cadastro da EMLURB vier completo (pedido 06) e as BL forem conferidas em campo (prompt 3).

Nenhuma dessas regras foi escolhida olhando resultado de alerta, F1 ou dia de alagamento. Se você aprovar, a regra escolhida entra
no código, roda sobre os 5 pontos e só então vai para a reprise.

## 5. Emenda ao ADR 0027

Rascunho com status **proposto** em `docs/adr/0027-balde-a-10-min-e-posicao-fora-de-modelo-aprendido.md`, seção
"Emenda proposta A — regra da área de contribuição".
