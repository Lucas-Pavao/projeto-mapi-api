# O que ainda depende de buscar informação fora do repositório

- Data: 2026-10-11 · Tarefa: F5-ARQ-02 · Autor: Arquiteto (nível Máximo) · Para: Lucas
- Fontes: `docs/pedidos/*.md`, `.claude/mapi/PROGRESSO.md` ("Bloqueios abertos" e pergunta 31), `docs/FONTES-E-LICENCAS.md`
  ("Pendências"), `docs/PRONTIDAO-ACADEMICA.md` §5.2, ADRs 0008 e 0009, `docs/PRE-REGISTRO-AVALIACAO.md` §10,
  `docs/INTERPRETACAO-F1.md` §4 e §6.
- Este documento junta, em linguagem direta, tudo o que o código não resolve sozinho porque a informação está com outra
  pessoa ou outro órgão. Para a ordem do trabalho técnico, veja `docs/ROADMAP-REVISAO.md`.

## 1. O que são os arquivos de `docs/pedidos/`

São **rascunhos** de solicitações oficiais: e-mails ou ofícios a órgãos técnicos (CHM/DHN, IBGE, PE3D, Defesa Civil) e
pedidos pela Lei de Acesso à Informação (LAI) no Portal da Transparência do Recife. O sistema **não envia nada**.

Para cada um, você:

1. lê e ajusta o texto;
2. preenche os campos entre colchetes (`[nome completo]`, `[programa]`, `[instituição]`, `[título]`, `[e-mail]`,
   `[CPF/identificação exigida pelo formulário]`, `[data do envio]` etc.);
3. envia pelo canal indicado no próprio arquivo, em seu nome;
4. anota a data de envio e o número de protocolo. Hoje o repositório não registra o envio de nenhum pedido
   (`docs/pedidos/README.md`: "Nenhum foi enviado"; PRONTIDAO §5.2).

Quando a resposta chegar, guarde o PDF ou a planilha em `docs/extratos/` e avise o Arquiteto para atualizar o ADR ou o
documento afetado (`docs/pedidos/README.md`, último parágrafo). Nos pedidos por LAI, o prazo legal é de 20 dias,
prorrogáveis por mais 10 (pedidos 05 e 06).

## 2. Resumo

| # | O que falta | Com quem | Pedido | O que trava | Prioridade |
|---|---|---|---|---|---|
| 1 | Ficha F-41 da estação do Porto do Recife e autorização para reutilizar as tábuas | CHM/DHN (Marinha) | 01 | Δ entre maré e terreno; publicar a maré derivada | alta |
| 2 | Altitude da RN do marégrafo e nível médio do mar no SGB | IBGE, Coordenação de Geodésia | 02 | Δ entre maré e terreno | alta |
| 3 | Relatório técnico do MDT (datum, geoide, precisão) e termo de uso | Governo de PE (PE3D / APAC) | 03 | Δ; publicar a camada de terreno | alta |
| 4 | Quantos falsos alarmes por mês a Defesa Civil tolera | Defesa Civil do Recife | 04 | Limiares de Moderado e Alto | média |
| 5 | Registros de alagamento com data, hora e endereço | Defesa Civil do Recife, via LAI | 05 | Validação com mais casos e com hora | alta |
| 6 | Cadastro da microdrenagem nos 4 pontos | EMLURB / URB Recife, via LAI | 06 | Capacidade da galeria e soleira com fonte | média |
| 7 | Termos de uso das demais fontes | ANA, APAC, Cemaden, Open-Meteo, CARTO, Prefeitura | não há | Publicar dados e o site | média |
| 8 | Credencial do Hidro-WebService da ANA | ANA | não há | Dado da ANA ao vivo | baixa |
| 9 | Pluviômetro urbano com hora confiável em 2023–2024 | Cemaden, APAC ou ANA | não há | Um teste com poder | média |
| 10 | Confirmação de que a rede de Petri vai exportar no formato combinado | você | não há | Voltar a avaliar a Petri | baixa agora |
| 11 | Onde guardar a cópia dos dumps | você | não há | Proteção contra perda dos dados | **urgente** |
| 12 | Teste da camada de terreno em celular real | você (ou alguém com um celular comum) | não há | Fechar o critério do ADR 0008 | média |
| 13 | Sua confirmação escrita e a leitura do orientador nos pré-registros e no MEMO | você e o orientador | não há | Validade do protocolo diante da banca | alta |
| 14 | Polígonos oficiais de bairro | Prefeitura do Recife | não há | Análise secundária por bairro | baixa |
| 15 | Qual modelo de reanálise o Open-Meteo usou em cada período | documentação do Open-Meteo | não há | Uma ameaça à validade citada no texto | média |

## 3. Pedidos redigidos

### 3.1 Ficha F-41 e reutilização das tábuas de maré (pedido 01)

- **O que falta:** a ficha F-41 da estação maregráfica do Porto do Recife, com as referências de nível (RN) e a altura de
  cada uma acima do Nível de Redução (NR); o nível médio do mar acima do NR e o período que o originou (a tábua indica
  1,28 m); o vínculo da RN com o Sistema Geodésico Brasileiro, se houver. Também: se você pode publicar, num repositório
  acadêmico aberto, os valores das tábuas de 2021 a 2026 que o projeto extraiu dos PDFs da DHN.
- **Com quem:** Centro de Hidrografia da Marinha (CHM), Diretoria de Hidrografia e Navegação, Divisão de Marés. A página do
  CHM deu erro HTTP 403 em 2026-10-10, por isso o pedido vai por contato direto.
- **Arquivo:** `docs/pedidos/01-chm-dhn-ficha-f41-recife.md`. Pede a F-41 (ou os três itens acima) e pergunta sobre a
  reutilização das tábuas.
- **Por que é necessário:** a maré vem referida ao NR da DHN; a cota da rua vem do MDT do PE3D, referida a outro plano.
  Sem o desnível Δ entre os dois, o projeto não pode comparar "nível do mar lá fora" com "cota da rua". O ADR 0009 proíbe
  essa comparação até existir um Δ com fonte primária. Hoje a estimativa de trabalho é Δ ≈ +1,04 ± 0,3 m, que o próprio
  handoff chama de inutilizável (HANDOFF-fase-3).
- **Para que serve:** na dissertação, permite testar o refluxo de maré (variante M1b), que hoje está fora. No sistema,
  libera a camada "abaixo da maré" e as regras de folga até a soleira. A parte da reutilização decide se os CSVs de maré
  podem ir para o repositório público (FONTES-E-LICENCAS, pendência 8).
- **Se nunca chegar:** a dissertação trabalha só com cotas relativas e declara o refluxo de maré como fora do alcance; os
  CSVs de maré ficam fora do repositório público, só com o hash dos PDFs. Os pedidos 02 e 03 ainda podem fechar Δ pelo
  outro lado da cadeia (ADR 0009, opção B).
- **Primeiro passo:** preencher os colchetes e enviar ao e-mail de atendimento do CHM; se não encontrar o e-mail, abrir
  pedido pelo Fala.BR (canal federal de LAI) endereçado à Marinha.

### 3.2 RN do marégrafo e nível médio do mar no SGB (pedido 02)

- **O que falta:** a altitude, no Sistema Geodésico Brasileiro (Datum Imbituba, REALT 2018), da RN ligada ao marégrafo do
  Porto do Recife; o nível médio do mar local referido ao SGB; qualquer nivelamento que ligue o NR da DHN ao SGB.
- **Com quem:** IBGE, Diretoria de Geociências, Coordenação de Geodésia (CGED), atendimento do Banco de Dados Geodésicos.
- **Arquivo:** `docs/pedidos/02-ibge-rn-maregrafo-nmm-recife.md`. Cita que a RN 393Y (Madalena) já consultada não está
  ligada ao marégrafo.
- **Por que é necessário:** é a outra ponta da mesma cadeia do Δ (ADR 0009, passo 2; `docs/pedidos/README.md`, "item E da
  cadeia"). Com a F-41 (pedido 01) e esta altitude, o Δ fecha com fonte primária.
- **Para que serve:** o mesmo do item 3.1: refluxo de maré na dissertação e camadas de maré no sistema.
- **Se nunca chegar:** igual ao item 3.1. O ADR 0009 também previa nivelamento GNSS como alternativa, mas isso exige
  campo e equipamento e não está planejado.
- **Primeiro passo:** preencher e enviar ao atendimento do IBGE (canal "Fale com o IBGE" ou e-mail da CGED).

### 3.3 Relatório técnico do MDT e termo de uso do PE3D (pedido 03)

- **O que falta:** o datum vertical das cotas do MDT de 2016 (altitude ortométrica em Imbituba? qual realização? ou altura
  elipsoidal?), o modelo geoidal usado (MAPGEO2010 ou 2015), a precisão vertical, as datas de voo das folhas do Recife e o
  termo de uso, com a citação recomendada.
- **Com quem:** Governo de Pernambuco, Programa Pernambuco Tridimensional (PE3D). A disponibilização dos dados é da APAC
  (FONTES-E-LICENCAS §1). O campo `[secretaria responsável]` precisa ser confirmado.
- **Arquivo:** `docs/pedidos/03-pe3d-relatorio-tecnico-mdt.md`. Pede os 5 itens acima; os GeoTIFFs só informam a
  projeção (SIRGAS 2000 / UTM 25S).
- **Por que é necessário:** se o MDT não estiver no SGB, qualquer Δ calculado erra em metros (`docs/pedidos/README.md`).
  E sem termo de uso não se sabe se a camada de terreno (derivada do MDT) pode ir para um site público.
- **Para que serve:** na dissertação, documentar a referência das cotas usadas no balde e citar a fonte corretamente. No
  sistema, permitir publicar a camada de terreno fora da sua máquina.
- **Se nunca chegar:** a legenda continua "datum não confirmado — cotas relativas"; a camada fica só em uso local e
  acadêmico; os zips do PE3D não são redistribuídos (PRONTIDAO §1.1).
- **Primeiro passo:** confirmar na página da APAC ou da SEINFRA a secretaria responsável e enviar o pedido; em paralelo,
  pedir o termo de compromisso de cessão citado num documento do TJPE (FONTES-E-LICENCAS §1).

### 3.4 Tolerância a falso alarme (pedido 04)

- **O que falta:** para os níveis Alto (mobilização) e Moderado (prontidão), quantos alertas falsos por ponto e por mês a
  equipe aceita antes de deixar de confiar no aviso; quantas horas de antecedência são úteis; o que custa mais, um
  alagamento não avisado ou um deslocamento à toa.
- **Com quem:** Defesa Civil do Recife, setor de monitoramento ou de gestão de risco (`[setor]` a confirmar).
- **Arquivo:** `docs/pedidos/04-defesa-civil-tolerancia-falso-alarme.md`. Diz que uma resposta aproximada já basta e que
  será citada como critério definido com o usuário final.
- **Por que é necessário:** o projeto decidiu não escolher os limiares de Moderado e Alto por conta própria
  (PROGRESSO, decisão de 2026-10-10; MODELAGEM-IA, decisão 6). Bloqueio F4-PED-01.
- **Para que serve:** no sistema, transformar a probabilidade em classe com um critério do usuário. Na dissertação, é o
  critério de utilidade operacional, defensável na banca.
- **Se nunca chegar:** reporta-se a curva inteira (precisão × recall), sem limiar operacional, e declara-se a ausência.
  Não há prejuízo ao resultado confirmatório, que não usa esses limiares.
- **Primeiro passo:** perguntar por telefone à Defesa Civil quem responde por monitoramento e mandar o texto por e-mail a
  essa pessoa.

### 3.5 Registros de alagamento com hora e endereço (pedido 05, LAI)

- **O que falta:** os registros de alagamento de 01/01/2021 até a data do envio, um por linha, com data e hora de abertura,
  endereço ou coordenada, bairro, tipo e código Cobrade, origem do chamado e, se houver, lâmina d'água, duração e danos.
  Sem dados pessoais.
- **Com quem:** Defesa Civil do Recife, pelo formulário "Pedido de Acesso à Informação" do Portal da Transparência
  (endereço no próprio arquivo). O nome exato do órgão no formulário precisa ser confirmado.
- **Arquivo:** `docs/pedidos/05-lai-defesa-civil-registros-alagamento.md`. Pede CSV ou planilha, aceita coordenada
  arredondada se o endereço for tratado como dado pessoal e pede entrega parcial em vez de negativa total.
- **Por que é necessário:** o registro público (CKAN) tem só 18 dias com alagamento em 5 anos, todos sem hora (PROGRESSO,
  F2-BACK-18). Os 14 dias de 2023–2024 já foram gastos na 1ª análise. Um teste com poder precisa de ≥ 43 episódios
  independentes e de rótulo com hora e lugar (INTERPRETACAO §4.2). Bloqueio F4-PED-03.
- **Para que serve:** na dissertação, um conjunto de teste novo e separado (por exemplo, 2025–2026), casos documentados de
  "não alagou" e uma auditoria do rótulo atual. Com ≥ 43 dias positivos com local, a pergunta supervisionada pode ser
  reaberta, sempre com novo pré-registro (MEMO §3.2). É a única fonte realista de dezenas de episódios novos
  (INTERPRETACAO §4.2).
- **Se nunca chegar:** a dissertação fica com 14 positivos e com o negativo "dia sem registro". O resultado continua sendo o
  inconclusivo da 1ª análise, lido no enquadramento G (PRONTIDAO, "Veredito"). O Atlas Digital de Desastres não substitui:
  só traz eventos graves em dia × município (PROGRESSO, F4-IA-12).
- **Primeiro passo:** cadastrar-se no Portal da Transparência do Recife, escolher o órgão da Defesa Civil no formulário,
  colar o texto e guardar o número do protocolo. É o pedido mais demorado; convém enviá-lo primeiro.

### 3.6 Cadastro da microdrenagem (pedido 06, LAI)

- **O que falta:** nos entornos (raio de `[300 m]`) de Derby, Imbiribeira, Ibura e CIn-UFPE: traçado da rede, diâmetros e
  material, cotas de fundo nos poços de visita, soleiras e exutórios com o corpo receptor, ano de implantação e o
  referencial vertical das cotas.
- **Com quem:** EMLURB ou URB Recife, pelo mesmo formulário de LAI. O órgão que mantém o cadastro precisa ser confirmado (o
  arquivo sugere trocar para a Secretaria de Obras ou a SESAN, se for o caso).
- **Arquivo:** `docs/pedidos/06-lai-emlurb-cadastro-microdrenagem.md`. Pede CSV para cotas e diâmetros e GeoJSON ou
  Shapefile para o traçado; PDF só como complemento.
- **Por que é necessário:** a capacidade da galeria (`f`) e a soleira não têm fonte e entram no modelo só como faixa
  (PARAMETROS-MODELO-FISICO; PRE-REGISTRO-AVALIACAO §10, item 9). Com a capacidade medida, o cancelamento entre o
  coeficiente de escoamento e a área deixa de valer e o balde passa a medir algo próprio (INTERPRETACAO §4.1, item 2).
- **Para que serve:** na dissertação, um modelo físico com parâmetros de fonte, condição para qualquer nova análise ter
  sentido físico. No sistema, a soleira é pré-requisito das regras de refluxo (ADR 0009).
- **Se nunca chegar:** `f` e soleira continuam como faixa; a dissertação declara que o resultado depende dela (na 1ª
  análise a faixa toda deu a mesma categoria; RESULTADOS §6).
- **Primeiro passo:** confirmar por telefone ou pelo site da Prefeitura qual órgão guarda o cadastro, ajustar o destinatário
  e o raio, e enviar.

## 4. Pendências que não são pedidos redigidos

### 4.1 Termos de uso das demais fontes

| Fonte | O que confirmar | Com quem | Origem |
|---|---|---|---|
| ANA (Hidro-WebService) | Redistribuição, uso em site público e comercial, limite de requisições | hidro@ana.gov.br | FONTES, pendência 4 |
| APAC | Termo e licença dos dados de chuva e nível | APAC, por escrito | FONTES, pendência 5 |
| Cemaden | Se a licença CC BY-ND 3.0 BR cobre as séries e permite derivados | Fale Conosco ou SIC do Cemaden | FONTES, pendência 6 |
| Open-Meteo | Frase de atribuição; se o uso pela Prefeitura (site sem assinatura nem publicidade) é não comercial | página de licença do Open-Meteo; se houver dúvida, e-mail do projeto | FONTES, pendência 7; RC-21 |
| CARTO (mapa base) | Limite gratuito (as cláusulas 12.c e 12.d conflitam) e chave própria em produção | CARTO | FONTES, pendência 10 |
| Prefeitura (CKAN) | Licença de cada recurso anual e forma de atribuição oficial | EMPREL | FONTES, pendência 3 |
| TabuaMare | Termo da API (o código é MIT, mas o dado é da DHN) | autor da API; ou consumir a DHN direto | FONTES, pendência 9 |

- **Por que:** sem os termos, a seção de fontes da dissertação fica com "pendente" e não se pode publicar o dataset nem
  abrir o site ao público.
- **Para que serve:** reprodutibilidade (outra pessoa poder baixar os mesmos dados) e uso legal do sistema pela Prefeitura.
- **Se nunca chegar:** declarar na dissertação, publicar só código e hashes, e manter o site em uso local.
- **Primeiro passo:** um e-mail curto por órgão, começando pela ANA e pelo Open-Meteo, que afetam o resultado publicado.

### 4.2 Credencial do Hidro-WebService da ANA

- **O que falta:** acesso à ANA ao vivo. O histórico veio pelo serviço antigo (`ServiceANA.asmx`), e o Hidro-WebService ao
  vivo não foi testado por falta de credencial (PROGRESSO, F2-DADOS-07 e F2-DADOS-10).
- **Com quem:** ANA, pelo cadastro do Hidro-WebService ou por hidro@ana.gov.br.
- **Por que e para que:** só para o painel ao vivo ter a ANA. Não afeta a dissertação.
- **Se nunca chegar:** o painel segue com Cemaden e APAC.
- **Primeiro passo:** pedir o cadastro junto com a pergunta de termo de uso do item 4.1.

### 4.3 Pluviômetro urbano com hora confiável em 2023–2024

- **O que falta:** uma série de chuva de 15 min ou horária, de estação dentro do Recife, com fuso conhecido, nos anos que
  têm rótulo. A ANA mais próxima fica a 17 km do Derby (PRE-REGISTRO-ANALISE-2 §5), a 2ª análise com ela ficou "não
  testada" (portão reprovado), e o Cemaden verificado no banco só existe desde 2026-10-09 (PROGRESSO, F4-IA-13).
- **Com quem:** Cemaden (histórico dos pluviômetros do Recife), APAC ou ANA. O repositório **não verificou** se essa série
  histórica pode ser obtida.
- **Por que é necessário:** a 1ª análise degenerou porque a reanálise não alcança as intensidades de projeto (16,7 contra
  50,3 mm/h). Sem chuva pontual, qualquer número de casos reproduz a degeneração (INTERPRETACAO §4.1, item 1).
- **Para que serve:** é a condição mais importante para uma futura análise com poder.
- **Se nunca chegar:** a limitação "escala da forçante" fica como resultado declarado da dissertação.
- **Primeiro passo:** consultar o portal de dados do Cemaden e, se não houver download do período, pedir pelo SIC do
  Cemaden a série das estações do Recife de 2023–2024.

### 4.4 Rede de Petri: confirmação do export

- **O que falta:** você confirmar que, quando a Petri estiver pronta, ela exporta no formato combinado na MODELAGEM-IA §2.2
  (campos como lâmina em mm por ponto e replicação), e com quantas replicações.
- **Por que e para que:** H3, H4 e o emulador saíram da avaliação por emenda e só voltam com novo pré-registro (PROGRESSO,
  decisão de 2026-10-11). O formato combinado evita reescrever o código de avaliação.
- **Se nunca chegar:** a Petri fica como trabalho futuro na dissertação, como já está no deck (PROGRESSO, F4-IA-07).
- **Primeiro passo:** quando retomar a Petri, ler a MODELAGEM-IA §2.2 e responder sim ou não no PROGRESSO.

### 4.5 Onde guardar a cópia dos dumps (pergunta 31, urgente)

- **O que falta:** sua decisão sobre o destino da cópia dos 4 dumps (o pós-carga tem 317 MB), das entradas e dos escores da
  avaliação. Opções da pergunta 31: disco externo, seu Google Drive (com uma versão sem a tabela `users`) ou outro.
- **Por que é necessário:** hoje existe uma única cópia, no mesmo disco, que estava 96% ocupado (PROGRESSO, pergunta 31;
  PRONTIDAO L2). Bloqueia a F5-OPS-03. Se o disco falhar, parte dos dados não se recupera igual (o CKAN muda, a reanálise
  é revisada; RC-01).
- **Para que serve:** reprodutibilidade da dissertação. Os dumps são a âncora do modo `fontes` de `reproduzir.sh`.
- **Se nunca chegar:** risco de perder o dado que sustenta todos os números.
- **Primeiro passo:** responder a pergunta 31 com uma letra (a, b ou c).

### 4.6 Teste da camada de terreno em celular real

- **O que falta:** abrir o mapa num celular de gama média em rede 4G, ligar a camada de terreno no Derby, navegar do zoom 14
  ao 17 e medir o tempo até aparecer. Antes, fixar por escrito quantas rodadas (pelo menos 5) e se vale a mediana
  (RC-12).
- **Por que é necessário:** o critério do ADR 0008 é ≤ 3 s. Na simulação, a camada ficou em 2,95–2,98 s mesmo depois da
  otimização, numa máquina sem GPU (PROGRESSO, F3-FRONT-03; ADR 0008, cabeçalho).
- **Para que serve:** decidir se a camada fica como está ou se o ADR 0008 precisa ser reaberto.
- **Se nunca chegar:** a camada fica marcada como "no limite, não validada em celular".
- **Primeiro passo:** subir o front e a API localmente, abrir pelo celular na mesma rede e cronometrar.

### 4.7 Sua confirmação e a leitura do orientador

- **O que falta:** no cabeçalho do `PRE-REGISTRO-AVALIACAO.md`, a sua confirmação das escolhas da §2 e da §4.2 e a leitura
  do orientador estão em branco. As decisões de método do MEMO §5 (2021 como "sem rótulo", limiar só de fonte externa)
  foram tomadas pelo Arquiteto e pedem a sua confirmação (RC-25). A revisão crítica também recomenda a leitura humana do
  MEMO e dos ADRs 0006 e 0009 (RC-11).
- **Por que é necessário:** todos os checkpoints da revisão foram feitos por modelos da mesma família. Para a banca, o
  protocolo precisa de uma leitura humana registrada.
- **Para que serve:** validade do protocolo e da 1ª análise na dissertação.
- **Se nunca chegar:** declarar na metodologia que a confirmação não foi feita antes da execução.
- **Primeiro passo:** marcar `[x] confirmado em <data>` no cabeçalho e levar os documentos à próxima reunião com o orientador.

### 4.8 Polígonos oficiais de bairro

- **O que falta:** os limites oficiais dos bairros do Recife.
- **Com quem:** Prefeitura do Recife (portal de dados abertos ou ESIG).
- **Por que e para que:** só para a análise secundária por bairro (Q3), que não rodou por falta deles (RESULTADOS §8).
- **Se nunca chegar:** a análise por bairro fica fora.
- **Primeiro passo:** procurar o conjunto "bairros" no portal de dados abertos da Prefeitura.

### 4.9 Modelo de reanálise do Open-Meteo por período

- **O que falta:** confirmar, na documentação do Open-Meteo, qual modelo o archive usou de 2021-04 a 2024-12 e se foi o
  mesmo em todo o período (PRONTIDAO L7; PRE-REGISTRO-AVALIACAO §10, item 5). Junto, há a suspeita de um deslocamento de
  ~1 h do lado do Open-Meteo (PROGRESSO, F2-DADOS-10; RC-16).
- **Por que e para que:** são ameaças à validade da 1ª análise que precisam aparecer no texto com a resposta, não como dúvida.
- **Se nunca chegar:** declarar como ameaça não resolvida.
- **Primeiro passo:** ler a página do archive do Open-Meteo e registrar a resposta, com a data de acesso, no PROGRESSO.

## 5. Ordem sugerida

1. **Hoje:** responder a pergunta 31 (item 4.5).
2. **Esta semana:** enviar o pedido 05 (o mais demorado), depois os pedidos 01, 02 e 03 juntos (fecham o Δ) e o 06;
   marcar a confirmação no pré-registro (item 4.7).
3. **Em seguida:** pedido 04, e-mails de termo de uso (item 4.1) e o teste no celular (item 4.6).
4. Registrar cada envio no PROGRESSO com data e protocolo, para o prazo da LAI poder ser cobrado.
