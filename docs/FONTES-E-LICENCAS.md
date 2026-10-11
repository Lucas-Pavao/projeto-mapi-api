# Fontes de dados e licenças

- Data de acesso às páginas de termos: 2026-10-10
- Escopo: as 11 fontes externas usadas ou previstas pelo MAPi (ver seção de tabela-resumo).
- Método: busca na web e leitura das páginas oficiais de termos. Quando a página oficial não foi
  localizada ou não pôde ser lida (por exemplo, respostas HTTP 403), o termo está marcado como
  "termo não localizado" e a URL onde foi procurado é registrada. Nenhum termo foi inferido.
- Este documento não é parecer jurídico. Os itens da seção "Pendências" precisam de confirmação
  com o órgão responsável antes de uso comercial, redistribuição ou publicação de dados derivados.

## Tabela-resumo

| Fonte | Dado usado pelo MAPi | Licença ou termo | Atribuição exigida | Restrição principal | URL do termo |
|---|---|---|---|---|---|
| PE3D (Governo de Pernambuco, via APAC) | MDT LiDAR 2016, 1 m | Termo não localizado publicamente; há termo de compromisso citado em documento do TJPE | Não localizada | Acesso condicionado a termo de compromisso | Termo não localizado (ver seção 1) |
| MapBiomas | Cobertura e uso do solo (uso futuro, impermeabilização) | CC BY-SA 4.0 | Citação da coleção, data de acesso e link (Souza et al., 2020) | Compartilhar derivados sob a mesma licença | https://sites.mapbiomas.org/conheca-o-mapbiomas/termos-de-uso/ |
| Prefeitura do Recife (CKAN) | Registro de atendimentos da Defesa Civil (rótulos de alagamento) | ODbL | Atribuição à fonte e indicação da licença ODbL (texto oficial não definido na página) | Share-alike para bases derivadas; LGPD se houver endereços | https://dados.recife.pe.gov.br/dataset/registro-de-atendimentos-da-defesa-civil |
| ANA (HidroWeb / Hidro-WebService) | Telemetria hidrológica (nível, vazão, chuva) | Termo da API não localizado; política do app HidroWeb Mobile proíbe cópia sem autorização | Não localizada | Cadastro obrigatório (CPF/CNPJ) para acesso automatizado | Termo não localizado (ver seção 4) |
| APAC | Pluviometria e nível (rede estadual) | Termo não localizado | Não localizada | Não localizada | Termo não localizado (ver seção 5) |
| Cemaden | Pluviômetros | Termo de dados não localizado; conteúdo do site sob CC BY-ND 3.0 BR | Não localizada para os dados; citação usual "CEMADEN" (uso acadêmico) | Cláusula "sem derivações" pode alcançar dados transformados | Termo não localizado (ver seção 6) |
| Open-Meteo | Previsão (forecast), histórico (archive) e marine | CC BY 4.0 para os dados; API gratuita apenas não comercial | Crédito ao Open-Meteo com link para a licença; ECMWF/Copernicus para ERA5 | API gratuita: uso não comercial; limites de chamadas | https://open-meteo.com/en/terms |
| DHN / Centro de Hidrografia da Marinha | Tábuas das marés (previsão) | Termo não localizado | Não localizada | Não localizada | Termo não localizado (ver seção 8) |
| TabuaMare (API de terceiros) | Tábuas das marés reorganizadas a partir dos PDFs da DHN | Código do projeto: MIT. Termos da API não localizados | Não localizada; origem dos dados é a DHN | Dados originais são da Marinha | https://github.com/Ddiidev/tabua_mare_convert_pdf2db |
| CARTO basemaps | Mapa base do front | Termos próprios (última atualização 29/09/2026); chave de API própria | Atribuição a OpenStreetMap e a CARTO | Uso comercial exige plano pago; chave de API própria | https://carto.com/legal/basemap-terms/ |
| OpenStreetMap | Dados do mapa base (tiles) e geocodificação | ODbL | "OpenStreetMap" com link para a página de copyright | Atribuição visível e indicação da ODbL | https://www.openstreetmap.org/copyright |
| Nominatim (OSMF) | Geocodificação de endereços | Política de uso Nominatim | Atribuição ao OpenStreetMap | 1 req/s; User-Agent próprio; cache obrigatório | https://operations.osmfoundation.org/policies/nominatim/ |

## 1. PE3D — Programa Pernambuco Tridimensional

- Órgão: Governo de Pernambuco. O programa foi desenvolvido pela Secretaria de Infraestrutura e Recursos Hídricos (SEINFRA); a disponibilização dos dados é responsabilidade da APAC.
- Dado usado: MDT LiDAR de 2016, resolução de 1 m (ver `mapi-geo-pe3d`).
- Licença ou termo: termo não localizado em página pública. Achado em busca: documento do Tribunal de Justiça de Pernambuco (https://portal.tjpe.jus.br/documents/29010/0/-/5948df8c-aae3-235d-c4f0-3731154bc6c5) que descreve um termo de compromisso para cessão dos dados, com entrega por portal, sigweb ou mídia fornecida pelo solicitante. Esse documento não foi aberto na íntegra; o conteúdo acima vem do resumo da busca.
- Origem: contrato PSHPE nº 001/2014 (Programa de Sustentabilidade Hídrica de Pernambuco, financiado pelo Banco Mundial), segundo estudos acadêmicos da UFPE (busca).
- Atribuição: não localizada. Texto sugerido na seção de atribuição consolidada.
- Restrição: acesso condicionado ao termo de compromisso.
- URL onde se procurou: https://www.apac.pe.gov.br (menus sem conteúdo de dados abertos).

## 2. MapBiomas — coleção anual de cobertura e uso do solo

- Órgão: Projeto MapBiomas (rede colaborativa). Dados publicados também via Google Earth Engine e com crédito ao SEEG.
- Dado usado: cobertura e uso do solo para uso futuro (impermeabilização). Não é usado na versão atual.
- Licença: CC BY-SA 4.0. A página diz: "The MapBiomas data are public, open and free under Creative Commons CC-BY-SA license."
- Atribuição exigida: formato de citação da página: "MapBiomas Project- Collection [version] of the Annual Series of Land Use and Land Cover Maps of Brazil", com data de acesso e link (http://mapbiomas.org). Referência científica: Souza et al. (2020), Remote Sensing, v. 12, n. 17, DOI 10.3390/rs12172735.
- Restrição: derivados sob a mesma licença (SA). A página não traz termos próprios para produtos específicos como impermeabilização; a busca não encontrou termo específico para esse produto.
- URL do termo: https://sites.mapbiomas.org/conheca-o-mapbiomas/termos-de-uso/ (acesso em 2026-10-10).

## 3. Prefeitura do Recife — CKAN de dados abertos (Defesa Civil)

- Órgão: Prefeitura do Recife. Conjunto "Registro de atendimentos da Defesa Civil", com a Secretaria Executiva de Defesa Civil como proprietária, "Defesa Civil" como fonte e autor, e a EMPREL como mantenedora (contato: dadosabertos@recife.pe.gov.br).
- Dado usado: ocorrências de atendimento da Defesa Civil, com rótulos de alagamento (usados como rótulo de treino e validação).
- Licença: ODbL (Open Data Commons Open Database License). A página exibe o selo "Este conjunto de dados satisfaz a Definição de Aberto - Open Definition".
- Atribuição: o conjunto não traz texto de atribuição próprio. Recomendação: citar a Prefeitura do Recife, a Defesa Civil e a licença ODbL (ver seção de atribuição consolidada).
- Restrição: a ODbL exige atribuição e compartilhamento sob a mesma licença para bases derivadas. Registros com endereço podem envolver dados pessoais (LGPD). A busca trouxe relato de 2024 (reportagem não aberta) em que a Defesa Civil recusou inicialmente a lista de endereços de áreas de risco, citando a LGPD. Ver pendências.
- Verificação: a página fetchada é a do conjunto (2021 a 2024 por recurso). A licença por recurso anual deve ser conferida em cada página de recurso.
- URL do termo: https://dados.recife.pe.gov.br/dataset/registro-de-atendimentos-da-defesa-civil (acesso em 2026-10-10).

## 4. ANA — HidroWeb e Hidro-WebService

- Órgão: Agência Nacional de Águas e Saneamento Básico (ANA).
- Dado usado: telemetria hidrológica (nível, vazão e chuva) de estações da rede nacional.
- Termo da API: termo não localizado. O Hidro-WebService substituiu o Telemetria1WS (março de 2023), segundo apresentação da ANA de novembro de 2024 (busca).
- Regras encontradas (não são o termo da API):
  - Acesso automatizado exige cadastro. O pedido deve ser enviado a hidro@ana.gov.br com CPF ou CNPJ e e-mail para a senha. Fonte: resultado de busca sobre a documentação do serviço (https://www.gov.br/ana/pt-br/assuntos/monitoramento-e-eventos-criticos/monitoramento-hidrologico/orientacoes-manuais/manuais/manual-hidrowebservice_publica.pdf). O PDF não pôde ser lido como texto; a informação de contato foi confirmada no próprio PDF.
  - Política de privacidade do app HidroWeb Mobile: proíbe copiar, reproduzir ou alterar dados e conteúdos sem autorização expressa da ANA. Essa política é do aplicativo, não da API: https://www.gov.br/ana/pt-br/todos-os-documentos-do-portal/documentos-sti/politica_de_privacidade_do_aplicativo_hidroweb_mobile_da_ana_v1-0.pdf/@@download/file
- Licença, atribuição e limite de requisições: não localizados.
- Documentação da API: https://www.ana.gov.br/hidrowebservice/swagger-ui/index.html (apontada no PDF; não aberta).

## 5. APAC — Agência Pernambucana de Águas e Clima

- Órgão: Agência Pernambucana de Águas e Clima (APAC). Opera a rede estadual de monitoramento hidrometeorológico (lei estadual, conforme busca).
- Dado usado: pluviometria e nível de rios.
- Termo: termo não localizado. A página inicial do site (https://www.apac.pe.gov.br) mostra apenas menus (SIRH, Boletins Hidrológicos, Monitoramento, Geoportal). Não há página de dados abertos ou termo de uso no conteúdo lido.
- Achado relacionado: acordo de cooperação com o Serviço Geológico do Brasil prevê que a APAC disponibilize dados da rede por FTP ou webservice. Esse acordo é entre instituições, não é termo de uso para o público (busca).
- Atribuição e restrições: não localizadas.
- Observação: resultados de busca que mencionam "termos de uso" para a APAC pertencem a um portal de notícias, não à agência, e foram descartados.

## 6. Cemaden — pluviômetros

- Órgão: Centro Nacional de Monitoramento e Alertas de Desastres Naturais (Cemaden).
- Dado usado: pluviômetros (rede do projeto "Pluviômetros nas Comunidades", entre outras).
- Termo de dados: termo não localizado. A página de acesso à informação (https://www.gov.br/cemaden/pt-br/acesso-a-informacao/acoes-e-programas-1) tem os itens "Dados Abertos" (link para o mapa interativo), "Termos de Uso" e "Termo de Uso e Aviso de Privacidade" apenas como links, sem texto.
- Licença do site: o rodapé diz que todo o conteúdo do site está sob "Creative Commons Atribuição-SemDerivações 3.0 Não Adaptada" (CC BY-ND 3.0 Brasil). Não está claro se isso cobre as séries de dados brutos.
- Citação usual (não exigência oficial): "CEMADEN (2024)", como usada em trabalho do SBRH (busca).
- Restrição: a cláusula "sem derivações" pode alcançar médias, gráficos e bases transformadas. Não confirmado.

## 7. Open-Meteo — previsão, histórico (archive) e marine

- Órgão: Open-Meteo (projeto independente).
- Dados usados: forecast (previsão), archive (histórico, com ERA5 como origem) e marine.
- Termos da API gratuita (https://open-meteo.com/en/terms, acesso em 2026-10-10):
  - "You may only use the free API services for non-commercial purposes."
  - Uso não comercial inclui "private or non-profit websites or apps that do not have subscriptions or advertising". Uso comercial inclui "Operating websites or apps that have subscriptions or display advertisements" e "Integrating our service into commercial products or promotional activities".
  - Limites da API gratuita: 600 chamadas por minuto, 5.000 por hora e 10.000 por dia. A tabela de preços traz 300.000 por mês.
  - Os termos dizem: "The data obtained through the API is provided under the terms of the CC-BY 4.0 licence."
- Licença dos dados: CC BY 4.0. Atribuição sugerida pelo próprio Open-Meteo: "Weather data by" com link para open-meteo.com. Esta frase veio de resultado de busca, porque a página de licença (https://open-meteo.com/en/license) não pôde ser lida na consulta (conteúdo vazio). Confirmar antes de publicar.
- Dados ERA5 (origem do histórico): licença "Licence to Use Copernicus Products" (https://apps.ecmwf.int/datasets/licences/copernicus/, lida). Para distribuição pública: "Generated using Copernicus Climate Change Service information [Ano]". Para produtos adaptados ou modificados: "Contains modified Copernicus Climate Change Service information [Ano]". Também é preciso informar que "neither the European Commission nor ECMWF is responsible for any use made of the information". Não há restrição de uso não comercial nessa licença. Página do ERA5 no CDS: https://cds.climate.copernicus.eu/datasets/reanalysis-era5-single-levels (licença CC-BY; DOI 10.24381/cds.adbb2d47).
- Marine: a página de termos de Open-Meteo é a mesma para todas as APIs. Não foi verificada separadamente a licença de dados marinhos de origem.
- Restrição principal: o uso da API gratuita é restrito a fins não comerciais. Um site público do MAPi sem assinatura nem publicidade se enquadra no uso não comercial; se houver assinatura ou publicidade, é preciso plano pago.

## 8. DHN / Centro de Hidrografia da Marinha — tábuas das marés

- Órgão: Diretoria de Hidrografia e Navegação (DHN), por meio do Centro de Hidrografia da Marinha (CHM).
- Dado usado: previsões de maré (tábuas) para portos do Recife e do litoral.
- Termo de uso: termo não localizado. As páginas oficiais consultadas retornaram HTTP 403 (https://www.marinha.mil.br/cppb/tabuas_de_mare e https://www.marinha.mil.br/chm/dados-do-segmento-hidrografia/tabuas-de-mare). Uma página do CHM encontrada em busca (https://www.marinha.mil.br/chm/node/17624) não foi lida na íntegra.
- Achados de busca (não são termos de uso):
  - As previsões são calculadas no CHM por análise harmônica.
  - As tábuas impressas são vendidas pela EMGEPRON ou por comércio eletrônico de cartas náuticas.
  - O CHM pede que navegantes enviem informações para correção das tábuas.
- Restrição: não localizada. Para uso público e comercial, é preciso confirmar com o CHM.
- **Ficha F-41 do Porto do Recife (versão 1/2010)**, obtida pelo Lucas em 2026-10-11 (SHA-256 73f53922…). É usada só
  pelos números citados em `docs/extratos/f41-porto-recife.md`, e o PDF fica fora do repositório (`MAPI/fontes/dhn/`)
  enquanto o termo da DHN não for localizado. Citação: Marinha do Brasil, DHN/CHM, F-41 — Porto do Recife, estação 30645, v1/2010.
- **IBGE, BDG**: os relatórios das RN 2603F e 393T (e mais 17 RRNN do Recife) são dados públicos do IBGE, com citação.
  As cópias estão em `MAPI/fontes/ibge-bdg/`.

- **Uso no MAPi para 2021–2025 (F2-BACK-14, 2026-10-10):** a API TabuaMare só serve o ano corrente, então o histórico
  2021–2025 do Porto do Recife (carta 902, estação pe02) foi extraído diretamente dos **PDFs oficiais das Tábuas das
  Marés da DHN** por script versionado (`scripts/tabuas-dhn/extrair_tabua_dhn.py`); o SHA-256 de cada PDF fica no CSV
  derivado (`src/main/resources/tide/dhn-pe02-<ano>.csv`). Os PDFs de **2021 e 2022 só foram encontrados no Internet
  Archive** (cópias de páginas oficiais da Marinha); registrar isso como procedência na dissertação. A extração de 2026
  conferiu com a API em 1 407 extremos (diferença máxima 0). Redistribuir os CSVs derivados depende do termo da DHN
  (pendência abaixo).

## 9. TabuaMare — API de terceiros

- Projeto: API de tábuas das marés do litoral brasileiro, que reorganiza em formato consultável os PDFs da DHN.
- Licença do código: MIT (README do repositório https://github.com/Ddiidev/tabua_mare_convert_pdf2db, lido).
- Origem dos dados: o README diz que os dados "são extraídos de documentos PDF oficiais" da Marinha do Brasil (https://www.marinha.mil.br/cppb/tabuas_de_mare).
- Termos da API (tabuamare.devtu.qzz.io): termo não localizado. O README não traz termos de uso da API.
- Consequência: a licença MIT cobre o código do projeto, não os dados. Os dados continuam sendo da DHN, e o termo da DHN (seção 8) é o que vale para o conteúdo.
- Dependência: o MAPi depende de um serviço de terceiros sem termo publicado. Recomenda-se registrar esse risco como pendência, e avaliar consultar a DHN diretamente.

## 10. CARTO basemaps — mapa base do front

- Órgão: CARTO.
- Uso: mapa base do front (MapLibre GL).
- Termos (https://carto.com/legal/basemap-terms/, "Last updated 29 September 2026", lidos em 2026-10-10):
  - Atribuição (cláusula 13.a): "Customer must provide attribution to both OpenStreetMap and CARTO" (detalhes em carto.com/attribution/). A atribuição deve ser "prominent and conspicuous" (13.b).
  - Chave de API (cláusula 8): "Customer must always use the Basemap Services with Customer's own unique API keys." O uso de chave de outro cliente não é permitido (8.c).
  - Uso comercial (cláusula 1.g): uso "in or for a website, application, product or service that is offered for a fee" exige plano pago. Uso "personal, educational, academic, research and non-profit" é não comercial.
  - Limites: cláusula 12.d cita "fair use limit of one million (1,000,000) tile requests each calendar month" para os serviços gratuitos. A cláusula 12.c cita 5.000.000 por mês para uso não comercial gratuito. Os dois números conflitam e precisam ser confirmados com a CARTO.
- Atribuição sugerida: "© OpenStreetMap contributors, © CARTO" (texto sugerido em material de busca; confirmar com carto.com/attribution/).
- Restrição principal: chave de API própria e limite de uso mensal; uso comercial exige plano pago.

## 11. OpenStreetMap e Nominatim

### 11.1 OpenStreetMap (dados do mapa base e geocodificação)

- Órgão: OpenStreetMap Foundation (OSMF).
- Licença: ODbL. A página diz: "OpenStreetMap is open data, licensed under the Open Data Commons Open Database License".
- Atribuição exigida (https://www.openstreetmap.org/copyright, lida): "Provide credit to OpenStreetMap by displaying our attribution notice." e "Make clear that the data is available under the Open Database License."
- Observação: a forma "© OpenStreetMap contributors" aparece em versões antigas da página e em guias de atribuição. O guia de atribuição adotado pela OSMF em 2021 pede a palavra "OpenStreetMap" com link para openstreetmap.org/copyright. Os dois textos são usados na prática; a recomendação deste documento é usar os dois juntos no rodapé.

### 11.2 Nominatim (geocodificação)

- Órgão: OpenStreetMap Foundation.
- Política (https://operations.osmfoundation.org/policies/nominatim/, lida):
  - Limite: "an absolute maximum of 1 request per second". Para tarefas em lote: "restricted to 4 requests per minute" em scripts que rodam por mais de um dia ou em intervalos regulares.
  - User-Agent: "stock User-Agents as set by http libraries will not do". Deve-se identificar a aplicação.
  - Cache: "Results must be cached on your side."
  - Lote: "limit your requests to a single thread" e "limited to 1 machine only, no distributed scripts".
  - Atribuição: "Clearly display attribution as suitable for your medium."
  - Uso intenso: a política indica instalar instância própria. O descumprimento pode levar à retirada do acesso ("you might have your access withdrawn").

## Texto de atribuição consolidado

### Para o rodapé do front

```
Mapa base © OpenStreetMap contributors (ODbL) e © CARTO. Previsão do tempo: Open-Meteo.com (CC BY 4.0), dados ERA5 contendo informação modificada do Copernicus Climate Change Service [ano]. Geocodificação: Nominatim/OpenStreetMap. Dados de alagamento: Prefeitura do Recife, Defesa Civil (ODbL). Maré: DHN/Marinha do Brasil (via TabuaMare). Pluviometria e nível: APAC, ANA e Cemaden [a confirmar].
```

Notas para o rodapé:
- O texto acima é uma proposta. Os itens marcados "a confirmar" dependem das pendências.
- Para o ERA5, o texto oficial é "Contains modified Copernicus Climate Change Service information [Year]" (adaptado) ou "Generated using ..." (sem adaptação). Neither the European Commission nor ECMWF is responsible for any use that may be made of the information. Essa frase precisa aparecer em publicação.
- O link para openstreetmap.org/copyright e para open-meteo.com deve estar no próprio rodapé.

### Para a dissertação (seção de fontes)

Formato sugerido, um item por fonte:

- MapBiomas Project. Collection [versão] of the Annual Series of Land Use and Land Cover Maps of Brazil. Acesso em 2026-10-10: https://mapbiomas.org. Licença CC BY-SA 4.0. Referência: Souza et al. (2020), Remote Sensing, 12(17), DOI 10.3390/rs12172735.
- Prefeitura do Recife. Registro de atendimentos da Defesa Civil. Portal de Dados Abertos do Recife, licença ODbL. Acesso em 2026-10-10: https://dados.recife.pe.gov.br/dataset/registro-de-atendimentos-da-defesa-civil.
- Open-Meteo.com. Historical Weather API e Forecast API. Licença CC BY 4.0. Acesso em 2026-10-10: https://open-meteo.com/en/terms.
- Copernicus Climate Change Service (C3S). ERA5 reanalysis (single levels). DOI 10.24381/cds.adbb2d47. Licença: Licence to Use Copernicus Products. Acesso em 2026-10-10.
- OpenStreetMap contributors. Licença ODbL. Acesso em 2026-10-10: https://www.openstreetmap.org/copyright.
- CARTO. Basemap Terms. Acesso em 2026-10-10: https://carto.com/legal/basemap-terms/.
- Marinha do Brasil, Diretoria de Hidrografia e Navegação. Tábuas das Marés. Acesso em 2026-10-10. Termo de uso: pendente (ver seção 8).
- Governo de Pernambuco. Programa Pernambuco Tridimensional (PE3D), MDT LiDAR 2016, 1 m. Acesso em 2026-10-10. Termo: pendente (ver seção 1).

## Pendências

1. PE3D: termo de uso não localizado em página pública. Confirmar com a APAC o termo de compromisso vigente e as condições de citação e redistribuição (incluindo derivados como a camada de terreno do mapa).
2. MapBiomas: licença confirmada (CC BY-SA 4.0). Antes de usar a camada de impermeabilização, conferir a versão da coleção e se há termo específico do produto.
3. Prefeitura do Recife: licença ODbL confirmada no conjunto. Conferir a licença de cada recurso anual. Confirmar com a EMPREL a forma de atribuição oficial. Avaliar a LGPD se os registros tiverem endereço: o relato de 2024 (busca, não aberta) indica recusa inicial da Defesa Civil em compartilhar endereços de áreas de risco, citando a LGPD. Usar apenas dados sem identificação pessoal, ou pedir parecer da Prefeitura.
4. ANA: termo de uso da API Hidro-WebService não localizado. Confirmar com hidro@ana.gov.br a permissão de redistribuição, de uso em site público e de uso comercial, e o limite de requisições.
5. APAC: termo de uso e licença dos dados de pluviometria e nível não localizados. Confirmar com a APAC por escrito. Consultar o termo de compromisso do PE3D (ver item 1).
6. Cemaden: termo de dados não localizado. Confirmar com o Cemaden (Fale Conosco ou SIC) se a licença CC BY-ND 3.0 BR cobre as séries de dados e se derivados (médias, gráficos, bases transformadas) são permitidos.
7. Open-Meteo: a página de licença não pôde ser lida (conteúdo vazio). Confirmar a frase de atribuição em https://open-meteo.com/en/license. Confirmar que o uso do MAPi (site sem assinatura nem publicidade) é não comercial. Se houver assinatura, publicidade ou produto comercial, contratar plano pago.
8. DHN / CHM: páginas oficiais retornaram HTTP 403. Confirmar com o CHM o termo de uso e reutilização das tábuas e a responsabilidade pelas previsões — inclusive se os CSVs extraídos dos PDFs 2021–2026 podem ficar no repositório (o pedido `docs/pedidos/01-…` pode incluir essa pergunta).
9. TabuaMare: termo da API não localizado. O código é MIT, mas os dados são da DHN. Registrar como risco: dependência de serviço de terceiros sem termo publicado; avaliar consumir as tábuas da DHN diretamente em produção.
10. CARTO: os limites de uso gratuito conflitam entre as cláusulas 12.c (5.000.000 por mês) e 12.d (1.000.000 por mês). Confirmar com a CARTO e registrar a chave de API própria no ambiente de produção.
11. OpenStreetMap: a forma exata de atribuição muda entre a página de copyright e o guia de 2021. Escolher uma forma e aplicar de forma uniforme no front e na dissertação.
12. Nominatim: a política foi lida. Confirmar a taxa efetiva do MAPi (1 req/s) e a existência de cache local antes de qualquer carga de geocodificação em lote.
