# Alinhamento dos pontos de alagamento pelo local citado em notícia

- Data: 2026-11-21 · Pedido do Lucas: "identificar em notícia o ponto exato e ajustar".
- Regra seguida: das notícias só se tira o **lugar** (logradouro, número, cruzamento, referência). Datas, contagens e intensidade de
  alagamento não foram usadas para nenhum valor do modelo.
- Coordenada: geometria do OpenStreetMap (Overpass, base de 2026-11-21T23:18Z, `tools/terreno/vias_osm/alinhamento/osm_alinhamento.json`,
  ODbL) e números de casa pelo Nominatim (OSM). Aplicada em `tools/terreno/pontos_criticos.csv`, no seed
  (`MapiServiceImpl.seedPilotData`) e no banco pela migração `V39__alinhamento_pontos_por_noticia.sql`.

| Ponto | Antes (lat, lon) | Depois (lat, lon) | Deslocamento | Local citado | Fonte (acesso 2026-11-21) |
|---|---|---|---|---|---|
| AV_RECIFE_IBURA | −8,107910, −34,927138 | **−8,107984, −34,927161** | 9 m | "Av. Recife / Entrada do Ibura, Ipsep"; "Av. Recife N. 2210 - Próx. entrada do Ibura. Ponto de alagamento em ambos sentidos" (listas da CTTU) → cruzamento Av. Recife × Av. Dom Hélder Câmara (nó do OSM) | Folha PE, 06/02/2023, https://www.folhape.com.br/noticias/com-volume-de-chuva-acima-do-esperado-recife-registra-14-pontos-de/257133/ ; Folha PE, 15/05/2025, https://www.folhape.com.br/noticias/chuvas-no-recife-pontos-de-alagamento/411354/ |
| AGAMENON_DERBY | −8,052554, −34,894371 | **−8,054324, −34,896875** | 337 m | "Av Gov. Agamenon Magalhães, lado oposto ao HR, sentido Olinda" → projeção do Hospital da Restauração (OSM, way 247488808) na pista sentido norte da Agamenon (way 315073885) | Portal de Prefeitura, 04/08/2026, https://portaldeprefeitura.com.br/clima/chuva-intensa-e-mare-alta-deixam-recife-intransitavel/588860/ |
| MASCARENHAS_IMBIRIBEIRA | −8,118123, −34,904945 | **−8,107178, −34,911237** | 1.393 m | "Av. Mal Mascarenhas de Moraes entre os números 2857 e 2557 - ambos os sentidos" (CTTU); "em frente aos números 2557 e 2857 ... e também no cruzamento da via com a Rua Jean Emile Favre" → ponto médio entre o nº 2557 (−8,106837, −34,911142) e o nº 2857 (−8,107519, −34,911331) do Nominatim; o cruzamento com a Jean Emile Favre fica a ~95 m | Folha PE, 15/05/2025 (acima); Diario de Pernambuco, 04/08/2026, https://www.diariodepernambuco.com.br/vida-urbana/2026/08/11720289-pontos-de-alagamento-sao-registrados-em-recife-olinda-e-paulista-durante-chuvas-na-manha-desta-terca-4.html |
| CIN_UFPE | −8,055310, −34,951160 | **sem mudança** | — | nenhuma notícia cita o CIn ou a Av. Jornalista Aníbal Fernandes; há relatos de alagamento no campus em geral e na BR-101 (km 66 e 68) | busca de 2026-11-21 |
| JABOATAO_CENTRO | −8,106520, −35,013210 | **sem mudança; continua em revisão** | — | as notícias citam o bairro Centro e o Rio Duas Unas "nas proximidades de Jaboatão Centro", sem rua; o ponto atual cai em encosta a 64,8 m | busca de 2026-11-21 |

## O que mudou por causa do alinhamento

| | Ibura | Derby | Imbiribeira |
|---|---|---|---|
| Elementos do ESIG a 300 m | 317 → 316 | 224 → 290 | 51 → 288 |
| Bocas de lobo a 300 m | 65 → 65 | 46 → 64 | 0 → 14 |
| Área snap 10 m (m²) | 208 → 207 | 62 → 74 | 10.103 → 713 |
| Razão A(20 m)/A(5 m) | 388,6 → 111,3 (trava) | 572,1 → 415,0 (trava) | 1,3 → 1,5 |
| Profundidade da depressão (m) | 0,098 → 0,218 | 0,0 → 0,0 | 0,271 → 0,0 |
| Capacidade pelo ESIG | entrada 0,093 m³/s; cano sem dado | entrada 0,279 m³/s (3 BL); cano sem dado (trecho sem cota na outra ponta) | cano 0,226 m³/s (D 0,6 m, S 0,0018); entrada sem dado (nenhuma BL no alcance) |
| Limpezas casadas na 156 (150 m) | 53 → 53 | 24 → 35 | 4 → **130** |
| λ_re (por dia) | 0,0234 | 0,0098 | **0,0333** |

Fontes: `docs/extratos/esig-drenagem/resumo_por_ponto.csv`, `docs/terreno/area-contribuicao/area_regras.csv`,
`MAPI/terreno/onda14/ponto_terreno.csv`, `scripts/capacidade-galeria/capacidade_por_ponto.csv`,
`docs/extratos/emlurb-156/entupimento/reentupimento.csv`. Os valores "antes" estão no histórico do git (branches de cada prompt).

Status no alerta depois do alinhamento: CIn e Imbiribeira com terreno OK; Ibura e Derby em revisão pela trava da área; Jaboatão em
revisão pela posição.
