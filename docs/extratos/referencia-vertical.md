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
