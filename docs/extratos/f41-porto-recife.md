# Extrato — Ficha F-41 do Porto do Recife e RRNN do IBGE

Tarefa F3-GEO-08. Extração em 2026-10-11, uma única vez. Usado na emenda 1 do ADR 0009.
Os PDFs ficam fora do repositório, em `MAPI/fontes/`, porque o termo de uso da DHN não foi localizado
(FONTES-E-LICENCAS §8). Hashes em `MAPI/fontes/SHA256SUMS`.

## 1. Arquivo

- `30645_-_porto_do_recife_f-41_-_tabuas_padrao_v1-10.pdf`, 2 páginas, 223 317 bytes.
  SHA-256 `73f5392200cfbd95d7ae39472dd98a54595b61dff2e52ab52e0ea3ded0ffbd4d`. Entregue pelo Lucas em 2026-10-11.
- Cabeçalho: "F-41 – Padrão - Porto de Recife – 30645 - Versão 1/2010"; "DESCRIÇÃO DE ESTAÇÃO MAREGRÁFICA";
  "Esta ficha foi compilada da F-41-902–001/97 e da F-41-902-002/01. Atualizada em15/09/2010."
- Extração: `pdftotext -layout` (texto) e leitura visual do diagrama da página 2, renderizado a 110 dpi. As setas
  verticais do diagrama partem da linha da RN-2603F-IBGE. Os números da §2 vêm dessa leitura.

## 2. Dados da estação (citações literais)

- Estação 30645, carta 902, "Lat 08º 03'.4 S", "Long 034º 52`.1 W", "Datum WGS-84", "Fuso + 3".
- "O nível de redução está 47,7 centímetros acima do zero da régua de 1997."
- "Fonte de informação: Análise estatística e harmônica de 41 dias de observação da maré."
- Observação 1: "Período de observação para obtenção dos elementos de maré. 27/07/1997 a 05/09/1997."
- Observação 2: análise pelo método harmônico, "sendo os dados processados pelo BNDO."
- Diagrama: "Z0 = 128,2 cm" (NM acima do NR); "S0 = 175,9 cm" (NM acima do zero da régua; 47,7 + 128,2 = 175,9).
- Diagrama, a partir da RN-2603F-IBGE: "409,6 cm" até o zero da régua; "361,9 cm" até o NR; "Altitude = 233,7 cm"
  até o NM. Conferência: 409,6 − 47,7 = 361,9 e 361,9 − 128,2 = 233,7. "Altitude" aqui é a altura sobre o NM local,
  **não** a altitude do SGB.
- Desníveis entre as RN (diagrama, de cima para baixo): RN-393T-IBGE → RN3-DHN "68,9 cm"; RN3-DHN → RN2-DHN "8,1 cm";
  RN2-DHN → RN-2603F-IBGE "3,9 cm"; RN-2603F-IBGE → RN1-DHN "13,1 cm".
- RN-2603F-IBGE: "cravada no chão junto à casa de força entre os armazéns 6 e 7, junto ao meio-fio. Citada a partir de 1997."
- RN-393T-IBGE: "cravada entre o par esquerdo de pilares do antigo prédio da Administração do porto [...] Citado a partir de 1975."
- RN1-DHN: "Lat: 08º03'23",0490 S – Long: 034º 52'06",9804 W – (F-21-902-003/01). Implantada em 2001."
  Observação 3: "A RN1-DHN foi rastreada em 2001."

### Alturas sobre o NR (cálculo do autor do extrato, a partir dos números acima)

| RN | Altura sobre o NR (m) | Conta |
|---|---|---|
| RN1-DHN | 3,488 | 3,619 − 0,131 |
| **RN-2603F-IBGE** | **3,619** | diagrama |
| RN2-DHN | 3,658 | 3,619 + 0,039 |
| RN3-DHN | 3,739 | 3,658 + 0,081 |
| RN-393T-IBGE | 4,428 | 3,739 + 0,689 |

## 3. IBGE — BDG, as duas RRNN citadas na F-41

Relatórios gerados em 2026-10-11 em `http://www.bdg.ibge.gov.br/bdg/pdf/relatorio.asp?L1=<RN>`.

| RN | Altitude normal (m) | Sigma (m) | Datum | Medição / cálculo | Marco | Coordenada (fonte) |
|---|---|---|---|---|---|---|
| 2603F | 2,7977 | 0,089 | Imbituba | 01/11/2005 / 30/07/2018 (REALT 2018) | "BOM", visita 25/03/2009 | 08º 03' 24,00" S, 034º 52' 06,00" W ("GPS Navegação") |
| 393T | 3,7135 | 0,089 | Imbituba | 30/07/1987 / 30/07/2018 | "NÃO ENCONTRADO", visita 04/04/2006 | 08º 03' 37,90" S, 034º 52' 14,20" W ("Imagem") |

- 2603F, localização: "No cais do Porto do Recife, na base de concreto de uma tampa de esgoto [...] entre os armazéns 6
  e 7 [...] 160 m além da RN 2603-E." Observação: "RN 2603-F = EG 8131555." A descrição coincide com a da F-41.
- 393T, localização: "Chapa cravada na base de uma das colunas do antigo prédio da fiscalização do Porto de Recife".
- SHA-256 das cópias guardadas: 2603F `e703eacccf87a5a4…`, 393T `4414caca9cb3d1f1…` (completos em
  `MAPI/fontes/SHA256SUMS`). O relatório traz a data de geração, então um novo download muda o hash.

## 4. Cadeia D − E (Figura 4 do IBGE, extrato `referencia-vertical.md` §1)

D = altura da RN acima do NR (F-41); E = altitude da mesma RN no SGB (BDG). Altitude do plano NR: h_NR = E − D.
Δ do ADR 0009 = −h_NR = D − E.

| RN | D (m) | E (m) | Δ = D − E (m) | Uso |
|---|---|---|---|---|
| **2603F** | 3,619 | 2,7977 | **+0,821** | principal: marco "BOM", no cais junto à régua, nivelado pelo IBGE em 2005 |
| 393T | 4,428 | 3,7135 | +0,715 | checagem: marco não encontrado desde 2006, nivelado em 1987 |

- Diferença entre as duas: 0,107 m (fechamento entre o nivelamento da DHN e o do IBGE nesse trecho).
- NMM local de 1997 no SGB: Z0 − Δ = 1,282 − 0,821 = **+0,461 m** acima do Datum de Imbituba
  (pela 393T: +0,567 m).
- `mean_level` = 1,28 m da TabuaMare é o Z0 = 128,2 cm da F-41: **confirmado**. A F-41 atribui o Z0 a 41 dias de 1997,
  não à série de 1978–1986 citada no extrato `referencia-vertical.md` §7. Divergência registrada.

## 5. Checagem do MDT contra RRNN do IBGE (opção B2)

Script: `scripts/referencia-vertical/vies_mdt_rn_ibge.py`, com a tabela `rns_ibge_recife.csv` (19 RRNN das séries 393 e
2603 no Recife, BDG). Saída completa em `scripts/referencia-vertical/saida_vies_mdt.txt`.

- 5 RRNN caíram na lâmina d'água (p10 < 1,0 m; o MDT dá 0,4–0,5 m sobre a água). As coordenadas do BDG são arredondadas
  ao segundo (~30 m) e o cais está a menos disso da água. Inclui a própria 2603F.
- 14 RRNN em terra: MDT − IBGE com mediana **−0,356 m**, desvio-padrão 0,563 m, de −1,392 a +0,365 m.
  Só os 4 marcos "BOM": mediana −0,251 m (−0,160; −0,132; −0,342; −1,362).
- RN1-DHN, com coordenada precisa da F-41 (E 294 074,3, N 9 108 996,0, UTM 25S, transformada como SIRGAS 2000):
  MDT 2,260 m no pixel. Altitude esperada = 2,7977 − 0,131 = 2,667 m. MDT − SGB = −0,41 m.
- Ondulação geoidal no porto (EGM2008 via PROJ, 2026-10-11): N = −5,74 m (EGM96: −5,60 m). Se o MDT estivesse em
  altura elipsoidal, as diferenças seriam da ordem de −5,7 m. O observado (decímetros) **descarta altura elipsoidal**.
- Por que a checagem não fecha o datum ao decímetro: as chapas ficam em bases de coluna, meio-fio e tampas, acima do
  terreno nu; a coordenada tem erro de dezenas de metros; o voo é de 2016 e o nivelamento, de 1987–2005. O viés
  negativo é esperado por construção (chapa acima do chão), então −0,36 m é limite, não medida do viés do MDT.
