# Entupimento e limpeza pela Central 156: resultados

- Data: 2026-11-21 · Método: `docs/PRE-REGISTRO-ENTUPIMENTO-156.md` (commitado antes de rodar, `cf48572`).
- Reproduzir: `python3 -I scripts/central-156/entupimento_156.py <pasta 156> <geojson ESIG> <séries de chuva>`.
- Dados: 15 CSV anuais da 156 (2012-01-01 a 2026-06-19), hashes em `../MANIFESTO.csv`. Nenhum dado de alagamento.
- Tipos aprovados como entupimento: limpeza de galerias, valetas e sarjetas e limpeza de canaleta. "Limpeza feita" = esses mesmos
  chamados com situação ATENDIDA.

## Desvios do pré-registro (todos declarados)

1. **Grafia dos logradouros:** a 1ª execução usou `AGAMENON MAGALHAES` e `MASCARENHAS DE MORAIS`, que não existem na 156; trocados por
   `GOVERNADOR AGAMENON MAGALHAES` e `MARECHAL MASCARENHAS DE MORAES` (commit com a 1ª execução: `fix(156): grafia...`).
2. **Início da cobertura:** o arquivo de 2012 traz demandas de 2008; a cobertura passou a contar de 2012-01-01 (14,46 anos, não 18,26).
3. **Sensibilidade "rua inteira"** (qualquer bairro), porque o bairro mais frequente a 150 m saiu o vizinho no Derby (BOA VISTA) e na
   Imbiribeira (BOA VIAGEM). No CIn ele saiu AREIAS, com 1 chamado a 150 m.
4. **Mínimo de 10 limpezas** casadas para usar a taxa do ponto no modelo (`mapi_ai/entupimento.py`, `MIN_LIMPEZAS`). Regra nova, posta
   depois de ver que a Imbiribeira tem 4.

Qualidade da coordenada: algumas coordenadas da 156 se repetem em milhares de chamados de bairros diferentes (por exemplo, −8,06527 /
−34,89692 em 2.771 chamados de Santo Amaro, Derby, Campo Grande…), sinal de geocodificação com ponto padrão. A 150 m dos pontos isso pesa
pouco (Ibura: 537 chamados em 123 coordenadas distintas), mas não foi filtrado.

## 1. Casamento chamado–ponto (`casamento.csv`)

| Ponto | Bairro (regra) | Por coordenada 150 m | Por logradouro + bairro | União | Rua inteira (sensib.) |
|---|---|---|---|---|---|
| Ibura | IPSEP | 64 | 58 | **122** | 152 |
| CIn | AREIAS | 0 | — | **0** | — |
| Derby | BOA VISTA | 3 | 26 | **29** | 261 |
| Imbiribeira | BOA VIAGEM | 0 | 5 | **5** | 146 |

## 2. Frequência de entupimento percebido, λ (`lambda_por_ponto.csv`)

| Ponto | Chamados por ano (união) | BL do ESIG a 150 m | Chamados por boca de lobo por ano |
|---|---|---|---|
| Ibura | 8,4 | 14 | 0,60 |
| Derby | 2,0 | 10 | 0,20 |
| Imbiribeira | 0,35 | 0 | — |
| CIn | 0 | 1 | — |

Limite inferior: é entupimento reclamado, não todo entupimento.

## 3. Tempo de atendimento (`atendimento_km.csv`, Kaplan–Meier com censura)

Ibura: mediana 75 dias (25%: 0; 75%: 149), 53 de 122 atendidos. Derby: mediana 0 dias (24 de 29 atendidos no mesmo dia, provável
registro na hora da execução). Imbiribeira: 113 dias (5 chamados). Inclui fila e burocracia; dias inteiros.

## 4. Reentupimento depois de uma limpeza (`reentupimento.csv`) — o que entra no modelo

| Ponto (150 m) | Limpezas | Taxa λ_re (por dia) | Tempo médio até o próximo chamado | Mediana (KM) | P(novo chamado em 30 / 60 / 90 dias) | Intervalo mediano entre limpezas |
|---|---|---|---|---|---|---|
| **Ibura** | 53 | 0,0235 | 43 dias | 27 dias | 0,51 / 0,72 / 0,85 | 60 dias |
| **Derby** | 24 | 0,0053 | 189 dias | 69 dias | 0,38 / 0,46 / 0,63 | 69 dias |
| Imbiribeira | 4 | (0,0008) | — | — | — | — |
| CIn | 0 | — | — | — | — | — |

Na escala da rua inteira e do bairro, a taxa mede chamados em qualquer ponto da rua ou do bairro, não da mesma galeria (bairro: um novo
chamado a cada 2 a 6 dias); fica só como registro.

**Frequência de limpeza.** Não há frequência oficial publicada acessível (página de drenagem da EMLURB e Caderno de Encargos fora do ar
em 2026-11-21, também no navegador; pedido 09). A frequência **observada** perto do Ibura e do Derby é de uma limpeza a cada **60 a 69
dias** (mediana), o que bate com a ordem de grandeza da literatura: limpar a cada 2 meses em vez de 1 vez por ano retira ~3 vezes mais
material (Langeveld et al., 2016, via Rietveld et al., 2020, p. 549), e o leito de sedimento estabiliza em alguns meses (Post et al.,
2016, mesma página). A limpeza observada na 156 é **corretiva** (depois de chamado), não programada.

### 4.1 Depois do alinhamento dos pontos por notícia (2026-11-21, `docs/ALINHAMENTO-PONTOS.md`)

Mesmo método, posições novas (Ibura 9 m, Derby 337 m, Imbiribeira 1,4 km). É este o valor usado no alerta (`LimpezaProperties`) e na IA.

| Ponto (150 m) | Limpezas | λ_re (por dia) | Tempo médio até o próximo chamado | Mediana (KM) | P(novo chamado em 30 / 60 / 90 dias) | Intervalo mediano entre limpezas |
|---|---|---|---|---|---|---|
| Ibura | 53 | 0,0234 | 43 dias | 31 dias | 0,49 / 0,72 / 0,85 | 60 dias |
| Derby | 35 | 0,0098 | 102 dias | 36 dias | 0,43 / 0,60 / 0,71 | 64 dias |
| Imbiribeira | 130 | 0,0333 | 30 dias | 20 dias | 0,65 / 0,85 / 0,95 | 26 dias |
| CIn | 0 | — | — | — | — | — |

Casamento (bairro do ponto): Ibura IPSEP, Derby BOA VISTA, Imbiribeira IMBIRIBEIRA, CIn AREIAS.

## 5. Sazonalidade (`sazonalidade.csv`)

Ibura (união, 122 chamados): pico de março a agosto (12 a 18 por mês) e mínimo em outubro (3). Os bairros seguem o mesmo desenho
(maio a julho). Pode ser entupimento maior na estação chuvosa ou só mais gente percebendo quando chove.

## 6. Dias secos antes do chamado (`dias_secos.csv`, 2021-09 a 2026-10, só chuva)

Mediana de 0 dias secos antes do chamado e de 0 na base de todos os dias, nos 4 bairros. Fração depois de 7 ou mais dias secos: 1,3% a
3,7% dos chamados contra 3,0% a 4,4% dos dias. Os chamados **não** vêm depois de estiagem longa; vêm em período de chuva, o que reforça
que o chamado mede entupimento **percebido** quando chove, não o acúmulo no seco.

## 7. Como entra no modelo

`projeto-mapi-ai/mapi_ai/entupimento.py` (branch `feat/entupimento-limpeza`): `P(entupida | t) = 1 − exp(−λ_re · t)`, com t = dias desde a
última limpeza conhecida, e a lâmina vira mistura dos dois estados (livre e entupida). Exemplo no Ibura (λ_re = 0,0235/dia):

| Dias desde a limpeza | 3 | 7 | 30 | 60 | 90 |
|---|---|---|---|---|---|
| P(entupida) | 0,07 | 0,15 | 0,51 | 0,76 | 0,88 |

Limpeza recente → P(entupida) pequena → chance de alagar menor. Pontos sem taxa (CIn, Imbiribeira) ficam com o comportamento de hoje
(galeria livre), nunca com um número inventado.

## Emenda 1 (2026-11-22, aprovada pelo Lucas): antes e depois

Regras em `docs/PRE-REGISTRO-ENTUPIMENTO-156.md` (emenda 1), escritas antes de rodar. Comando:
`python3 -I scripts/central-156/entupimento_156.py MAPI/fontes/emlurb-156 docs/extratos/esig-drenagem/geojson projeto-mapi-ai/data/bucket10 MAPI/fontes/recife-bairros/bairros.geojson`.

- **E1, coordenadas-padrão:** 109 coordenadas, com 21.948 chamados (`coordenadas_padrao.json`). Perto dos pontos (150 m):
  155 no Derby, 0 nos outros.
- **E2, bairro oficial:** polígonos "Bairros do Recife" (Prefeitura do Recife, dataset "Área urbana", ODbL; acesso em
  2026-11-22; SHA-256 `2099e5d6…` em `MAPI/fontes/recife-bairros/`).
- **Achado:** em 2024, só 805 dos chamados de drenagem têm coordenada (nos outros anos de 2015 a 2026, de 1.384 a 5.198).
  Nesse ano o casamento por coordenada fica quase cego e vale o casamento por logradouro e bairro.

| Ponto | Bairro (antes → depois) | λ_re/dia (antes → depois) | Limpezas | Última limpeza | p95 do intervalo (dias) | Limpeza no fim da base | D_E (dias) | π_E permanente |
|---|---|---|---|---|---|---|---|---|
| Ibura | IPSEP → IBURA | 0,02337 → 0,01038 | 53 → 19 | 2023-08-30 → 2023-08-17 | 1.648 | **conhecida** (1.037 ≤ 1.648) | 75,9 (ponto) | 0,441 |
| Derby | BOA VISTA → DERBY | 0,00981 → 0,01068 | 35 → 40 | 2024-07-05 → 2024-08-21 | 445 | desconhecida (667 > 445) | 59,0 (ponto) | 0,387 |
| Mascarenhas | IMBIRIBEIRA (igual) | 0,03331 (igual) | 130 | 2023-08-31 | 221 | desconhecida (1.023 > 221) | 44,2 (ponto) | 0,596 |
| CIn | AREIAS → CIDADE UNIVERSITARIA | sem taxa | 0 | — | — | — | 185,5 (bairro) | — |

Leitura:
- **Ibura continua saturando.** Com o bairro oficial (IBURA), o casamento por logradouro e bairro perdeu os chamados que a
  156 grava como IPSEP na Av. Recife: as limpezas caem de 53 para 19. Os intervalos entre essas 19 têm p95 de 1.648 dias. Pela
  regra, a limpeza de 2023-08-17 ainda é "conhecida", e P(entupida) = 1 − exp(−0,01038 × 1.037) ≈ 1,0. A regra foi aplicada
  como aprovada e não foi mudada depois de ver o resultado. Fica registrado para decisão.
- **Derby e Mascarenhas saem da saturação:** regime permanente com π_E 0,387 e 0,596.
- **No ar:** a carga completa da base no banco (`POST /api/admin/limpeza/ingestao?anoInicial=2012`, ADMIN) ainda não rodou.
  Só a carga semanal (ano corrente) roda sozinha. Até a carga completa, todo ponto com taxa fica em regime permanente
  ("nenhuma limpeza registrada").
