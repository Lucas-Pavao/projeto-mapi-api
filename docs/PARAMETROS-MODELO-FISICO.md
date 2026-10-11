# Parâmetros do modelo físico (balde) e limiar da regra de chuva

Tarefa F4-IA-11 (Agente 3). Levantamento em fontes externas, em **2026-10-11**. Trechos citados e URLs em `docs/extratos/hidrologia-recife.md` (os números entre colchetes, como [E7], remetem aos itens desse arquivo).

## Regra metodológica

Nenhum valor abaixo foi escolhido olhando os eventos de alagamento rotulados. Não foram abertos o banco, `MAPI/exports` nem os relatórios com chuva por dia positivo. Todo valor central vem de fonte externa; onde não há fonte, a faixa de sensibilidade é proposta com justificativa física e fica registrada antes de qualquer avaliação. Valores centrais, faixas e o limiar de chuva devem ser **congelados no manifesto do experimento antes de calcular qualquer métrica**.

Qualidade da fonte: **primária** = documento do órgão ou publicação com dado/método original; **secundária** = trabalho que cita a primária (TCC, resumo de congresso, notícia); **sem verificação** = só resumo do buscador.

## Tabela de parâmetros

Modelo do balde (glossário): entra `chuva × área contribuinte × C`; sai pela galeria se o nível lá fora permitir. Lâmina em múltiplos da ficha (5 mm).

| # | Parâmetro | Símbolo | Unidade | Valor central | Faixa de sensibilidade | Fonte (URL, página) | Acesso | Qualidade | Observação |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Coeficiente de escoamento, área urbana consolidada | C | adim. | **0,86** | 0,70 a 0,95 | Silva Júnior (2015, dissertação UPE) via Xavier Filho (2016, cap. 6): https://repositorio.ufpe.br/bitstream/123456789/46416/1/RICARDO%20FERRAZ%20XAVIER%20FILHO%20-%20CONTROLE%20DE%20ESCOAMENTO%20NO%20LOTE%20COMO%20ALTERNATIVA%20COMPENSAT%c3%93RIA%20PARA%20OS%20ALAGAMENTOS%20EM%20%c3%81REA%20DO%20RECIFE-PE%20-%20APLICA%c3%87%c3%83O%20DA%20LEI%20MUNICIPAL%20N%C2%BA%2018.112-2015..pdf [E8] | 2026-10-11 | secundária | Microbacia de 4,7 ha no Recife, "urbanização consolidada", chuva de 15 min, TR 2 e 5 anos. Dissertação original não obtida. Limite inferior 0,70 vem do C ponderado do projeto da URB Recife (2018, p. 7, encosta com 30% de área vazia) [E7]; limite superior 0,95 é o C de áreas cobertas (mesma fonte) |
| 2 | Coeficiente de escoamento, ponderado de projeto | C | adim. | 0,70 | (referência da faixa) | URB Recife (2018), p. 7: https://emur.recife.pe.gov.br/bitstreams/d8aa1b08-6121-4726-891c-747188873ac7/download [E7] | 2026-10-11 | primária (documento de órgão municipal) | 15% pavimento (0,85), 55% cobertas (0,95), 30% vazias planas (0,15). Não é área plana e densa; serve de cota inferior |
| 3 | Número da curva (alternativa ao C) | CN | adim. | **90,64** | 85 a 98 | Bezerra (2025), Tabela 3: https://repositorio.ufpe.br/bitstream/123456789/63527/1/TCC%20Breno%20Thierry%20Caldas%20Bezerra.pdf [E9]; faixa: TR-55 Tab. 2-2a via Barbosa (2025), Tab. 10 [E10, E11] | 2026-10-11 | secundária (CN do raster da ANA 2022, aplicado em bacia da Av. 30 de Outubro) | 85 (residencial denso, grupo C/D) a 98 (impermeável, usado por Barbosa 2025). Comercial 89 a 95 por grupo de solo |
| 4 | Abstração inicial | Ia | mm | **5,2** (= 0,2·S com CN 90,64, S = 26,2 mm) | 1,0 (CN 98) a 9,0 (CN 85); razão Ia/S de 0,05 a 0,2 | Ia = 0,2·S: Bezerra (2025), Eq. 8-9 [E9]; TR-55 [E11] | 2026-10-11 | secundária (cálculo do autor sobre fórmula do SCS) | 5,2 mm é ~1 ficha. Fórmula de S em mm: 25400/CN − 254. Razão 0,2 foi calibrada em bacias rurais; para urbano há trabalhos com 0,05 `[trecho de busca]` |
| 5 | Armazenamento em depressão (superfície impermeável) | Sd | mm | 2,54 | 1,27 a 2,54 | ASCE (1992) apud Rossman (2012), via Barbosa (2025), Tab. 8: https://repositorio.ufpe.br/bitstream/123456789/66887/1/TCC-%20Pedro%20Henrique%20Campos%20de%20Souza%20Barbosa.pdf [E10] | 2026-10-11 | secundária | Alternativa a Ia se o balde usar perda inicial de superfície em vez de CN. Não somar com Ia (dupla contagem) |
| 6 | Intensidade IDF do Recife, TR 2 anos, 60 min | i(2, 60) | mm/h | **50,3** | 47,4 a 55 | Silva e Araújo (2013), Eq. 16, p. 1379: https://periodicos.ufpe.br/revistas/rbgfe/article/download/233110/27026 [E1]; EMLURB/PDDR via Barbosa (2025), Eq. 3 [E2]; Torres et al. (2025) via Fonseca Neto et al. (2026): 55 mm [E3] | 2026-10-11 | primária (artigo revisado; equação com 9 anos de série) e secundária (EMLURB) | Três equações concordam em ~±8% em 60 min. Ver tabela de limiares abaixo |
| 7 | Período de retorno de projeto, microdrenagem | TR | anos | **5** | 2 a 10 | URB Recife (2018), p. 8 [E7]; Barbosa (2025): "usual para obras de microdrenagem" [E10]; PMSB (2026): 10 anos em projetos ProMorar [E5] | 2026-10-11 | primária (URB Recife) e secundária | Não achei norma municipal geral. 5 anos é o mais citado para o Recife; 10 anos aparece em projetos ribeirinhos recentes; 2 anos aparece em Silva Júnior (2015) como cenário de simulação |
| 8 | Chuva de projeto equivalente da galeria (TR 5, 60 min) | P5,60 | mm | **60,1** (Silva e Araújo) ou 55,3 (EMLURB) | 47 a 69 (TR 2 a 10, 60 min) | Cálculo sobre as Eq. 16 [E1] e 3 [E2] | 2026-10-11 | secundária (derivado) | Lâmina que a galeria foi, em tese, desenhada para escoar. Acima disso, a rua acumula mesmo sem maré. Subdimensionamento reconhecido pelo PDDR: "Muitos segmentos estão sub-dimensionados" [E6] |
| 9 | Lâmina crítica de "alagamento" em via | h_crit | cm | **15** | 10 a 30 | RS (2026-09-23): 15 cm derruba pessoa, 30 cm arrasta veículo [E12]; Teixeira (2021): 10 e 15 cm como lâmina de projeto de sarjeta [E12] | 2026-10-11 | secundária | 15 cm é critério de projeto e de risco a pessoa; 30 cm é risco a veículo em água em movimento. Sem norma brasileira única. O CKAN registra notificação, não lâmina |
| 10 | Tempo de entrada (concentração inicial) | Te | min | 10 | 5 a 15 | URB Recife (2018), p. 8: "5 a 15 minutos" [E7]; Silva Júnior (2015) usa Tc = 15 min [E8] | 2026-10-11 | primária e secundária | Só para o balde com passo sub-horário; em passo de 1 h, irrelevante |
| 11 | Chuva diária P99 (referência de evento intenso) | P99,24h | mm | 90,0 | 80,5 a 96,4 | PMSB (2026), cap. 7, Atlas Climatológico de PE 1991-2020 (APAC 2023): https://sesan.recife.pe.gov.br/wp-content/uploads/2026/02/Produto-2_Atualizacao-do-Diagnostico_Rev01_CONSULTA-PUBLICA.pdf [E5] | 2026-10-11 | secundária (cita APAC) | Postos Alto da Brasileira (80,5), Santo Amaro (90,0), Várzea (96,4). Percentil 99 do total diário, não limiar de alagamento |
| 12 | Extensão e cadastro da microdrenagem | — | km | 1.050,39 (tubos) + 773,16 (canaletas) | — | PMSB (2026) [E5]; PDDR (2015): 1.580 km [E6] | 2026-10-11 | primária (documento municipal) | Sem diâmetro, declividade nem soleira no cadastro disponível; só ~20% da rede com cadastro completo e esse dado não é público (ver "sem fonte") |

### Limiares de chuva por duração e período de retorno (derivados das IDF)

Cálculo sobre as Equações 16 [E1] e 3 [E2]. Lâmina em mm = intensidade × duração. Duas colunas: Silva e Araújo (2013) / EMLURB-PDDR. Durações acima de 120 min extrapolam as equações além do que a série curta sustenta; usar como ordem de grandeza.

| Duração | TR 2 anos | TR 5 anos | TR 10 anos |
|---|---|---|---|
| 15 min | 23,4 / 23,9 | 28,0 / 27,9 | 32,0 / 31,3 |
| 30 min | 35,9 / 34,5 | 42,9 / 40,2 | 49,1 / 45,1 |
| 1 h | 50,3 / 47,4 | 60,1 / 55,3 | 68,7 / 62,1 |
| 3 h | 74,6 / 74,3 | 89,1 / 86,6 | 101,8 / 97,2 |
| 6 h | 90,6 / 96,9 | 108,2 / 113,0 | 123,8 / 126,8 |
| 24 h | 126,9 / 162,4 | 151,5 / 189,2 | 173,3 / 212,5 |

Referência independente: Torres et al. (2025) via Fonseca Neto et al. (2026) dão 55 mm para TR 2 anos, 60 min [E3].

## Limiar da regra de chuva (baseline "chuva > X")

**Recomendação: X = 55 mm em 1 h** (TR 2 anos, 60 min), com os níveis de sensibilidade 50% (27,5 mm) e 70% (38,5 mm) do mesmo limiar, fixados no manifesto antes da avaliação.

Origem: Fonseca Neto et al. (2026), UFPE e Cemaden, I Congresso Pernambucano de Recursos Hídricos, https://static.even3.com/anais/1443737.pdf [E3]. Os autores partem de 55 mm (Torres et al., 2025, coletânea de IDF do Brasil) e testam 50% a 100% do limiar contra ocorrências de alagamento e enxurrada do Cemaden em Recife (pluviômetros do Cemaden, 2016 a 2025). Em 50% (27,5 mm/h): 89 alertas, 72 com ocorrência, 17 falsos. Em 100%: 11 alertas, nenhum falso. As três IDF independentes (55; 50,3; 47,4 mm) apontam a mesma ordem de grandeza.

Por que este e não outro:
- É definido por IDF (clima) e validado por terceiros contra ocorrências; o MAPi não participou da escolha.
- Casa com a grade horária do MAPi (janela de 1 h).
- O limiar da Prefeitura (PLACON 2024: "Previsão de chuvas acima de 30mm em até 24h" para Mobilização) é de **previsão** para mobilizar equipes, 24 h, sem relação direta com alagamento observado [E4]; entra como segunda regra de sensibilidade (30 mm em 24 h), não como a principal.

Ressalvas que devem constar na dissertação:
1. **Independência parcial.** O período de Fonseca Neto et al. (2016-2025) sobrepõe o dos rótulos do MAPi. O limiar de 55 mm vem da IDF, não do ajuste a essas ocorrências, mas a leitura "50% a 100%" foi avaliada sobre elas. Declarar.
2. **Escala.** O estudo vale para o município (qualquer pluviômetro ultrapassando o limiar), e o MAPi usa chuva por ponto, de reanálise Open-Meteo no treino. A distribuição de chuva horária da reanálise é mais suave que a de pluviômetro; 55 mm/h pode ser raro ali. Medir a diferença Open-Meteo × pluviômetro antes de interpretar o baseline (sem olhar rótulos, só a distribuição da chuva).
3. **Natureza do documento.** Resumo de congresso de 4 páginas, sem revisão de pares conhecida; qualidade **secundária**.
4. Não há limiar oficial em mm para alagamento da APAC ou do Cemaden publicado `[sem fonte]`. A APAC emite níveis por cor (Observação, Atenção, Alerta); a tabela em mm não foi localizada.

## Parâmetros sem fonte

| Parâmetro | Valor central | Faixa de sensibilidade proposta | Justificativa |
|---|---|---|---|
| Capacidade da galeria por ponto (vazão, m³/s ou mm/h equivalente) | `[sem fonte]` | De 0 a chuva de projeto TR 5 (60 mm/h, linha 8), em passos de 20%; mais um caso com galeria totalmente obstruída | O cadastro existe na Prefeitura (PDDR, 2016) mas sem diâmetro e declividade, e não é público [E5]. O PDDR reconhece subdimensionamento e obstrução [E6]. A faixa parte do teto teórico (projeto) até zero (lixo e assoreamento) |
| Soleira da galeria no canal (`outletSillElevation`) | `[sem fonte]` | Cota da rua menos 0,5 a 3,0 m, em passos de 0,5 m (soleira abaixo da rua); mais o desnível NR↔MDT do ADR 0009 (±0,3 m) | Nenhum cadastro público traz cotas de fundo. ~20% da rede tem cota de fundo (vídeo inspeção), dado não divulgado [E5]. A faixa é um gradiente físico plausível para galeria que descarrega em canal de planície; sem validação |
| Área contribuinte por ponto | MDT do PE3D (ADR 0008/0009), em validação | ±30% sobre a área derivada | Fonte é o terreno, não a literatura. A rede enterrada redireciona fluxo e a área efetiva pode diferir da topográfica |
| Curva cota-volume da depressão | Derivada do MDT | ±30% no volume por cm | Idem; ignora cavidades de menos de 1 m (resolução) e edificações |
| Taxa de infiltração (Horton/Green-Ampt) | não usada (CN) | Não varrer | Com CN, a infiltração está embutida. Em área com C ≥ 0,70 o termo é de segunda ordem; solos do Recife não foram classificados em grupo hidrológico `[sem fonte]` (a ANA usou grupo implícito no CN 90,64) |
| Resíduo meteorológico e erro da maré prevista | — | Do ADR 0009: ±0,3 m na folga | Fora do escopo desta tarefa |
| Limiar de chuva da APAC/Cemaden em mm para alagamento | `[sem fonte]` | usar a regra da seção anterior | Não publicado/não localizado |
| IDF do CPRM (Atlas Pluviométrico) para o Recife | `[sem fonte]` | — | O resumo do buscador citou parâmetros (a = 1423,97; b = 0,1124; c = 21; d = 0,7721, estação Aeroporto) mas o PDF do Recife não pôde ser aberto (erro de certificado em `rigeo.cprm.gov.br`); o arquivo aberto era o de Rio Largo (AL). Não usar |
| Norma municipal de lâmina máxima e de TR de microdrenagem | `[sem fonte]` | 2 a 10 anos; 10 a 30 cm | O Manual de Drenagem do PDDR é citado como concluído [E6], mas não foi localizado online. Pedir à EMLURB/SESAN |
| Critério de arraste de veículo (produto velocidade × profundidade) | `[sem fonte]` | — | Shand et al. (2011, ARR) retornou HTTP 403; a lâmina de 15 a 30 cm vem de alertas de Defesa Civil, sem v·y |

## Existe cadastro público de galerias e soleiras?

**Não localizei cadastro público.** O que há:
- A Prefeitura tem o cadastro de microdrenagem do PDDR (2016): tipo (tubulação ou canaleta) e comprimento, 16.161 poços de visita, 15.566 bocas de lobo, 43.690 caixas gaveta; **sem diâmetro nem declividade**. Cerca de 20% da rede tem cadastro completo com cotas de fundo e dimensões, por vídeo inspeção [E5].
- O próprio PMSB registra "Não foram disponibilizados os relatórios de cadastro já existentes" (nota 44) [E5]. O TCC de Bezerra (2025) obteve o cadastro da Prefeitura, o que sugere acesso por pedido, não por portal [E9].
- Soleiras (cota de saída no canal) não aparecem em nenhum documento lido.

Ação proposta: pedido formal à EMLURB/SESAN (via Lei de Acesso à Informação) do cadastro de microdrenagem georreferenciado, com cota de fundo e diâmetro, para os 5 pontos críticos e suas saídas nos canais. Sem isso, soleira e capacidade ficam como parâmetros de sensibilidade, e a conclusão do baseline do balde é condicional a essa faixa.

## Uso sugerido no experimento

1. Congelar no manifesto: C = 0,86 (faixa 0,70 a 0,95), Ia = 5,2 mm (1,0 a 9,0), h_crit = 15 cm (10 a 30), limiar X = 55 mm/h (27,5 e 38,5 como sensibilidade).
2. Capacidade da galeria e soleira: varrer a faixa "sem fonte" e reportar o balde como família de curvas, não como um ponto.
3. Se C e CN forem usados juntos, escolher um (C no balde racional; CN se o balde usar perda inicial). Não aplicar C e Ia ao mesmo tempo sem justificar.
4. Nenhum dos valores acima pode ser recalibrado com os positivos de teste; calibração só no treino, e o balde só entra como feature se isso for respeitado (MODELAGEM-IA, A.2.1).
