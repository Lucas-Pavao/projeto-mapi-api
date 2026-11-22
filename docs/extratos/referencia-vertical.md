# Extratos — referência vertical (MDT do PE3D × tábua de maré)

Tarefa F3-GEO-03. Acesso a todas as fontes em 2026-10-10. Citações curtas, literais, com URL.
Usadas no ADR `docs/adr/0009-referencia-vertical-mdt-mare.md`.

## 1. IBGE — relação entre o Datum Vertical do SGB e o NR (nota técnica da CGED, 11/02/2009)

URL: https://geoftp.ibge.gov.br/metodos_e_outros_documentos_de_referencia/outros_documentos_tecnicos/rmpg/relacao_dvsgb_nr_zh.pdf

- "As altitudes do SGB têm origem em dois valores particulares do nível médio do mar (NMM), denominados Datum de Imbituba e Datum de Santana." (seção 1)
- Imbituba: observações de 1949 a 1957 (seção 1).
- "Na TM [Tábua de Marés], são divulgados os horários e alturas das preamares e baixamares [...] medidas a partir do Nível de Referência (NR), isto é, um nível mínimo definido localmente de forma que não haja valores negativos de altura da maré." (seção 2)
- "O nível do mar local é um elemento crítico [...] mas muitas vezes sua relação com os data verticais do SGB é desconhecida." (seção 1)
- Figura 3 (seção 3): diferença entre altitudes das RRNN do SGB e as alturas em relação aos NMM locais, por marégrafo, incluindo **Recife**. Leitura do gráfico (sem tabela numérica): Recife ≈ +0,14 m no ajustamento em blocos (linha sólida, ~3 000 km nivelados desde Imbituba); o ajustamento global (1993, linha tracejada) não tem marcador em Recife e passa por ≈ +0,15 m (Salvador) e ≈ +0,55 m (Fortaleza). A leitura é visual: incerteza de leitura ≈ ±0,03 m, fora a incerteza do próprio dado.
- RMPG: "Suas estações maregráficas estão instaladas em Imbituba (SC), Macaé (RJ), Salvador (BA), Fortaleza (CE) e Santana (AP)". **Recife não é estação da RMPG.** (seção 4)
- Figura 4 (seção 5), cadeia de transferência de nível: "D. Altura da RN primária acima do Nível de Redução da DHN ("NR")"; "E. Altitude da RN primária referente a Imbituba (ou Santana), obtida no BDG do IBGE"; "H. Constante a ser subtraída das leituras do marégrafo para referenciá-las ao NR"; "K. Constante a ser subtraída das leituras do marégrafo para referenciá-las a Imbituba". Ou seja, o desnível NR↔Imbituba = D − E (com sinais conforme a convenção), e exige as duas pontas na mesma RN.

## 2. IBGE — Dez anos de monitoramento do nível do mar (Luz e Guimarães, 2003)

URL: https://geoftp.ibge.gov.br/metodos_e_outros_documentos_de_referencia/outros_documentos_tecnicos/rmpg/10anos.pdf

- "[...] o nível médio do mar em Salvador estaria cerca de 1 cm acima do Datum, enquanto que em Fortaleza essa diferença chegaria a 30 cm. Mais recentemente, o reajustamento global da RAAP [...] alterou tais valores para 15 cm e 55 cm, respectivamente."
- Isso confirma a leitura da Figura 3 acima (Salvador ≈ 0,15 m e Fortaleza ≈ 0,55 m no ajustamento global) e mostra que o NMM local fica acima do Datum de Imbituba no Nordeste, crescendo para o norte. Valor numérico para Recife: **não publicado neste texto**.

## 3. DHN via TabuaMare — Porto do Recife

URL: `https://tabuamare.api.br/api/v2/harbors/%5Bpe02%5D` e `https://tabuamare.api.br/api/v2/tabua-mare/pe02/10/10` (consultados 2026-10-10; a API é a fonte que o sistema usa, `AppProperties.TabuaMare.apiUrl`).

Resposta (campos relevantes): `"id":"pe02"`, `"harbor_name":"PORTO DO RECIFE (ESTADO DE PERNAMBUCO)"`, `"card":"902"`, `"data_collection_institution":"DHN"`, `"mean_level":1.28`, `geo_location` lat −8,0567 lng −34,8667, `"timezone":"UTC -03.0"`.
Leitura: `mean_level` = 1,28 m é o nível médio do mar local **acima do NR** da DHN (mesma ordem do valor de Cabedelo, 1,18 m, citado em INPH 002/2021 com fonte FEMAR; o campo não vem rotulado na API, a interpretação é do autor e precisa ser confirmada na F-41).
Exemplo de dia (2026-10-10): preamar 2,45 m às 03:23, baixa-mar 0,20 m às 09:38; ou seja, as alturas da tábua são positivas e referidas ao NR.

Confirmação de que a referência da tábua é o NR: FAQ do CHM, "se forem previsões de maré, estão referenciados ao NR"; as observações vêm referidas ao zero da régua. URL: https://www.marinha.mil.br/chm/bndo/duvidas-frequentes (conteúdo conforme resumo da busca; a página devolveu HTTP 403 ao acesso direto).

## 4. DHN — fichas F-41 e RN da estação

- Página das estações: https://www.marinha.mil.br/chm/estacoes-maregraficas (HTTP 403 em 2026-10-10, por curl e WebFetch).
- Modelo de F-41 (Imbituba, versão 2/2021): https://assets.marinha.mil.br/chm/sites/www.marinha.mil.br.chm/files/dados_de_mare/60250_-_porto_de_imbituba_f-41_tabuas_padrao_v2-2021.pdf (HTTP 000 / indisponível no acesso feito).
- **A F-41 do Porto do Recife não foi obtida.** É nela que está a altura da RN da DHN acima do NR (item D da Figura 4 do IBGE).

## 5. IBGE — BDG, RN em Recife

URL: http://www.bdg.ibge.gov.br/bdg/pdf/relatorio.asp?L1=393Y (relatório gerado em 2026-10-10).

- RN 393Y, Recife: "Altitude Normal(m) 2,2398", fonte "Nivelamento Geométrico", "Datum Imbituba", cálculo em 30/07/2018 (REALT 2018, 2ª edição: https://biblioteca.ibge.gov.br/visualizacao/livros/liv101666.pdf). UTM E 290 186, N 9 108 734. Localização: calçada do Clube Internacional do Recife, Rua Benfica 505, Madalena. **Não está no marégrafo do porto**; serve apenas como prova de que o BDG publica altitudes de RRNN no Recife referidas a Imbituba.
- RN 9320A (Várzea), altitude normal 6,9921 m, Imbituba (resultado de busca; relatório http://www.bdg.ibge.gov.br/bdg/pdf/relatorio.asp?L1=9320A não aberto por inteiro).
- Altitude do IBGE é **normal** (Imbituba), não ortométrica estrita; a diferença é de ordem centimétrica em cotas baixas [sem fonte, estimativa teórica; verificar].

## 6. PSMSL — marégrafo de Recife (estação 556)

URLs: https://psmsl.org/data/obtaining/stations/556.php e https://psmsl.org/data/obtaining/rlr.diagrams/556.php

- "Add 5.546m to all data values to refer to RLR"; "RLR is 9.700m below primary benchmark BM 1". Série 1948–1968, estação histórica.
- Referência interna do PSMSL (RLR), **não** é o NR da DHN nem o datum do SGB. Só serve para indicar que existiu um BM 1 do marégrafo antigo; a altitude de BM 1 no SGB não consta na página.
- Estações GNSS próximas listadas pelo PSMSL (SONEL): PERC e RECF.

## 7. Dados de DHN usados no cálculo do NR do Recife

Fuchs, Galvão Teixeira e Nzualo (2021), International Hydrographic Review, nov/2021: https://journals.lib.unb.ca/index.php/ihr/article/view/33117
- "For the Recife Port, the data set had a sampling rate of 1 hour, starting on November 9, 1978, [...] and ending on October 31, 1986".
- O NMM e o NR do Recife foram calculados sobre série antiga (1978–1986); a Tabela 3 (Z0 e NMM em cm) é imagem no PDF e não foi extraída.
- Consequência: o 1,28 m reflete o NMM da época, não o de 2016 ou de 2026.

## 8. PE3D — datum vertical

Nenhuma fonte primária encontrada que declare datum vertical ou modelo geoidal do MDT.

- Cirilo et al., "Pernambuco Tridimensional" (ABRHidro): MDT/MDE "obtidos por meio de perfilamento laser com erro altimétrico melhor que 25cm" (1:5 000); cidades em 1:1 000 com erro "inferior a 10cm". URL: https://files.abrhidro.org.br/Eventos/Trabalhos/156/PAP020211.pdf. O texto não cita datum nem geoide.
- SRHS-PE, página do PE3D (consórcio Esteio, Engefoto, Aeroimagem, Fiducial, Lactec; contrato PSHPE 001/2014): https://www.srhs.pe.gov.br/programas-acoes/pe3d. Sem datum vertical.
- Lins (2018), UFPE, controle de qualidade do MDT do PE3D em Cabo de Santo Agostinho: https://www.ufpe.br/documents/39451/1217366/THYAGO+DE+SOUSA+LINS.pdf/cc968b7f-b1f4-44d7-a153-679604d78552 — compara o MDT com 29 pontos GNSS do IBGE (coluna "H", sem declarar o datum). Em 9 pares (Tabelas 1 e 3, mesmas coordenadas), MDT − GNSS: média +0,012 m, desvio-padrão 0,114 m, intervalo −0,178 a +0,139 m (cálculo do autor deste ADR). Isso é compatível com MDT na mesma referência vertical dos pontos do IBGE ao nível de decímetros, mas **não prova** o datum nem o geoide.
- Contexto geoide no Recife: Calado, UFPE, avaliação de EGM2008 e MAPGEO2015 no Recife: https://repositorio.ufpe.br/jspui/bitstream/123456789/47360/1/LUCAS%20GONZALES%20LIMA%20PEREIRA%20CALADO-AVALIA%c3%87%c3%83O%20DA%20ACUR%c3%81CIA%20DOS%20MODELOS%20GEOIDAIS%20EGM2008%20E%20MAPGEO2015%20PARA%20A%20CIDADE%20DO%20RECIFE_PE.pdf (não lido por inteiro).
- IBGE, modelos geoidais (MAPGEO2015; sucessor hgeoHNOR2020): https://www.ibge.gov.br/geociencias/informacoes-sobre-posicionamento-geodesico/servicos-para-posicionamento-geodesico/10855-modelo-de-ondulacao-geoidal.html
- Portal de distribuição: www.pe3d.pe.gov.br (cadastro necessário; metadados do GeoTIFF e relatório técnico do contrato PSHPE 001/2014 não consultados). O GeoTIFF do repositório nem tem CRS (ver skill mapi-geo-pe3d).

## 9. Novas RRNN do porto, viés MDT↔RN e escala do PE3D (2026-11-21, prompt 4)

> O refluxo de maré **voltou ao escopo** em 2026-11-21 por decisão do Lucas (ADR 0009, emenda 3). Esta seção só registra
> fontes e confere números; Δ = +0,821 m (emenda 1) **não mudou**.

**RRNN acrescentadas** a `scripts/referencia-vertical/rns_ibge_recife.csv` (relatórios do BDG gerados em 2026-11-21 em
`http://www.bdg.ibge.gov.br/bdg/pdf/relatorio.asp?L1=<RN>`; cópias e SHA-256 completos em `MAPI/fontes/bdg-2026-11-21/SHA256SUMS`; o
relatório traz a data de geração, então um novo download muda o hash). Datum Imbituba, cálculo REALT de 30/07/2018. Coordenadas
convertidas para UTM 25S (`scripts/capacidade-galeria/utm.py`).

| RN | Altitude normal (m) | Sigma (m) | Marco (visita) | Nivelamento | Local (BDG) | SHA-256 (início) |
|---|---|---|---|---|---|---|
| 9319P | 3,0346 | sem ("simples transporte") | NÃO ENCONTRADO (05/04/2006) | 16/08/1958 | quebra-mar, 90 m ao sul do marégrafo; chapa "IAGS M6" | `c7aee3404c2df65e` |
| 9319R | 2,9762 | sem | NÃO ENCONTRADO (05/04/2006) | 20/08/1958 | porta do Farol do Picão, no quebra-mar; chapa "IAGS M3" | `b977879adf758c37` |
| 9334M | 3,3229 | 0,089 | BOM (25/03/2009) | 09/07/1987 | marco da Portobrás; chapa "R.N.1 Portobrás"; = EG 8131552 | `d4a2f2521b04bc41` |
| 9334N | 2,8382 | 0,089 | BOM (25/03/2009) | 31/07/1987 | junto ao cabeço 72, Cais do Porto; = EG 8131556 | `07dc5618f7ece592` |
| 394A | 2,8939 | sem | NÃO ENCONTRADO (05/04/2006) | 14/08/1958 | lado oeste do quebra-mar | `2ae9179736694a36` |

Conferência das que já estavam no CSV: 2603D = 2,8221 m (`cb9e77dbb6fabf2c`), 2603E = 2,7741 m (`912162d4957d1a7f`) e 2603J = 2,7572 m
(`6a502ca5c28736e3`) batem com o CSV. **A 8131557 é a 2603J** ("RN 2603-J = EG 8131557", relatório do BDG).

**Viés MDT − RN** (`scripts/referencia-vertical/vies_mdt_rn_ibge.py`, mesmo método; mosaico com as folhas de Jaboatão, que não
alteram os pixels do Recife; saída em `saida_vies_mdt_2026-11-21.txt`):

| Conjunto | Antes (emenda 1): n / mediana / dp | Agora: n / mediana / dp |
|---|---|---|
| Terra, todos | 14 / −0,356 / 0,563 m | **17 / −0,369 / 0,517 m** |
| Terra, marco BOM | 4 / −0,251 / 0,583 m | **6 / −0,251 / 0,485 m** |

- As 14 linhas antigas saem idênticas. Entram 9334M (−0,699, BOM), 9334N (−0,150, BOM) e 9319R (−0,544).
- 9319P e 394A caem como AGUA (p10 < 1 m): ficam no quebra-mar, e a coordenada do BDG é arredondada ao segundo (~30 m).
- A 9319R entrou como TERRA no limite (p10 = 1,12 m); também está no quebra-mar e foi nivelada em 1958, sem sigma, com o marco não
  encontrado desde 2006. Sem ela: n = 16, mediana −0,356 m, dp 0,534 m (igual à de antes).
- Conclusão: o viés não muda de forma relevante. A estimativa continua "MDT ~0,25 a 0,37 m abaixo da RN", com dispersão de ~0,5 m,
  e o sinal esperado (a chapa fica acima do terreno nu). Continua **não medido no terreno nu**.

**Armadilhas registradas:**
- 1,14 m como nível médio sobre o zero hidrográfico (valor antigo de estudos municipais) contra **1,28 m** da tábua atual e da F-41
  (Z0 = 128,2 cm): diferença de +0,14 m. Busca no código e nos documentos da API, da IA e do front em 2026-11-21: **nenhum uso de
  1,14 m nem de 1,25 m como nível médio**. O código usa 1,28 m (`AbstractTideExtremesAdapter`, `mean_level_m`). O ADR 0009 cita
  "−1,14 m" só como limite da estimativa de trabalho antiga de h_NR, já substituída pela emenda 1.
- Z0 de Suape (1,25 m): é de outro porto e não se transpõe para o Recife.

**Escala do PE3D:** as 88 folhas usadas (48 do Recife, 40 de Jaboatão) têm o tamanho e a nomenclatura da articulação 1:5.000
(`TERRENO-PE3D.md` §3.1). Segundo o levantamento externo, os produtos 1:5.000 foram calibrados com o geoide MAPGEO2010, e os
1:1.000 nas RRNN do IBGE anteriores ao REALT 2018 **[a confirmar no pedido 03]**; Cirilo et al. (seção 8) dão erro altimétrico melhor
que 25 cm no 1:5.000. Consequência: se o MDT usa MAPGEO2010 e as RRNN estão no REALT 2018, parte do viés de ~−0,3 m pode ser
diferença de modelo geoidal e de realização, não erro do LiDAR. Para GNSS de campo (ponto de controle), usar o hgeoHNOR2020.

## 10. Conferência da RN 2603F e incerteza de Δ (2026-11-22, onda 16, Fase 1.2)

**A ficha não foi trocada.** Fichas baixadas de novo do BDG em 2026-11-22
(`http://www.bdg.ibge.gov.br/bdg/pdf/relatorio.asp?L1=2603F` e `...L1=3640N`):

| RN | Altitude normal (m) | σ (m) | Última atualização | Local | SHA-256 do PDF (16) |
|---|---|---|---|---|---|
| 2603F | **2,7977** | **0,089** | 27/12/2021 | cais do Porto do Recife, "RN 2603-F = EG 8131555" | `f78d16cfa04d72f9` |
| 3640N | 5,0196 | 0,089 | 30/07/2018 | Recife (outro marco) | `2fcba18a6c4680dc` |

- A cópia de 2026-10-11 (`MAPI/fontes/ibge-bdg/2603F.pdf`, SHA-256 `e703eacc…`) traz os mesmos valores. Os PDFs diferem
  só no carimbo de data.
- As duas RRNN têm **o mesmo σ, 0,089 m**: é a sigma absoluta do ajustamento REALT 2018 para a linha de nivelamento, não
  uma marca da 3640N. Foi isso que fez a pesquisa de 22/11 achar que os valores tinham sido trocados. A altitude da 2603F
  (2,7977 m) é a que entra em Δ = 3,619 − 2,7977 = **0,821 m**.

**Incerteza registrada:**

| Componente | Valor | Entra em | Fonte |
|---|---|---|---|
| σ da altitude da RN 2603F | 0,089 m | Δ | ficha do BDG acima |
| Nivelamento da DHN (RN até o NR, F-41) | centímetros | Δ | ADR 0009, emenda 1 |
| Desacordo 393T × 2603F | 0,107 m | faixa de Δ (0,72 a 0,92 m) | ADR 0009, emenda 1 |
| Variação das médias mensais do nível do mar | **até 25 cm** | **não entra em Δ**: entra na diferença entre a maré da tábua e a maré real (resíduo) e no Z0 de 41 dias de 1997 | nota técnica do IBGE de 11/02/2009, p. 2: "as médias mensais podem alcançar diferenças de 25 cm" (Imbituba, 1957; o texto diz que o efeito é mais pronunciado no Sul e no Sudeste). URL na seção 1 deste extrato; acesso em 2026-11-22 |

- O Δ liga dois planos fixos (o NR da DHN e o datum de Imbituba) pela mesma RN. A oscilação mensal do mar muda o nível da
  água, não a posição dos planos. Por isso, no refluxo, os 25 cm vão para a folga (maré real − soleira) como incerteza da
  maré prevista. Não vão para Δ.
- Uso no código: `AlertaProperties.refluxo.deltaM = 0.821` é o único lugar com o número. A soleira em NR é fundo do ESIG +
  viés do MDT + Δ, ou seja, cota_SGB = maré_NR − 0,821 m.
- Auditoria de 2026-11-22 (1,144; 1,14; 1,106; 1,25; +0,250): `docs/extratos/auditoria-conversao-mare.md`.
