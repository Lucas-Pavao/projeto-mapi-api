# Extratos — hidrologia do Recife (parâmetros do modelo físico)

Tarefa F4-IA-11. Acesso a todas as fontes em **2026-10-11**. Citações literais de até 15 palavras, com URL. Usadas em `docs/PARAMETROS-MODELO-FISICO.md`.
Regra metodológica: nada aqui foi escolhido olhando eventos rotulados; o banco, `MAPI/exports` e os relatórios com chuva por dia positivo não foram abertos.

Legenda de verificação: **[lido]** = texto integral baixado e lido; **[trecho de busca]** = só o resumo devolvido pelo buscador, página não lida (não usar como fonte primária sem conferir); **[bloqueado]** = HTTP 403/PDF ilegível.

## 1. Silva e Araújo (2013) — equação IDF do Recife [lido]

Silva, S. R. da; Araújo, G. R. de S. "Algoritmo para determinação da equação de chuvas intensas". Rev. Bras. Geografia Física 6(5):1371-1383, 2013. Pluviógrafo PCD Recife (dados fornecidos pelo ITEP), série de 9 anos (período 2003-2011), Gumbel.
URL: https://periodicos.ufpe.br/revistas/rbgfe/article/download/233110/27026 (p. 1379, Equação 16, lida na imagem da página)

- Equação 16: `I = 1380,2176 · TR^0,19369 / (t + 22)^0,78201` (I em mm/h, TR em anos, t em min). R² = 0,9957; EPE = 0,05744 (p. 1380).
- "a série de máximas precipitações anuais ter pequena extensão" (p. 1379). O ajuste vem de 9 anos.
- Tabela 5 (p. 1380), intensidades em mm/h: 60 min → 50,31 (TR 2), 60,08 (TR 5), 68,71 (TR 10); 1440 min → 5,29 (TR 2), 6,31 (TR 5), 7,22 (TR 10).
- Compara-se à equação de Ramos (2010), série de 40 anos do pluviógrafo do aeroporto: "obtida equação semelhante à obtida por Ramos (2010)" (p. 1381). Ramos e Azevedo (2010) não foi obtido `[sem fonte]`.

## 2. EMLURB / PDDR — equação IDF do Recife [lido, via terceiros]

Equação citada por Barbosa (2025, item 5) como "elaborada pela EMLURB para Recife no contexto da elaboração do PDDR, conforme Silva Júnior (2015)": `i = 611,3425 · Tr^0,1671 / (t + 7,3069)^0,6348` (i em mm/h, t em min). O texto do PDDR/EMLURB com a equação não foi obtido; a fonte é a citação.
URL (Barbosa, 2025): https://repositorio.ufpe.br/bitstream/123456789/66887/1/TCC-%20Pedro%20Henrique%20Campos%20de%20Souza%20Barbosa.pdf (Equação 3, Tabela 11)
- Tabela 11 (TR = 5 anos): 10 min → 130,94 mm/h; 60 min → 55,29 mm/h.
- "Adotou-se um tempo de retorno de 5 anos (usual para obras de microdrenagem)".

## 3. Fonseca Neto et al. (2026) — limiar de chuva TR 2 anos, 1 h, Recife [lido]

Fonseca Neto, G. C. da F.; Casagrande, L.; Reis, A.; Barbarotto Jr., J. L.; Cabral, J. J. da S. P. "Avaliação de limiares críticos de precipitação para emissão de alertas de desastres hidrológicos na cidade de Recife-PE". I Congresso Pernambucano de Recursos Hídricos, Recife, 24-26/03/2026 (UFPE, Cemaden).
URL: https://static.even3.com/anais/1443737.pdf

- "para Recife, o volume de chuva para TR de 2 anos e duração de 60 minutos é de 55 mm" (limiar inicial, extraído da coletânea de Torres et al., 2025, Data 10(2):17, https://doi.org/10.3390/data10020017; essa coletânea não foi aberta).
- Dados: pluviômetros do Cemaden no município, 10 min, março/2016 a maio/2025, cruzados com ocorrências do banco do Cemaden e notícias locais.
- Resultado: "o limiar de 50% gerou 89 alertas, capturando mais eventos reais (72)", com 17 falsos; "100% do limiar eliminou os falsos alertas", mas só 11 alertas (percentuais testados: 50 a 100% de 55 mm).
- Conclusão: "eventos chuvosos que atingem 90% a 100% do limiar indicado correspondem a eventos com ocorrência".
- Ressalva do artigo: o limiar vale para o município inteiro e usa qualquer pluviômetro; não é por ponto. É um resumo de congresso (4 páginas, sem revisão de pares conhecida). O período dos dados (2016-2025) sobrepõe o do MAPi: o limiar foi calibrado em parte sobre as mesmas ocorrências que o MAPi usa como rótulo, o que reduz o ganho como "limiar independente". Declarar na dissertação.

## 4. Prefeitura do Recife / SEDEC — PLACON 2024 (estágios do COP) [lido]

Plano de Contingência 2024, Ação Inverno, Defesa Civil/SEDEC Recife (quadro "Níveis de criticidade e estágios operacionais", p. 58-60; fonte do quadro: "SEDEC RECIFE, 2023").
URL: https://acaoinverno.recife.pe.gov.br/wp-content/uploads/2024/04/002-PLANO-DE-CONTINGE%CC%82NCIA-2024.pdf

- Normalidade: "Previsão de chuvas inferior a 30 mm".
- Mobilização: "Previsão de chuvas acima de 30mm em até 24h"; "Nível da maré em 1,20 metros" (referência vertical da maré não declarada `[sem fonte]`).
- Atenção: "Previsão de chuvas moderadas em 24h". Alerta: "Previsão de chuvas fortes em até 24h". As faixas "moderadas" e "fortes" não têm mm no quadro.
- Critérios de ativação (p. 66): "Quantitativo dos índices pluviométricos precipitados em 06h, 12h e 24h"; dados vêm da APAC.
- São limiares de **previsão** (24 h) para mobilizar equipe, não de ocorrência de alagamento. Não há limiar em mm/1 h nem por ponto.

## 5. Prefeitura do Recife — PMSB, Produto 2 (consulta pública, 2026; COBRAPE) [lido]

Atualização do Plano Municipal de Saneamento Básico do Recife, Produto 2 — Atualização do Diagnóstico, rev. 01 de 01/12/2025.
URL: https://sesan.recife.pe.gov.br/wp-content/uploads/2026/02/Produto-2_Atualizacao-do-Diagnostico_Rev01_CONSULTA-PUBLICA.pdf

- Chuva diária P99 (Atlas Climatológico de Pernambuco, 1991-2020, APAC 2023): "80,5 mm no posto do Alto da Brasileira, 90,0 mm em Santo Amaro e 96,4 mm na Várzea" (cap. 7).
- Cadastro: "o município possui o cadastro dos elementos do sistema de microdrenagem, elaborado no âmbito do PDDR (2016)"; "sem informações de diâmetro e declividades"; 1.050,39 km de tubulação, 773,16 km de canaletas, 16.161 PV, 15.566 BL.
- "cerca de 20% da rede já possui cadastro completo, com dados como cotas de fundo e dimensões" (vídeo inspeção). Nota de rodapé 44: "Não foram disponibilizados os relatórios de cadastro já existentes."
- TR em projetos recentes: "O dimensionamento foi feito considerando tempo de retorno de 10 anos" (comunidades do ProMorar/SESAN, áreas ribeirinhas; não é norma geral do município).

## 6. PDDR (Prefeitura do Recife, fev/2015) — situação da microdrenagem [lido]

URL: https://conselhodacidade.recife.pe.gov.br/sites/default/files/2020-12/PDDR%20-%20PLANO%20DIRETOR%20DE%20DRENAGEM%20DO%20RECIFE.pdf (apresentação de slides)

- "extensão aproximada de 1.580 km. Muitos segmentos estão sub-dimensionados; Não há cadastro desta rede de drenagem."
- "Inúmeras áreas afetadas por alagamentos, devido influência das marés".
- "Cadastro de Microdrenagem - (60% realizado)" (situação em 2015). O Manual de Drenagem do PDDR é citado como concluído, mas não foi localizado online `[sem fonte]`.

## 7. URB Recife (2018) — projeto básico, Rua Limoeiro, Jordão [lido]

Relatório do Projeto Básico de Engenharia para Contenção e Drenagem na Rua Limoeiro, Jordão. URB Recife, maio/2018 (p. 6-8). Hidrologia tomada do estudo de macrodrenagem da RMR (AQUAPLAN).
URL: https://emur.recife.pe.gov.br/bitstreams/d8aa1b08-6121-4726-891c-747188873ac7/download

- "Áreas Pavimentadas C = 0,85 em 15% da área"; "Área Cobertas C = 0,95 em 55% da área"; "Áreas Vazias Planas C = 0,15 em 30% da área"; "Daí resultando o valor de C ponderado igual a 0,70."
- "Na local em estudo será adotado um tempo de recorrência de 5 anos."
- "Precipitação Média Anual - 1.654,9mm"; "Dias de Chuva por Ano - 185 dias".
- "O mais utilizado nos projetos de drenagem urbana situa-se no intervalo de 5 a 15 minutos" (tempo de entrada).
- Ressalva: encosta com 30% de área vazia; C = 0,70 subestima uma área plana e densa.

## 8. Xavier Filho (2016) citando Silva Júnior (2015) — C de área consolidada do Recife [lido]

Xavier Filho, R. F. "Controle de escoamento no lote como alternativa compensatória para os alagamentos em área do Recife-PE: aplicação da Lei Municipal 18.112/2015". TCC, UFPE, 2016, cap. 6.
URL: https://repositorio.ufpe.br/bitstream/123456789/46416/1/RICARDO%20FERRAZ%20XAVIER%20FILHO%20-%20CONTROLE%20DE%20ESCOAMENTO%20NO%20LOTE%20COMO%20ALTERNATIVA%20COMPENSAT%c3%93RIA%20PARA%20OS%20ALAGAMENTOS%20EM%20%c3%81REA%20DO%20RECIFE-PE%20-%20APLICA%c3%87%c3%83O%20DA%20LEI%20MUNICIPAL%20N%C2%BA%2018.112-2015..pdf

- "o coeficiente de escoamento (C) médio adotado para esta área de contribuição foi de 0,86, característico de uma área com urbanização consolidada" (Silva Júnior, 2015, dissertação UPE, não obtida; fonte secundária).
- Duração da chuva de projeto = tempo de concentração de 15 min; TR de 2 e 5 anos (mesma fonte).
- C de pré-urbanização usado em reservatórios de retardo: 0,15 (Lei 18.112/2015).

## 9. Bezerra (2025) — CN de bacia urbana do Recife [lido]

Bezerra, B. T. C. "Modelagem hidrodinâmica de evento de inundação com auxílio do PCSWMM em bacia urbana de Recife-PE". TCC, UFPE, 2025 (bacia da Av. 30 de Outubro, Jardim São Paulo; evento de 05/02/2025).
URL: https://repositorio.ufpe.br/bitstream/123456789/63527/1/TCC%20Breno%20Thierry%20Caldas%20Bezerra.pdf

- Tabela 3: CN = 90,64 (banco de CN da ANA, 2022, raster de 30 m, base MapBiomas) e 7 dias secos.
- "I𝑎 = 0,2 ∙ Smax" para pequenas bacias; "Smax = 1000/CN − 10" (a equação está em polegadas; em mm a forma é S = 25400/CN − 254).
- "o cadastro da drenagem disponibilizado pelo Plano de Drenagem da Prefeitura do Recife em 2016" foi usado para a microdrenagem.
- Lâmina observada em campo: "foi medido 1,05m de altura de lâmina d'água" na Rua Leandro Barreto (evento extremo de 2025).
- "cerca de 1.500 km de extensão de galerias e canaletas registradas (EMLURB, 2013)".

## 10. Barbosa (2025) — CN, abstração de depressão, TR [lido]

Barbosa, P. H. C. S. "Avaliação da eficiência de bacia de detenção na redução de alagamentos urbanos em Recife (PE)". TCC, UFPE, 2025. URL: ver item 2.
- "O valor de CN adotado para todas as sub-áreas foi de 98" (bacia com alta impermeabilização).
- Tabela 10 (McCuen 1996 apud Rossman 2012), CN grupos A/B/C/D: zonas comerciais 89/92/94/95; industriais 81/88/91/93; residencial lotes <500 m² 77/85/90/92; telhados, estacionamentos, ruas asfaltadas com drenagem 98/98/98/98.
- Tabela 8 (ASCE 1992 apud Rossman 2012), armazenamento em depressão: superfícies impermeáveis 1,27 a 2,54 mm; gramado 2,54 a 5,08 mm. Adotado: 2,54 mm (impermeável) e 5,0 mm (permeável).

## 11. NRCS TR-55 (1986) — CN urbano, Ia = 0,2 S [parcial]

Tabela 2-2a copiada em https://ehs.umich.edu/wp-content/uploads/2017/01/TR-55_curve_numbers.pdf (PDF não legível pela ferramenta; valores conferidos pelo resumo do buscador e pela Tabela 10 de Barbosa, 2025, item 10).
- Áreas impermeáveis (estacionamentos, telhados, ruas): CN 98 em todos os grupos. Espaço aberto em boa condição: 39/61/74/80.
- Ia = 0,2 S é suposição do método para bacias rurais pequenas (Bezerra, 2025, item 9); a literatura recente propõe razões menores (0,05) em bacias urbanas `[trecho de busca]` — se mudar a razão, recalibrar o CN.

## 12. Lâmina crítica em via — critérios (secundários)

- Governo do RS, comunicado de 23/09/2026 [lido]: "Apenas 15 centímetros de água em movimento já podem derrubar uma pessoa."; "Cerca de 30 centímetros de água em movimento podem ser suficientes para arrastar um veículo." URL: https://estado.rs.gov.br/autoridades-de-seguranca-publica-alertam-para-riscos-de-circulacao-durante-alagamentos
- Teixeira (2021), UTFPR, TCC, [lido]: "As duas hipóteses mais recorrentes são de lâminas de 10 e 15 cm (TUCCI et al., 2001)." Critério de dimensionamento de sarjeta, não de dano. URL: https://repositorio.utfpr.edu.br/jspui/bitstream/1/30734/1/drenagemurbanaalturaagua.pdf
- Defesa Civil de SP (15 cm arrasta veículo) e Bombeiros de MG (20 cm atinge escapamento) [trecho de busca; páginas não lidas; a cartilha MG excedeu o limite de tamanho]. URLs: https://www.bnewssaopaulo.com.br/noticias/politica/enchente-defesa-civil-ensina-acoes-para-evitar-fatalidade.html e https://www.bombeiros.mg.gov.br/storage/files/13/Cartilha de Dicas Preventivas - Pessoas em veículos em inundações - MOBILE.pdf
- Shand et al. (2011), Australian Rainfall and Runoff, Project 10 Stage 2, "Appropriate Safety Criteria for Vehicles" [bloqueado, HTTP 403]: https://arr.ga.gov.au/__data/assets/pdf_file/0018/40572/ARR_Project_10_Stage2_Report_Final.pdf. Citação só por existência; valores (v·y) não verificados.

## 13. Não verificado

- CPRM, Atlas Pluviométrico, IDF do Recife (estação Aeroporto, cód. 82899): o buscador mostrou parâmetros "a = 1423,97; b = 0,1124; c = 21; d = 0,7721" mas o arquivo baixado era o de Rio Largo (AL) e a página do Recife (`rigeo.cprm.gov.br/.../atlas_recife.pdf`) devolveu erro de certificado. `[sem fonte]` até abrir o PDF do Recife.
- Limiares da APAC e do Cemaden em mm para alagamento no Recife: não encontrados. APAC publica níveis por cor (Observação/Atenção/Alerta); sem tabela em mm localizada `[sem fonte]`.
