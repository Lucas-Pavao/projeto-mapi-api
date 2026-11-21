# Cadastro de drenagem do ESIG (EMLURB): o que vem preenchido

Prompt 1, tarefa 1. Só inspeção: nenhuma capacidade foi calculada. Nenhum dado de alagamento foi aberto.

- Serviços (acesso em **2026-11-21**):
  - microdrenagem: https://esigportal2.recife.pe.gov.br/arcgis/rest/services/Emlurb/BASES_DRENAGEM_RECIFE_MI/MapServer
  - macrodrenagem: https://esigportal2.recife.pe.gov.br/arcgis/rest/services/Emlurb/BASES_DRENAGEM_RECIFE_MA/MapServer
- Descrição do serviço: "B.D.E. ... da gestão e manejo de águas pluviais e drenagem urbana do Recife. Contrato Número 6016/2012"; direitos: "ABF Engenharia, Serviços e Comércio Ltda" (`metadados/servico_MI.json`). ArcGIS Server 11.5, EPSG:31985, até 8.000 registros por consulta (MI) e 2.000 (MA).
- Arquivos: `metadados/*.json` (hash em `metadados/SHA256SUMS`); `geojson/*.geojson` (hash, URL e hora em `geojson/MANIFESTO.csv`); `preenchimento_por_campo.csv` (todos os campos, por ponto e camada); `resumo_por_ponto.csv`.
- Reproduzir: `python3 -I scripts/capacidade-galeria/baixa_esig.py` (rede) e `python3 -I scripts/capacidade-galeria/relatorio_preenchimento.py` (sem rede).
- Consulta: buffer de 300 m em volta do ponto (`distance=300`, metros), `outFields=*`, `outSR=31985`, `f=geojson`. Nenhuma consulta passou do limite: todas vieram em 1 página.

## 1. O que muda em relação ao que se esperava

1. **A camada 1 "Rede" não tem diâmetro, cota nem declividade.** Os únicos atributos são `REDE_TIPO` ("Tubulação" em 100%), comprimento (`VLCOMPRIM`, m) e datas de edição (todas 2026-10-26). As linhas não dizem de qual nó saem nem para qual vão. O diâmetro só existe nos nós (camada 0, `VLTBMONT*` e `VLTBJUSAN*`), e a ligação trecho-nó tem que ser feita pela geometria: 94% das pontas das linhas caem a até 0,5 m de um nó (Ibura 599 de 638, Derby 417 de 472, CIn 71 de 76, Imbiribeira 92 de 106).
2. **As cotas de montante e jusante estão vazias.** Na cidade inteira: `COTA_MONTANTE_01` preenchida em 51 de 175.631 elementos e `COTA_JUSANTE` em 32. `COTA_FUNDO` em 4.025. Nos 4 pontos: 0 registros com cota de montante ou jusante.
3. **Há uma alternativa para a cota de fundo:** `Z − VLHFUNDO`. Nos 38 elementos que têm os três campos (Ibura e Derby), `COTA_FUNDO = Z − VLHFUNDO` (diferença < 5 mm) em 27, com diferença de 0,05 a 0,5 m em 10 e um valor absurdo (`COTA_FUNDO = −93,601` m, OBJECTID 3352, Ibura). Ou seja, `Z` parece ser a cota da tampa e `VLHFUNDO` a profundidade até o fundo, em metros. Isso é inferência, não dicionário de dados.
4. **O levantamento não é de 2012.** O contrato é de 2012, mas `DATA_LEVANTAMENTO` vai de 2018-02-21 a 2024-02-20 nos pontos; parte dos registros traz 1899-12-30, que é o "zero" de data do Excel e foi contado como vazio.
5. **A cobertura muda muito de ponto a ponto** (tabela abaixo). CIn e Imbiribeira quase não têm cadastro hidráulico; a Imbiribeira não tem nenhuma boca de lobo cadastrada a 300 m.

## 2. Resumo por ponto (buffer de 300 m)

Fonte: `resumo_por_ponto.csv`. BL = boca de lobo, PV = poço de visita, CG = caixa com gaveta, CAN = canaleta.

| Ponto | Elementos | BL (≤50 / ≤100 / ≤300 m) | PV | CG | Com tubo de jusante | Com profundidade (`VLHFUNDO`) | Elemento mais próximo | BL mais próxima | Levantamento |
|---|---|---|---|---|---|---|---|---|---|
| AV_RECIFE_IBURA | 317 | 3 / 8 / 65 | 43 | 202 | 70% | 79% | PV a 8,4 m | 17,0 m | 2018-06 a 2020-03 |
| CIN_UFPE | 37 | 0 / 0 / 1 | 10 | 26 | 0% | 0% | CG a 22,1 m | 120,2 m | sem data |
| AGAMENON_DERBY | 224 | 0 / 3 / 46 | 83 | 95 | 41% | 44% | CG a 54,5 m | 71,1 m | 2018-02 a 2024-02 |
| MASCARENHAS_IMBIRIBEIRA | 51 | 0 / 0 / 0 | 0 | 51 | 29% | 51% | CG a 87,3 m | nenhuma | 2018-07 |

Cidade inteira (consultas `returnCountOnly` e estatística por `TIPO`, 2026-11-21): 175.631 elementos; BL 15.884, PV 16.790, CG 44.164, CAN 61.791, FC 37.002. Com tubo de jusante: 17.162 (9,8%); com profundidade: 18.746 (10,7%); BL com tubo de jusante: 2.793 de 15.884. A ordem de grandeza bate com o PMSB/PDDR (15.566 BL, 16.161 PV, cerca de 20% da rede com cadastro completo; `PARAMETROS-MODELO-FISICO.md`, item 12 e [E5]).

## 3. Preenchimento por campo, camada 0 (% sem dado = nulo, zero ou data 1899)

| Campo | Ibura | CIn | Derby | Imbiribeira | Observação |
|---|---|---|---|---|---|
| `TIPO` | 0% | 0% | 0% | 0% | valores: BL, PV, CG, CAN (FC não aparece nos 4 pontos) |
| `Z` | 2% | 0% | 0% | 0% | 0,80–2,86 m (Ibura), 7,56–8,89 (CIn), 0,73–4,77 (Derby), 1,70–3,56 (Imbiribeira) |
| `VLHFUNDO` | 21% | 100% | 56% | 49% | 0,17–2,50 m |
| `COTA_FUNDO` | 91% | 100% | 95% | 100% | um valor implausível (−93,6 m) no Ibura |
| `COTA_MONTANTE_01..03`, `COTA_JUSANTE` | 100% | 100% | 100% | 100% | vazios |
| `VLTBJUSAN1` | 30% | 100% | 59% | 71% | valores distintos abaixo |
| `VLTBJUSAN2` | 89% | 100% | 99% | 100% | |
| `VLTBMONT1` | 54% | 100% | 71% | 100% | um zero no Ibura e um no Derby (`VLTBMONT2`) |
| `QTD_CONEXÕES` | 17% | 100% | 56% | 47% | 1 a 4 |
| `DATA_LEVANTAMENTO` | 18% | 100% | 60% | 47% | |
| `LOGRADOURO_OFICIAL` | 98% | 100% | 100% | 100% | |
| `TIPO_TAMPA`, `ESTADO_CONSERVAÇÃO_TAMPA`, `TIPO_CALÇADA`, `TIPO_MEIO_FIO`, `CÓDIGO`, `COD_MICROBACIA` | 100% | 100% | 100% | 100% | `COD_MICROBACIA` vazio na cidade inteira (0 registros) |

## 4. Unidade dos tubos (`VLTBMONT*`, `VLTBJUSAN*`)

**Metros (diâmetro nominal), com confiança alta, sem dicionário de dados.**

- Os metadados só dizem "Tubo Montante 1" e "Tubo Jusante 1", sem unidade. Não há dicionário de dados publicado no serviço (`description` vazio em todas as camadas).
- Valores distintos nos 4 pontos: 0,1; 0,2; 0,3; 0,4; 0,5; 0,6; 0,8; 1,0; 1,4. Mais frequentes: 0,6 (Ibura, 102 de 223) e 0,4 (Derby, 52 de 91). São os diâmetros comerciais de tubo de concreto em metros (DN 300, 400, 600, 800, 1000 mm). Em centímetros daria tubo de 6 mm; em milímetros, menos de 1 mm.
- 0,1 m (um registro, Ibura) é menor que o usual em galeria pluvial: marcar como suspeito no cálculo.
- `VLHFUNDO` também está em metros: fecha com `Z − COTA_FUNDO` (seção 1, item 3).
- Não se sabe se o tubo é circular. Seções retangulares (galeria celular) não têm campo próprio.

## 5. Macrodrenagem (serviço MA)

Canal que recebe cada ponto, pela consulta de 300 m (camadas 1 e 2) e bacia (camada 4):

| Ponto | Canal (camada 2) | Largura média / profundidade média (m) | Declividade (m/m) | Vazão Q25 (m³/s) | Bacia |
|---|---|---|---|---|---|
| Ibura | Canal Cabeceira do Aeroporto / Av. Recife; Canal da Vila Arq. Maria Lúcia Barbosa | 1,65 e 3,33 / 1,6 e 2,8 | 0,00183 e 0,01122 | 2,55 e 10 | Rio Tejipió |
| CIn | Canal do Cavouco | 5,3 / 2,0 | 0,00156 | 25 | Capibaribe e Tejipió (limite) |
| Derby | Canal Derby–Tacaruna | 13,44 / 2,88 | −0,0001 | vazio | Capibaribe |
| Imbiribeira | Canal do Jordão | 10,64 / 1,82 | −0,00268 | 68 | Tejipió |

Declividade negativa (Derby, Jordão) indica canal de maré ou sentido de digitalização trocado; não usar sem conferir.

## 6. O que isso significa para a tarefa 2 (sem decidir nada ainda)

- **Ibura:** dá para tentar Manning. Há diâmetro em 70% dos nós e profundidade em 79%; a declividade sairia de `(Z − VLHFUNDO)` nas duas pontas do trecho, ligadas pela geometria. As cotas de montante/jusante do cadastro não existem.
- **Derby:** possível com lacunas (41% com tubo). O elemento mais próximo está a 54,5 m do ponto, o que já é uma questão sobre a posição do ponto ou do cadastro.
- **CIn:** sem nenhum campo hidráulico. Capacidade fica "sem dado (cadastro sem diâmetro e sem profundidade)".
- **Imbiribeira:** só caixas com gaveta, tubo de 0,3 m em 15 delas, nenhuma boca de lobo. A capacidade de entrada por boca de lobo fica "sem dado (nenhuma BL cadastrada a 300 m)". É provável que o cadastro esteja incompleto ali, não que não haja boca de lobo; isso tem que constar.
- A capacidade de entrada (número de BL) é viável no Ibura e no Derby. As BL quase nunca têm tamanho ou tipo de grelha: só o `TIPO = BL`, então a capacidade por BL terá de vir de um tipo-padrão do manual, declarado.

Os campos vazios estão listados no pedido LAI 06 (`docs/pedidos/06-lai-emlurb-cadastro-microdrenagem.md`).
