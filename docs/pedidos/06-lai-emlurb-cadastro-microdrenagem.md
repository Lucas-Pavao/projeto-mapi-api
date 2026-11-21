**Canal oficial:** Portal da Transparência da Prefeitura do Recife, formulário "Pedido de Acesso à Informação" (Lei 12.527/2011 e Lei municipal 17.866/2013) — https://transparencia.recife.pe.gov.br/codigos/web/lai/pedidoInformacaoForm.php (acessado em 2026-10-11; o formulário pode exigir cadastro/identificação do requerente e permite escolher o órgão destinatário). Acompanhamento do pedido pela mesma plataforma ("Consulta de pedido").
**Para:** EMLURB (Empresa de Manutenção e Limpeza Urbana), mantenedora da base `Emlurb/BASES_DRENAGEM_RECIFE_MI` no ESIG. [se o formulário indicar outro órgão (URB Recife, Secretaria de Infraestrutura, SESAN), trocar o destinatário e manter o texto]
**Assunto:** Exportação da base de microdrenagem do ESIG (Contrato 6016/2012), produtos 6 e 10 do PDDR e dados de bombas e comportas — pesquisa acadêmica

> Atualizado em 2026-11-21 (prompt 1, tarefa 4) com o que se viu no serviço público do ESIG
> (`docs/extratos/esig-drenagem/RELATORIO-PREENCHIMENTO.md`). A versão de 2026-10-11 pedia o cadastro sem saber que
> ele estava publicado; agora o pedido nomeia a base e pede o que falta nela.

Prezados,

Com base na Lei de Acesso à Informação (Lei 12.527/2011), solicito as informações abaixo sobre a drenagem do Recife.

Sou [nome completo], mestrando(a) em [programa], [instituição]. Desenvolvo um sistema de previsão de risco de alagamento para o Recife. Os dados serão usados para estimar a capacidade de escoamento da drenagem em quatro pontos de alagamento, e o resultado será citado na dissertação com a fonte.

### 1. Exportação completa da base de microdrenagem do ESIG

A base "B.D.E. da gestão e manejo de águas pluviais e drenagem urbana do Recife — Contrato Número 6016/2012" está publicada no ESIG como serviço de mapa: `https://esigportal2.recife.pe.gov.br/arcgis/rest/services/Emlurb/BASES_DRENAGEM_RECIFE_MI/MapServer` (camadas 0 "Elementos da Rede", 1 "Rede" e 2 "Canaleta") e `.../Emlurb/BASES_DRENAGEM_RECIFE_MA/MapServer` (macrodrenagem). Solicito:

1. a **exportação completa** das camadas, com todos os atributos, em arquivo aberto (GeoPackage, Shapefile ou CSV com coordenadas em SIRGAS 2000 / UTM 25S);
2. o **dicionário de dados**: significado e unidade de cada campo, em especial `Z`, `VLHFUNDO` ("Altura de Fundo"), `VLTBMONT1..3` ("Tubo Montante"), `VLTBJUSAN1..2` ("Tubo Jusante"), `COTA_FUNDO`, `COTA_MONTANTE_01..03`, `COTA_JUSANTE` e `QTD_CONEXÕES`; a referência vertical das cotas (datum e RN usada); e se os tubos são todos circulares;
3. a **ligação entre trechos e nós**: na camada 1 ("Rede") cada trecho só tem tipo e comprimento. Peço a tabela que diz de qual elemento (poço, caixa ou boca de lobo) cada trecho sai e para qual vai, e o diâmetro, o material e o sentido de escoamento de cada trecho, se existirem em outra tabela;
4. as **atualizações posteriores a 2012**: o serviço traz datas de levantamento de 2018 a 2024. Peço a indicação das áreas levantadas depois do contrato, por qual contrato ou equipe, e se há levantamentos ainda não carregados no ESIG.

**Campos vazios no serviço público** (conferido em 2026-11-21 nos 175.631 elementos da camada 0 e nos entornos de 300 m dos quatro pontos). Se esses dados existirem fora do ESIG (planilha de campo, projeto, vídeo inspeção), peço que sejam entregues:

| Campo | Preenchido na cidade | Nos quatro pontos |
|---|---|---|
| `COTA_MONTANTE_01` | 51 de 175.631 | 0 de 629 |
| `COTA_JUSANTE` | 32 de 175.631 | 0 de 629 |
| `COTA_FUNDO` | 4.025 de 175.631 | 38 de 629 |
| `VLTBJUSAN1` (tubo de jusante) | 17.162 de 175.631 | 0 de 37 no CIn-UFPE; 15 de 51 na Imbiribeira |
| `VLHFUNDO` (altura de fundo) | 18.746 de 175.631 | 0 de 37 no CIn-UFPE |
| `COD_MICROBACIA` | 0 | 0 |
| `TIPO_TAMPA`, `ESTADO_CONSERVAÇÃO_TAMPA`, `TIPO_MEIO_FIO`, `CÓDIGO` | até 11 | 0 |
| Bocas de lobo (`TIPO = BL`) a até 300 m da Av. Mascarenhas de Morais (Imbiribeira) | — | nenhuma cadastrada; peço confirmar se a área não foi levantada |

Para as bocas de lobo, peço também o **tipo** (simples na guia, com grelha, combinada) e as **dimensões** da abertura, que não existem como campo no serviço.

### 2. Produtos do Plano Diretor de Drenagem do Recife (PDDR)

5. **Produto 6 — Álbum de Cadastro** da rede de drenagem;
6. **Produto 10 — Manual de Drenagem e Manejo das Águas Pluviais (2016)**, com os **critérios de dimensionamento** da microdrenagem: período de retorno, equação de chuva (IDF), tempo de concentração, coeficiente de escoamento, coeficiente de Manning, lâmina máxima na via e capacidade de engolimento de boca de lobo adotada.

### 3. Declividade

7. A **declividade cadastrada ou de projeto** de cada trecho de galeria nos entornos dos quatro pontos. Se não houver, peço as cotas de fundo de montante e de jusante de cada trecho, ou os projetos (planta e perfil).

### 4. Bombas e comportas

8. **Estação de bombeamento da Av. Recife (Ibura):** número de bombas, capacidade nominal de cada uma (m³/s ou L/s), cotas de liga e desliga (cotas de operação) e referência vertical dessas cotas; regra de operação, se houver.
9. **Comportas do Canal Derby–Tacaruna:** localização, cota da soleira, regra de abertura e fechamento com a maré e referência vertical.

### Pontos de interesse (para entrega parcial)

Se não for possível entregar a cidade inteira no prazo, peço **primeiro** os dados das bacias de drenagem destes quatro pontos (raio de 300 m, ou a microbacia que os contém):

1. **Derby** — Av. Agamenon Magalhães, próximo ao Derby (lat −8,052554; lon −34,894371)
2. **Imbiribeira** — Av. Mascarenhas de Morais (lat −8,118123; lon −34,904945)
3. **Ibura** — Av. Recife, entrada do Ibura (lat −8,107910; lon −34,927138)
4. **CIn-UFPE** — entorno do Centro de Informática, Cidade Universitária (lat −8,055310; lon −34,951160)

**Formato:** arquivo aberto (art. 8º, §3º, da Lei 12.527/2011): GeoPackage, Shapefile ou CSV (UTF-8) para a base; PDF pesquisável para os produtos do PDDR. Planta em PDF só como complemento.

**Dados pessoais:** não peço nenhum. Nomes de contratada ou de responsável técnico podem permanecer, porque são informações de órgão público.

**Se não for possível entregar tudo:** peço o máximo disponível e a indicação dos campos e trechos que não estão cadastrados, do motivo (art. 11 da LAI) e da data do último levantamento, em vez de negativa total.

**Prazo:** 20 dias, prorrogáveis por mais 10 (art. 11, §§1º e 2º). Em caso de negativa, solicito a indicação da autoridade competente para o recurso.

Atenciosamente,
[nome completo]
[CPF/identificação exigida pelo formulário]
[programa / instituição]
[e-mail] · [telefone]
