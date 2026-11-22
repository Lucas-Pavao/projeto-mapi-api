# Pré-registro: entupimento e limpeza pela Central 156

- Data: 2026-11-21 · Prompt 3, tarefa 1 (depois da aprovação) · Escrito e commitado **antes** de rodar sobre os pontos.
- Aprovado pelo Lucas em 2026-11-21: os tipos que contam como entupimento (relatório, seção 5) e o pedido de usar os chamados de
  limpeza para dar menos chance de alagar logo depois de uma limpeza.
- Nenhum dado de alagamento é usado. A 156 não tem serviço de alagamento (arquivo lacrado vazio). A chuva usada é só a série de
  chuva por ponto (`projeto-mapi-ai/data/bucket10/serie10min_<ponto>.csv.gz`), sem rótulo.

## 1. Eventos

| Evento | Serviços (grupo/código) | Data |
|---|---|---|
| **Entupimento percebido** (chamado) | limpeza de galerias, valetas e sarjetas (2/4, 11/4, 27/8) e limpeza de canaleta (2/81, 11/81, 27/157) | `DATA_DEMANDA` |
| **Limpeza feita** | os mesmos serviços com `SITUACAO = ATENDIDA` | `DATA_ULT_SITUACAO` (data em que virou atendida) |

Falha estrutural (abatimento 2/72, 11/72, 27/141; recuperação de canaleta 2/97, 11/97, 27/189) é contada à parte e não entra em λ.

## 2. Casamento chamado–ponto (escrito antes)

1. **Por coordenada** (2015–2026, quando houver lat/lon): chamado a até **150 m** do ponto. Sensibilidade: 100 e 300 m. 150 m é da
   ordem do lado das bacias de 10.000 m² do terreno (~100 m) mais a incerteza do endereço geocodificado.
2. **Por logradouro e bairro** (todos os anos, inclusive 2012–2014 e chamados sem coordenada): logradouro normalizado (maiúsculas, sem
   acento, sem prefixo de tipo como AV/R/RUA/TR, espaços simples) igual ao do ponto **e** bairro normalizado igual ao do ponto.
   Logradouro do ponto: Ibura `RECIFE`, Derby `AGAMENON MAGALHAES`, Imbiribeira `MASCARENHAS DE MORAIS`; CIn sem logradouro (fica só a
   regra 1).
   **Correção de grafia (2026-11-21, depois da 1ª execução):** a 156 grava `GOVERNADOR AGAMENON MAGALHAES` e
   `MARECHAL MASCARENHAS DE MORAES` (com E). Os nomes acima não casavam com nenhuma linha; foram trocados pelos da base. É erro de
   digitação do pré-registro, não escolha pelo resultado (a 1ª execução fica registrada no commit anterior).
5. **Sensibilidade fora do pré-registro, declarada:** a rua inteira (logradouro igual, qualquer bairro), porque a regra do bairro
   mais frequente a 150 m pegou o bairro vizinho no Derby (BOA VISTA) e na Imbiribeira (BOA VIAGEM). Bairro do ponto: o bairro mais frequente entre **todos** os chamados da 156 (qualquer serviço) a até 150 m do ponto.
3. Taxa de casamento reportada: fração dos chamados de drenagem do bairro do ponto que casaram por cada regra.
4. **Por bairro** (para ter mais eventos): todos os chamados do bairro do ponto.

## 3. O que é calculado

1. **λ (entupimento percebido):** chamados por ano por ponto (regra 1, 150 m) e por boca de lobo (dividindo pelo nº de BL do ESIG a até
   150 m; se 0, só por ponto). Por bairro: chamados por ano.
2. **Tempo de atendimento:** de `DATA_DEMANDA` a `DATA_ULT_SITUACAO`. ATENDIDA = evento; as outras situações = **censuradas** na data da
   última movimentação. Estimador de Kaplan–Meier: mediana e quantis 25% e 75%. Ressalva: inclui fila e burocracia.
3. **Reentupimento depois de uma limpeza (o pedido novo):** para cada limpeza feita, o tempo até o próximo chamado de entupimento no
   mesmo casamento (ponto a 150 m, ou bairro). Sem novo chamado até o fim dos dados (última `DATA_DEMANDA` do arquivo de 2026) =
   censurado. Estima-se a taxa de reentupimento por **exposição** (eventos / dias expostos, modelo exponencial) e a curva de
   Kaplan–Meier em 30, 60, 90, 180 e 365 dias. Isso dá `P(entupida | t dias desde a última limpeza) ≈ 1 − exp(−λ_re · t)`.
4. **Sazonalidade:** chamados por mês do ano (bairro e cidade).
5. **Dias secos:** para cada chamado (2021-09 em diante, período da série de chuva), dias desde o último dia com ≥ 1 mm no pluviômetro
   do ponto; comparado com a mesma conta em todos os dias do período.

## 4. Como entra no modelo de estados

O estado entupido passa a depender do tempo desde a última limpeza conhecida: `π_E(t) = 1 − exp(−λ_re · t)` com λ_re do item 3.3
(chamado = entupimento percebido, então λ_re é limite inferior). Sem limpeza conhecida, usa-se o regime permanente. **Limpeza recente →
π_E pequeno → menor chance de alagar** na mistura de `MODELO-DISPONIBILIDADE-GALERIA.md`, seção 3. Os valores saem só da 156; nenhum
valor é ajustado para acertar alagamento.

## 5. Fonte da frequência esperada de limpeza

Não há frequência oficial publicada acessível: a página de drenagem da EMLURB e o Caderno de Encargos
(`www2.recife.pe.gov.br/.../caderno_de_encargos_da_emlurb_versao_final_v4.pdf`) não abriram em 2026-11-21 (certificado e DNS, também no
navegador). A frequência programada fica no pedido 09. Na falta dela, a frequência **observada** de limpeza perto de cada ponto sai do
item 3.3 (intervalo entre limpezas ATENDIDA), e a forma da curva é conferida com a literatura: o leito de sedimento estabiliza em alguns
meses (Post et al., 2016, via Rietveld et al., 2020, p. 549) e limpar a cada 2 meses retira ~3 vezes mais massa que 1 vez por ano
(Langeveld et al., 2016, mesma página).

## Emenda 1 (2026-11-22, onda 16, Fase 1) — PROPOSTA, espera o ok do ponto B

Escrita antes de rodar qualquer conta nova. Os números que já se conhecem (λ_re, últimas limpezas, medianas de atendimento) vêm
do relatório da onda 14 (`docs/extratos/emlurb-156/entupimento/RESULTADOS.md`). Nenhuma regra abaixo foi escolhida para mudar
o alerta de um jeito ou de outro.

### Por que

- **A limpeza satura.** A última limpeza conhecida a 150 m é de 2023-08-31 (Mascarenhas), 2023-08-30 (Ibura) e 2024-07-05
  (Derby). A base vai até 2026-06-19. Com `π_E(t) = 1 − exp(−λ_re·t)`, Mascarenhas e Ibura dão π_E = 1,000000 e Derby 0,9998.
  Esses pontos eram limpos a cada ~2 meses (mediana do intervalo entre limpezas: 60 dias em Ibura). Quase 3 anos sem limpeza
  registrada indica falta de registro, não falta de limpeza.
- **O bairro do CIn está errado.** A regra "bairro mais frequente entre os chamados a ≤ 150 m" pegou **um único** chamado,
  gravado pela 156 como AREIAS (Praça Jornalista Aníbal Fernandes, que fica na Cidade Universitária).
- **A 156 tem coordenadas-padrão.** Um mesmo ponto a 482 m do CIn junta 115 chamados de Beberibe, Dois Unidos e Casa Amarela.
  As três coordenadas mais repetidas da cidade juntam 2.771, 2.398 e 1.879 chamados. Um chamado numa coordenada-padrão não diz
  onde a boca de lobo está.

### Regras propostas

**E1. Coordenada-padrão sai do casamento por coordenada.** Uma coordenada (arredondada a 5 casas, ~1 m) com **20 ou mais**
chamados (qualquer serviço, todos os anos), e em que o bairro mais frequente tem **menos de 50%** deles, é coordenada-padrão.
Chamados nela saem do casamento por coordenada (regra 1 da seção 2). Continuam valendo pelo casamento por logradouro e bairro
(regra 2). O relatório traz quantas coordenadas e quantos chamados caem nessa regra, na cidade e perto de cada ponto.

**E2. Bairro do ponto pelo polígono oficial.** O bairro do ponto passa a ser o do polígono de bairros da Prefeitura do Recife
que contém o ponto (camada oficial de bairros; ESIG ou portal de dados abertos, licença e data de acesso registradas). Sai a
regra do bairro mais frequente. Pontos fora do Recife (Jaboatão) ficam sem bairro até haver camada oficial do município.

**E3. Quando a limpeza conhecida vale.** Para cada ponto com taxa própria (≥ 10 limpezas, desvio 4):
- `fim_cobertura` = a última `DATA_DEMANDA` da base (hoje 2026-06-19);
- `N_p` = percentil 95 dos intervalos entre limpezas ATENDIDA consecutivas no mesmo casamento do ponto;
- a última limpeza conhecida **vale** se `fim_cobertura − última_limpeza ≤ N_p`. Se não vale, a limpeza é **desconhecida**;
- depois da cobertura, a limpeza também é **desconhecida** quando a data de referência passa de `fim_cobertura + N_p`. A base
  anual é semanal, mas os Dados Vivos estão parados desde 2025-01-23.

**E4. Limpeza desconhecida usa o regime permanente**, já previsto na seção 4 e nunca implementado. É um processo de
renovação alternado: livre por um tempo médio `1/λ_re` (da limpeza ao próximo chamado) e entupida por um tempo médio `D_E`
(do chamado à limpeza). Assim:

`π_E = D_E / (1/λ_re + D_E)`

`D_E` é a média restrita a 365 dias da curva de Kaplan–Meier do tempo de atendimento do ponto (`atendimento_km.csv`). Com
menos de 10 atendimentos no ponto, usa o do bairro. A média, e não a mediana: a mediana de Derby é 0 dia (24 de 29 atendidos
no mesmo dia, provável fechamento administrativo) e apagaria o estado entupido.

**E5. Onde entra e quando.**
- Na IA, `mapi_ai/entupimento.py` devolve `pi_E`, `regime` (`TEMPO_DESDE_LIMPEZA` ou `PERMANENTE`) e o motivo.
- Na api, `LimpezaService` aplica E3 e E4 e grava o regime no ciclo.
- A carga da 156 no banco (`POST /api/admin/limpeza/ingestao`) só roda depois desta emenda aprovada (decisão do ponto A).
- E1 e E2 refazem o casamento, λ_re e o bairro de todos os pontos. Os valores novos vão para `parametros_156.json` e para
  `LimpezaProperties`, com o antes e o depois no relatório.

### O que esta emenda não resolve

- **Capacidade da galeria entupida.** `f_entupida = 0` (galeria fechada) continua `[sem fonte]`. No regime permanente, π_E
  passa a ser um número intermediário, e com f = 0 o estado entupido enche o balde com pouca chuva. O valor de f é da Fase 4
  (capacidade efetiva, com fonte ou como faixa).
- **"Chamado" é entupimento percebido**, então λ_re continua sendo um limite inferior.
