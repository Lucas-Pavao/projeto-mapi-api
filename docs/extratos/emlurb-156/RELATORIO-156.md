# Central 156 da EMLURB: o que a base tem, antes de qualquer conta

Prompt 3, tarefa 1. Nenhuma taxa (λ, μ) foi calculada e nenhum chamado foi ligado aos pontos. Nenhum dado de alagamento foi aberto.

- Conjunto: "Central de Atendimento de Serviços da Emlurb – 156", https://dados.recife.pe.gov.br/dataset/central-de-atendimento-de-servicos-da-emlurb-156
  (licença **ODbL**; responsável EMLURB; publicação Emprel; `package_show.json`, acesso em 2026-11-21).
- Arquivos: 15 CSV anuais (2012–2026), 290 MB brutos, guardados comprimidos fora do git em `MAPI/fontes/emlurb-156/<ano>.csv.gz`;
  SHA-256 de cada CSV bruto em `MANIFESTO.csv`. O CKAN tem recursos repetidos para 2012, 2014–2017, 2019, 2022, 2024 e 2025;
  baixei o mais recente de cada ano (o de 2025 mais recente tem 18,3 MB; o outro, 14,8 MB, ficou listado e não baixado).
- Dicionário de dados (PDF e JSON) versionado aqui. Reproduzir: `python3 -I scripts/central-156/baixa_156.py <pasta>` (rede) e
  `python3 -I scripts/central-156/relatorio_156.py <pasta>` (sem rede).
- Conferência com o que já estava no repositório: a contagem de 2022 bate com `docs/REVISAO-INGESTAO-2026-11.md` (limpeza de
  galeria 5.040, abatimento 3.290, tampa 2.576, canaleta 716, canal 550).

## 1. Colunas e formato

Todos os anos: UTF-8 com BOM, separador `;`. Colunas (dicionário JSON):

| Coluna | Dicionário | Observação |
|---|---|---|
| `GRUPOSERVICO_CODIGO`, `GRUPOSERVICO_DESCRICAO` | grupo de serviço | o código do grupo muda: drenagem é grupo 2 (CONSERVACAO) em 2012, 11 de 2012 a 2023 e 27 de 2024 em diante |
| `SERVICO_CODIGO`, `SERVICO_DESCRICAO` | serviço | a grafia muda entre anos (acentos perdidos ou trocados por `�`); por isso as contagens abaixo são por código |
| `LOGRADOURO`, `NUMERO`, `BAIRRO`, `RPA` | endereço da reclamação | número vazio em até 17% (2023) |
| `DATA_DEMANDA` | "Data de Cadastro" | **só data, sem hora** |
| `SITUACAO` | situação atual | CADASTRADA, PREPARAÇÃO, EXECUÇÃO, FISCALIZAÇÃO, PENDENTE, ATENDIDA |
| `DATA_ULT_SITUACAO` | "Data que ocorreu a última movimentação" | **não existe data de conclusão**; para ATENDIDA, é a data em que virou atendida |
| `latitude`, `longitude` | (não estão no dicionário) | só de 2015 em diante; vazias em 2012–2014 |

Granularidade (dicionário): "por chamados ao atendimento 156, contendo o último status". **Não há identificador do chamado.**
Linhas idênticas não se distinguem. O histórico de situações não existe: só a última.

## 2. Serviços do grupo drenagem, cidade inteira, por ano

Fonte: `servicos_drenagem_por_codigo.csv` (critério: grupo com "DRENAGEM" ou serviço com GAL, CANALETA, CANAL, SARJETA, VALETA ou TAMPA).

| Grupo/código | Serviço | 2012 | 2013 | 2014 | 2015 | 2016 | 2017 | 2018 | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 | 2026* | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2/4, 11/4, 27/8 | Limpeza de galerias, valetas e sarjetas | 3.615 | 5.794 | 4.069 | 3.609 | 3.648 | 4.917 | 5.213 | 5.414 | 4.300 | 5.586 | 5.040 | 4.966 | 7.715 | 5.633 | 2.276 | 71.795 |
| 2/81, 11/81, 27/157 | Limpeza de canaleta | 942 | 875 | 963 | 970 | 902 | 857 | 391 | 538 | 725 | 715 | 716 | 794 | 1.283 | 1.050 | 469 | 12.190 |
| 2/72, 11/72, 27/141 | Recuperação de abatimento de galeria | 1.509 | 1.957 | 1.510 | 994 | 1.434 | 1.596 | 1.902 | 2.169 | 1.449 | 2.865 | 3.290 | 2.540 | 1.090 | 1.554 | 738 | 26.597 |
| 2/18, 11/18, 27/41 | Reposição de tampa de poço, galeria e pré-moldado | 1.814 | 2.618 | 2.586 | 2.391 | 2.049 | 2.026 | 2.140 | 2.250 | 1.708 | 3.087 | 2.576 | 3.083 | 2.222 | 2.912 | 1.119 | 34.581 |
| 2/97, 11/97, 27/189 | Recuperação de canaleta de drenagem | 92 | 189 | 255 | 252 | 223 | 339 | 273 | 282 | 226 | 347 | 358 | 1.085 | 1.287 | 1.611 | 627 | 7.446 |
| 2/11, 22/11, 16/26 | Limpeza de canal (macrodrenagem) | 323 | 412 | 463 | 648 | 564 | 573 | 551 | 541 | 628 | 691 | 550 | 966 | 876 | 1.080 | 449 | 9.315 |
| 2/82, 12/82, 8/159, 8/116 | Canaleta de escadaria (encosta) | 98 | 174 | 110 | 114 | 73 | 176 | 192 | 277 | 244 | 290 | 180 | 302 | 2.513 | 1.255 | 1.510 | 7.508 |

\* 2026 até a última atualização do CKAN (recurso de 2026-06-19). Em 2012 a drenagem aparece quase toda no grupo 2 (CONSERVACAO); o grupo 11 só tem 448 registros naquele ano.

**Não existe** serviço chamado "boca de lobo", "desobstrução" ou "bueiro". A boca de lobo entupida entra, ao que tudo indica, em
"limpeza de galerias, valetas e sarjetas" junto com galeria e sarjeta. Isso impede separar o entupimento da boca de lobo do resto.

**Alagamento:** nenhum grupo nem serviço tem "ALAGA" ou "INUNDA" na descrição, em nenhum ano. O arquivo lacrado
(`lacrado/ALAGAMENTO_IDS.txt`) foi gerado **vazio** (0 linhas; SHA-256 da lista em `lacrado/ALAGAMENTO_IDS.sha256`). A 156 não
mistura rótulo de alagamento com manutenção, e a separação de dados do prompt 3 fica garantida sem lacrar nada.

## 3. Datas, situação e coordenada (grupos de drenagem, critério amplo)

Fonte: `qualidade_drenagem_por_ano.csv`.

| Ano | Registros | Com coordenada | Situação final ATENDIDA | Última movimentação depois da abertura |
|---|---|---|---|---|
| 2012 | 8.393 | 0% | 78% | 60% |
| 2013 | 12.019 | 0% | 69% | 48% |
| 2014 | 9.956 | 0% | 57% | 28% |
| 2015 | 8.978 | 79% | 42% | 13% |
| 2016 | 8.893 | 75% | 47% | 20% |
| 2017 | 10.484 | 75% | 39% | 20% |
| 2018 | 10.662 | 76% | 42% | 20% |
| 2019 | 11.471 | 77% | 36% | 16% |
| 2020 | 9.280 | 82% | 22% | 10% |
| 2021 | 13.581 | 84% | 21% | 14% |
| 2022 | 12.710 | 85% | 23% | 13% |
| 2023 | 13.736 | 86% | 30% | 13% |
| 2024 | 16.986 | 18% | 38% | 52% |
| 2025 | 15.095 | 56% | 36% | 39% |
| 2026 | 7.188 | 58% | 34% | 29% |

## 4. O que isso significa (sem conta ainda)

1. **Frequência (λ):** viável por bairro e, de 2015 a 2023, por coordenada (75–86% com lat/lon). Em 2012–2014 só por logradouro e
   bairro; em 2024 só 18% com coordenada. O chamado é entupimento **percebido e reclamado**: limite inferior de λ.
2. **Tempo de atendimento (μ):** fraco. Não há data de conclusão nem histórico. Em 2020–2023 só 21–30% dos chamados terminam
   ATENDIDA; o resto fica CADASTRADA (sem movimentação registrada). Para os ATENDIDA, `DATA_ULT_SITUACAO − DATA_DEMANDA` é o
   tempo até virar atendida, com fila e burocracia, em dias inteiros. Chamado que nunca foi fechado é dado censurado, e precisa
   entrar como tal (não como zero nem descartado), senão μ sai otimista.
3. **Sem hora:** relação com "dias secos antes da chuva" só em escala diária.
4. **Sem id:** casamento chamado–ponto e o lacre usam o hash da linha.

## 5. Decisão que preciso de você: quais serviços contam como "entupimento"

Proposta (para aprovar, mudar ou recusar):

| Serviço | Proposta | Por quê |
|---|---|---|
| Limpeza de galerias, valetas e sarjetas (2/4, 11/4, 27/8) | **conta** | é o pedido de desobstrução; inclui boca de lobo, sem separação |
| Limpeza de canaleta (2/81, 11/81, 27/157) | **conta** | canaleta obstruída tira capacidade de entrada |
| Recuperação de abatimento de galeria (2/72, 11/72, 27/141) | **não conta como entupimento**; registrar à parte como "falha estrutural" | é colapso da estrutura; pode obstruir, mas tem outra causa e outro reparo |
| Reposição de tampa (2/18, 11/18, 27/41) | não conta | tampa quebrada é risco a pessoa, não perda de capacidade |
| Recuperação de canaleta de drenagem (2/97, 11/97, 27/189) | não conta (à parte, como "falha estrutural") | reparo de estrutura |
| Limpeza de canal (macrodrenagem) | não conta | é o canal, não a galeria do ponto; pode virar covariável do nível lá fora |
| Canaleta de escadaria | não conta | encosta, fora dos 4 pontos |

Depois da sua resposta: regra de casamento chamado–ponto escrita antes (raio para coordenada; logradouro e bairro normalizados),
taxa de casamento, λ por ponto e por bairro, tempo até ATENDIDA com censura, sazonalidade e relação com dias secos.
