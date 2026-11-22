# Previsão do INMET por município: o que a rota entrega (bloco A1 do prompt dos horizontes)

Conferido em 2026-11-22 com chamadas reais. Respostas brutas no scratchpad da sessão; a do Recife, com os ícones
encurtados, virou fixture de teste (`src/test/resources/inmet/previsao-2611606-real.json`).

## Rota e acesso

- `GET https://apiprevmet3.inmet.gov.br/previsao/{codigo_ibge}`: HTTP 200, `application/json`, sem chave.
- Exige `User-Agent` de navegador, como a rota dos avisos (ADR 0024). Sem ele a conexão expira.
- Tamanho: 222 967 bytes (Recife), 224 916 (Jaboatão dos Guararapes, `2607901`), 222 916 (Olinda, `2609600`). Quase
  tudo é ícone PNG em base64.
- Limite de chamadas: nenhum publicado e nenhum cabeçalho de limite na resposta. Coletamos uma requisição por vez, com
  pausa de 2 s entre municípios, a cada 3 h.
- Termo de uso: o mesmo dos avisos (ADR 0024). O feed oficial declara domínio público e reprodução livre com citação da
  fonte. A tela cita "INMET — Instituto Nacional de Meteorologia".

## Formato

```
{"2611606": {
   "22/11/2026": {"manha": {...}, "tarde": {...}, "noite": {...}},
   "23/11/2026": {"manha": {...}, "tarde": {...}, "noite": {...}},
   "24/11/2026": {...campos do dia...},
   "25/11/2026": {...},
   "26/11/2026": {...}}}
```

- **5 dias.** Os 2 primeiros vêm por turno (manhã, tarde, noite). Os 3 seguintes vêm num bloco só. No MAPi, esse
  bloco vira o período `DIA`.
- Campos de cada bloco: `uf`, `entidade` (nome do município), `resumo` (texto, ex.: "Muitas nuvens com possibilidade
  de chuva isolada"), `tempo` (vazio nas 3 respostas), `temp_max`, `temp_min`, `umidade_max`, `umidade_min`,
  `dir_vento`, `int_vento`, `cod_icone`, `icone` (PNG base64), tendências de temperatura (texto e ícone),
  `dia_semana`, `estacao`, `nascer`, `ocaso`, `hora`, `fonte` (`prevmet`).
- Números vêm como texto (`"34"`).
- Datas no formato `dd/MM/yyyy`, sem fuso. São datas civis no horário de Brasília (= Recife, UTC−3 desde 2019).

## Quantidade de chuva

**Não há.** Nenhum campo com milímetros nem com probabilidade de chuva, em nenhum dos 3 municípios. A chuva só aparece
no texto do `resumo` e no código do ícone. Por isso a previsão do INMET não pode ser comparada em mm com a chuva medida.
A consulta do A3 (`docs/extratos/sql/inmet-previsao-x-chuva-medida.sql`) compara o texto ("pancada", "trovoada",
"chuva forte") com os eventos FORTE.

O coletor guarda em `rain_fields` qualquer campo com "chuva", "precip" ou "prob" no nome, como veio, para o caso de a
origem passar a trazer algum (há teste para isso).

## Hora de emissão

**Não há.** O campo `hora` parecia ser a hora da emissão, mas acompanha a hora local da consulta:

| Consulta (UTC) | Hora local | `hora` | SHA-256 do corpo (16 primeiros) | SHA-256 sem ícones |
|---|---|---|---|---|
| 2026-11-22 16:56 | 13h56 | `"13"` | `160b477c2169fca6` | `6111c245270c6e51` |
| 2026-11-22 17:15 | 14h15 | `"14"` | `676a34e9b0135ff5` | `e8b491df7bd64b7f` |
| 2026-11-22 17:33 | 14h33 | `"14"` | `6c982917391c7617` | (mudou de novo, ver abaixo) |

Entre a 1ª e a 2ª consulta, além de `hora`, mudaram a temperatura e o vento da manhã e da tarde de amanhã (23/11:
máxima 32 → 33 °C, mínima 24 → 25 °C, vento E-SE → E-W). Entre a 2ª e a 3ª, `hora` ficou em "14" e mudou só a noite
de amanhã (máxima 32 → 33 °C, mínima 24 → 25 °C). Leitura: a previsão é atualizada aos pedaços, perto das 14h locais, e
`hora` acompanha a hora local da consulta. Por isso:

- `hora` fica **fora** do hash que define uma versão da previsão (senão cada coleta viraria uma "versão nova");
- a emissão é aproximada por `first_seen_at`: o primeiro instante em que o MAPi viu aquela versão, como nos avisos;
- com coleta a cada 3 h, o atraso de `first_seen_at` em relação à atualização real fica em até 3 h. Isso é declarado
  na API (`note`).

## Frequência de mudança

Nas três consultas de 22/11 (13h56, 14h15 e 14h33 locais), a previsão de amanhã mudou duas vezes em 37 minutos, aos
pedaços. Três horários tão próximos não dizem quantas vezes ela muda ao longo do dia inteiro. A cadência de 3 h foi
mantida: o histórico guarda cada versão vista como uma linha (nada é sobrescrito), e depois de alguns dias a própria
tabela `official_forecast` mostra a frequência real de mudança. Se as mudanças se concentrarem perto das 14h, a coleta
pode passar a ter um horário fixo logo depois disso.

## O que o MAPi faz com isso

- Fonte `INMET_PREVISAO` (V41), fuso declarado `America/Sao_Paulo`, idade máxima de 12 h no alarme de fonte velha.
- Bruto inteiro em `raw_observation` com SHA-256, só quando o corpo muda.
- Uma linha por versão vista em `official_forecast`, com `first_seen_at` e `last_seen_at`.
- Leitura: `GET /api/previsao-oficial/{municipio}` (app) e `GET /api/public/previsao-oficial/{municipio}` (Minha rua).
- Na tela, aparece como "previsão oficial do INMET" e "visto às HHhMM", nunca com as cores de risco do MAPi.
