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
   regra 1). Bairro do ponto: o bairro mais frequente entre **todos** os chamados da 156 (qualquer serviço) a até 150 m do ponto.
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
