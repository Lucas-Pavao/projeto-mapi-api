# Parâmetros de entupimento da drenagem (literatura, como faixa)

- Data: 2026-11-21 · Prompt 3, tarefa 4 · Uso: faixas de sensibilidade do modelo de estados (`MODELO-DISPONIBILIDADE-GALERIA.md`).
- Regra: nenhum valor foi escolhido olhando dia de alagamento. Valor sem fonte lida entra só como faixa, marcado `[sem fonte]`.
- "Lido" = texto conferido no documento original nesta data. "Não lido" = a fonte foi indicada, mas não foi possível abrir o texto
  (motivo ao lado); o número **não** é usado até ser conferido.

## 1. Quadro-resumo

| # | Parâmetro | Valor | Fonte (página) | Situação |
|---|---|---|---|---|
| 1 | Fator de entupimento de projeto, 1 grelha | 50% | Guo (2012), p. 24 | lido |
| 2 | Fator de entupimento de projeto, 1 boca de guia (abertura no meio-fio) | 10% no texto; 0,12 na Tabela 5.1 | Guo (2012), p. 24–25 | lido (o próprio relatório usa os dois valores) |
| 3 | Fator para N unidades em série | `Cg = (C/N)·Σ_{i=1..N} e^(i−1)`, e = 0,5 (grelha) e 0,25 (guia) | Guo (2012), Eq. 5.1 e Tabela 5.1, p. 24–25 | lido |
| 4 | Área efetiva em ponto baixo | `Ae = (1 − Cg)·A` | Guo (2012), Eq. 5.3, p. 25 | lido |
| 5 | Entupimento em ponto baixo (HEC-22) | exemplo de projeto com grelha 50% entupida; grelha sozinha **não recomendada** em ponto baixo | FHWA HEC-22, 3ª ed. (2009, rev. 2013), p. 4-56 a 4-58 | lido |
| 6 | Entupimento em greide (HEC-22) | não reduzir a capacidade "except where local experience indicates" | HEC-22, p. 4-40 | lido |
| 7 | Fator de 50% da área de grelha (TxDOT Fort Worth, 2019) | 50% `[a conferir]` | TxDOT, "Inlet Hydraulic Parameters Summary" | **não lido**: o servidor `ftp.dot.state.tx.us` falhou na verificação do certificado TLS |
| 8 | Coeficiente de entupimento para várias unidades (MHFD/UDFCD, cap. 7, Tabela 7-9) | — | MHFD, Urban Storm Drainage Criteria Manual, vol. 1 | **não lido**: a página do MHFD deu 404 e a cópia de Fort Collins falhou no DNS. O método do MHFD é o de Guo (2000c, 2006), que está lido no item 3 |
| 9 | Fatores por tipo de boca de lobo (DAEE/CETESB 1980, DNIT 2006, Tomaz) | — | — | **não achado** online com página. `[sem fonte]` |
| 10 | Acúmulo de sedimento em caixa de boca de lobo: 14 a 24 mm/mês (Butler e Karunaratne, 1995, via Ekechukwu et al.) | — | UWE Bristol | **não lido**: o repositório da UWE respondeu 403. E o artigo de Butler e Karunaratne de 1995 que existe (*Water Research* 29(2), 719–729) trata de **eficiência de retenção** de sólidos, não de taxa de acúmulo: a atribuição precisa ser conferida antes de qualquer uso |
| 11 | Acúmulo medido em 407 caixas de boca de lobo (Holanda, ~14 meses) | média de **+18 mL/dia** por caixa; principais fatores: vegetação, volume de chuva, grau de enchimento | Rietveld, Clemens e Langeveld (2020), p. 549 (resumo) e p. 554 | lido |
| 12 | Limpeza a cada 2 meses em vez de 1 vez por ano | massa retirada ~3 vezes maior | Langeveld, Liefting e Schilperoort (2016), citado por Rietveld et al. (2020), p. 549 | lido (citação secundária) |
| 13 | Bocas de lobo com problema em microbacia urbana brasileira | 139 de 520 obstruídas (26,7%) e 69 danificadas (13,3%): 40% com problema (o resumo fala em "aproximadamente 45%") | Moraes e Silva (2019), Cuiabá-MT, Tabela 2, p. 85; visitas de jan. a out. de 2016 | lido; **só ordem de grandeza** |
| 14 | Rede de Petri estocástica para regimes de limpeza de boca de lobo | estados por nível de sedimento; aprendizado de máquina para classificar as caixas sem dado | Rodrigues, Neves, Wallis, Brook e Morosiuk (2023) | só o resumo lido (texto completo não acessível) |

## 2. Detalhe das fontes lidas

**Guo (2012).** Guo, J. C. Y. *Hydraulic efficiency of grate and curb-opening inlets under clogging effect*. Colorado Department of
Transportation, Report CDOT-2012-3, abr. 2012. https://rosap.ntl.bts.gov/view/dot/24517/dot_24517_DS1.pdf (acesso em 2026-11-21).

- p. 24: "To be conservative, a clogging factor of 50% is recommended for a single grate and 10% for a single curb-opening inlet."
- p. 24: "the clogging factor for multiple inlets in serial is equal to the total clogging percentage divided by the number of inlet
  units as (Guo 2000c)" — Eq. 5.1: `Cg = (1/N)(C + eC + e²C + … + e^(N−1)C)`, "e = decay ratio less than unity".
- Tabela 5.1 (p. 25), observado / previsto:

| N | Guia, observado | Guia, previsto (e = 0,25) | Grelha, observado | Grelha, previsto (e = 0,5) |
|---|---|---|---|---|
| 1 | 0,12 | 0,12 | 0,50 | 0,50 |
| 2 | 0,08 | 0,08 | 0,35 | 0,38 |
| 3 | 0,05 | 0,05 | 0,25 | 0,29 |
| 4 | 0,03 | 0,04 | 0,20 | 0,23 |

- Leitura: é a forma certa de "tirar a média" de várias bocas de lobo, porque o lixo se concentra nas primeiras. **Vale para unidades
  em série no mesmo ponto** (uma grelha dupla, três bocas seguidas), não para bocas espalhadas pela bacia.

**HEC-22.** FHWA. *Urban Drainage Design Manual*, HEC-22, 3ª ed., FHWA-NHI-10-009, set. 2009 (rev. ago. 2013; substituída pela
4ª ed., fev. 2024). https://www.fhwa.dot.gov/engineering/hydraulics/pubs/10009/10009.pdf (acesso em 2026-11-21). A 4ª edição
(FHWA-HIF-24-006) não pôde ser aberta (arquivo acima de 10 MB para a ferramenta de leitura); os valores abaixo são da 3ª.

- p. 4-40: "Since partial clogging of inlets on grade rarely causes major problems, allowances should not be made for reduction in
  inlet interception capacity except where local experience indicates an allowance is advisable."
- p. 4-56: "Total or partial clogging of inlets in these locations can result in hazardous ponded conditions. Grate inlets alone are
  not recommended for use in sag locations because of the tendencies of grates to become clogged."
- p. 4-58 (exemplo de projeto): grelha em ponto baixo dimensionada "assuming 50% clogging".

**Rietveld et al. (2020).** Rietveld, M. W. J.; Clemens, F. H. L. R.; Langeveld, J. G. Monitoring and statistical modelling of the
solids accumulation rate in gully pots. *Urban Water Journal* 17(6), 549–559, 2020. doi:10.1080/1573062X.2020.1800760. Repositório
TU Delft (CC BY-NC-ND 4.0), acesso em 2026-11-21. p. 554: "a dataset of 4173 data points with an average accumulation rate of +18 mL/day".
Clima temperado (chuva anual ~840 mm, p. 550); não é transponível para o Recife, serve para a **forma** da curva (o leito estabiliza
em alguns meses: Post et al., 2016, citado na p. 549).

**Moraes e Silva (2019).** Moraes, G. F.; Silva, A. R. C. B. Metodologia de diagnóstico do sistema de drenagem pluvial aplicado à
microbacia do córrego Quarta-Feira em Cuiabá-MT. *E&S – Engineering and Science* 8(1), 2019. doi:10.18607/ES201988061.
https://periodicoscientificos.ufmt.br/ojs/index.php/eng/article/download/8061/5452/27964 (acesso em 2026-11-21). Tabela 2 (p. 85):
520 bocas de lobo, 312 adequadas, 139 obstruídas, 69 danificadas.

**Rodrigues et al. (2023).** Rodrigues, L. G.; Neves, L. C.; Wallis, J.; Brook, R.; Morosiuk, K. Inclusion of Stochastic Petri-net
models on a risk-based tool for the maintenance of road drainage systems. In: *Life-Cycle of Structures and Infrastructure Systems*.
CRC Press, 2023, 8 p. doi:10.1201/9781003323020-417 (acesso ao resumo em 2026-11-21). Do resumo: "The stochastic Petri net model is used
to predict gullies' sediment level ranges over time based on historical data"; "Machine learning techniques are used to classify
gullies and overcome data absence"; as distribuições são usadas "to derive the probability of reaching a limit state given an assigned
cleansing frequency". Estados, transições e números do texto completo **não foram lidos**. Obs.: o prompt cita "Rodrigues Neves et
al."; na página, os autores são L. G. Rodrigues e L. C. Neves (dois autores diferentes).

## 3. Faixas propostas para o modelo de estados

| Estado | Fração da capacidade de entrada | Base |
|---|---|---|
| Livre | 1,0 | definição |
| Parcialmente obstruída | 0,5 a 0,9 (grelha: 0,5; guia: 0,88) | 1 − fator de Guo (2012) para 1 unidade |
| Entupida | 0 a 0,5 | `[sem fonte]`: faixa física, de totalmente fechada até o fator de projeto de grelha |

O tipo da boca de lobo (grelha ou guia) não está no cadastro do ESIG (prompt 1); até a ficha de campo, as duas faixas valem.

## 4. Pendências

1. TxDOT (item 7): abrir o PDF em outro computador ou pedir uma cópia; o certificado do servidor falhou.
2. MHFD Tabela 7-9 (item 8): obter o capítulo 7 da edição vigente e conferir se repete Guo (2012).
3. Butler e Karunaratne (item 10): conferir a fonte primária do "14 a 24 mm/mês" antes de usar.
4. Tabela brasileira de fatores por tipo de boca de lobo (item 9): DAEE/CETESB (1980), *Drenagem urbana: manual de projeto*, ou DNIT
   (2006), *Manual de drenagem de rodovias*, em biblioteca. Pedir também o Manual de Drenagem do PDDR (pedido 06, item 6).
