# Extrato — Radar da APAC (Chã Grande): imagens, legenda e georreferenciamento

Operação O1 do prompt do alerta de 20 min. Captura em 2026-11-17. **Termo de uso pendente: gravar e não publicar
derivado.** As imagens não entram em modelo nem na tela.

## 1. De onde vêm

- Diretório: `https://barramento2.apac.pe.gov.br/imagens_radar/` (listagem Apache aberta; `http` redireciona para `https`).
- Nome: `AAAAMMDDHHMM.<produto>_trans.png`, a cada 10 min, minuto terminado em 5 (por exemplo, `202611171155`).
- Produtos na listagem em 2026-11-17 12:15 UTC: `cappi1km_trans.png` (682), `Acum1h_trans.png` (681),
  `Acum24h_trans.png` (682) e 3 arquivos `PPI_top.nc` de 2018. O servidor guardava de 2026-11-12 18:25 em diante (~5 dias).
- **Fuso: UTC, verificado.** O serviço do painel da APAC
  (`https://barramento.apac.pe.gov.br/BarramentoServicosApac/Servicos/Site/PainelMapaGoogle/ServicoMonitoramentoRADAR.php`)
  devolve, para o mesmo arquivo, `data_hora_utc = 202611171105` e `data_hora_local = 17-11-2026 08:05 Hora Local`.
  A data de modificação do servidor (hora local) também fica 10 min depois do nome em UTC. Resposta guardada em
  `radar-apac/ServicoMonitoramentoRADAR-2026-11-17.json`.

## 2. O que o MAPi grava

- CAPPI 1 km (pedido) e acumulado de 1 h (estimativa de chuva pelo radar; 13 KB por imagem). O acumulado de 24 h não é
  gravado: deriva do de 1 h e ocupa 84 KB por imagem, com o disco a 95% (`df`, 2026-11-17).
- Volume próprio `../radar-apac` montado em `/data/radar` (`docker-compose.yml`), em `AAAA/MM/DD/<arquivo>`.
- Manifesto `radar_image` (V33): produto, instante UTC, status (`OK`, `ABSENT`, `ERROR`), caminho, SHA-256, tamanho,
  HTTP. Instante esperado fora da listagem ou com 404 vira `ABSENT`. Nada é apagado.
- Coletor `RadarArchiveServiceImpl`, a cada 10 min; alarme de fonte velha `APAC_RADAR` com 30 min (V33).

| Situação em 2026-11-17 13:10 UTC | CAPPI 1 km | Acumulado 1 h |
|---|---|---|
| Imagens gravadas (`OK`) | 689 | 688 |
| Primeira / última | 2026-11-12 18:25 / 2026-11-17 13:05 | 2026-11-12 18:35 / 2026-11-17 13:05 |
| Lacunas (`ABSENT`) | 0 | 0 |
| Volume em disco | 23 MB (os dois produtos) | |

(fonte: `SELECT product, status, count(*), min(ts_utc), max(ts_utc) FROM radar_image GROUP BY 1, 2`; `du -sh /data/radar`)

O backfill de 2026-11-17 baixou 682 CAPPI e 681 acumulados de 1 h, sem falha; o coletor registrou esses arquivos sem
baixar de novo.

## 3. Georreferenciamento

Do script do painel da APAC (`https://www.apac.pe.gov.br/modules/mod_painel_mapa/assets/js/script.js`, função
`animacaoRadar`, lido em 2026-11-17) e do serviço do painel:

| Item | Valor | Fonte |
|---|---|---|
| Imagem | PNG RGBA, **700 × 700 px**, fundo transparente, sem costa nem grade | arquivo |
| Extensão usada no mapa | `[-33.1681, -10.462, -37.7434, -5.92565]` em EPSG:4326 | `ol.source.ImageStatic`, `imageExtent` |
| Limites (serviço) | norte −5,92565; sul −10,462; leste −33,1681; oeste −37,7434 | `limite_imagens` |
| Projeção de exibição | a extensão é transformada de EPSG:4326 para EPSG:3857 e a imagem é esticada nela (`ImageStatic`) | script |
| Radar | lat −8,19, lon −35,464; raio 250 km | `raio` |
| Tamanho do pixel | 4,5753° / 700 = 0,00654° em longitude; 4,5364° / 700 = 0,00648° em latitude (~0,72 km) | conta |

Observação: o painel passa a extensão com leste antes de oeste (−33,17 antes de −37,74). Antes de converter a imagem
em chuva, conferir a orientação com um evento de chuva medido por pluviômetro (pendente; fora deste prompt).
O painel também tem CAPPI de Petrolina, Maceió e Natal (`cappipetr`, `cappimaceio`, `cappinatal`); hoje os arquivos
desses vêm vazios no serviço.

## 4. Legenda

A legenda do painel (`.../PainelMapaGoogle/Lib/Icons/legenda_radar_apac.png`, guardada em
`radar-apac/legenda_radar_apac.png`, SHA-256 `badc7ad0671871d688eedc940e0104a54e269f500478fc737c6f12faf1085534`) é em
**mm/h**, a mesma para os três produtos no serviço. Limites das classes:
0,0 · 0,5 · 1,0 · 1,5 · 2,0 · 3,0 · 5,0 · 8,0 · 15,0 · 25,0 · 43,0 · 73,0 · 125,0 · 213,0 · 365,0 mm/h.

Cores exatas encontradas nas 117 CAPPI de 2026-11-12 a 11-13 (contagem de pixels), associadas às classes por ordem e
matiz (a legenda é renderizada com suavização; o casamento é inferência, não tabela publicada):

| Cor RGB na CAPPI | Classe (mm/h) |
|---|---|
| 165,255,255 | 0,0–0,5 |
| 110,200,255 | 0,5–1,0 |
| 55,145,255 | 1,0–1,5 |
| 0,90,255 | 1,5–2,0 |
| 170,255,0 | 2,0–3,0 |
| 128,206,0 | 3,0–5,0 |
| 85,156,0 | 5,0–8,0 |
| 43,107,0 | 8,0–15,0 |
| 0,57,0 | 15,0–25,0 |
| 255,255,0 | 25,0–43,0 |
| 255,192,0 | 43,0–73,0 |
| 255,128,0 | 73,0–125,0 |
| 255,64,0 | 125,0–213,0 |
| 200,200,200 | **fora da legenda** (o pixel mais comum; provável eco abaixo do menor limiar) `[sem fonte]` |

O acumulado de 1 h usa outra paleta (0,0,199; 0,121,255; 50,200,255; 120,235,255; 255,255,255; 255,247,192;
255,229,0; 255,115,0; 255,63,0), que **não bate** com a legenda publicada. Legenda do acumulado `[sem fonte]`: pedir à APAC.
