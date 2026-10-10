# Terreno PE3D no MAPi

Tarefas F3-GEO-01 (processar rasters), F3-GEO-04 (atributos por ponto, só em CSV) e F3-GEO-05
(comparação com a altitude atual). Data da execução: 2026-10-10. Termos conforme `docs/GLOSSARIO.md`.

Reprodução: `tools/terreno/run.sh` (um comando, ≈ 80 s; ver `tools/terreno/README.md`). Saídas em
`../terreno/` (fora do git). Toda cota deste documento é **ortométrica** (datum do MDT) e **não pode
ser comparada com maré** até o ADR 0009 (referência vertical) ser aprovado.

## 1. Procedimento

```mermaid
flowchart LR
  Z[48 zips MDT] -->|/vsizip, sem extrair| INV[inventário + articulação]
  Z --> VRT[mosaico.vrt<br/>-a_srs EPSG:31985, nodata -9999]
  VRT --> COB[cobertura dos 5 pontos]
  VRT --> COG[COG 1 km x 1 km por ponto]
  VRT --> ATR[janela 2 km: slope, fill, D8,<br/>acumulação, curva cota-volume]
  ATR --> CSV[ponto_terreno.csv + meta.json]
  CSV --> CMP[comparação com altitudeM]
```

- Ferramentas: imagem `ghcr.io/osgeo/gdal:ubuntu-small-3.10.0`, digest
  `sha256:383e17c221d48031c5ac4fe7500d95adccb05d719a9685542c1e8d2d84ef50e0` (GDAL 3.10.0, Python 3.12.3,
  numpy 1.26.4; fonte: `ponto_terreno.meta.json`). Python roda com `-I`. Nada instalado no sistema.
- Mosaico: `gdalbuildvrt -a_srs EPSG:31985 -srcnodata -9999 -vrtnodata -9999` sobre os 48
  `/vsizip/<zip>/<folha>.tif`. SHA-256 do VRT: `d63e3b123507631b276eb98a1d247d3337655e50d4456168b61b582dc5408070`
  (fonte: `terreno/mosaico.vrt.sha256`; o VRT referencia os caminhos de montagem `/dados/...` do container).
- Hidrologia sem biblioteca externa: `richdem` não compila no ambiente (falha ao construir a roda).
  Implementação própria em `tools/terreno/terreno.py`: fill por Priority-Flood (Barnes et al., 2014) sem
  epsilon, que já devolve a direção D8 (árvore de descoberta); acumulação por soma em ordem inversa.
  Declividade: `gdaldem slope -p` (Horn) via `gdal.DEMProcessing`. Validação independente
  contra `richdem`/`whitebox` **não foi feita** (pendência).
- Disco: saídas totais 12 MB (fonte: `du -sh terreno`), muito abaixo do teto de 1 GB. `/home` ficou em 4,5 GB livres (fonte: `df -h`).

## 2. Inventário (F3-GEO-01)

| Item | Valor | Fonte |
|---|---|---|
| Folhas | 48 zips, 1.111.529.514 bytes zipados | `terreno/inventario.csv` (soma de `zip_bytes`) |
| Conteúdo por zip | 1 `.tif` + 1 `.tfw` | `unzip -l` |
| Tipo / nodata | Float32 / -9999 em todas | `inventario.csv` |
| Pixel | 1 m em 46 folhas; 0,99986 m em 2 (`SO-B-III`, `SO-B-IV`) | `inventario.csv` |
| Extensão (EPSG:31985) | E 276.017–296.878 m; N 9.096.714–9.124.483 m | `inventario.csv` (min/max) |
| Mosaico VRT | 20.861 x 27.770 px, pixel 0,999994 m | `terreno/mosaico_gdalinfo.txt` |
| Pixels válidos | 381.892.319 de 579.309.970 do retângulo do VRT (66 %) | `estimativas_cog.json` |
| Cota mínima / máxima | -4,55 m / 135,68 m | `inventario.csv` (z_min, z_max) |

Achados que **divergem do briefing**:

1. Os `.tif` **têm CRS embutido** (SIRGAS 2000 / UTM 25S, idêntico ao EPSG:31985 por `IsSame` nas 48 folhas;
   coluna `crs_igual_epsg31985`). O `-a_srs EPSG:31985` do VRT é redundante, mas mantido por segurança.
2. O `.tfw` **não é usado** pelo GDAL (o GeoTIFF já tem georreferência). Conferido à parte: em 48/48 folhas o `.tfw`
   coincide com o geotransform do `.tif` (canto = centro do pixel - 0,5 pixel; coluna `tfw_confere_com_geotransform`).
3. A extensão leste-oeste começa em E = 276 km, fora da faixa 280–300 km do briefing; N dentro de 9.090–9.130 km.
   Os 5 pontos têm E entre 278,2 e 291,2 km e N entre 9.102 e 9.109 km, coerentes com a RMR (fonte: `cobertura.csv`).
4. Duas folhas com pixel 0,99986 m forçam o VRT a pixel médio de 0,999994 m (reamostragem por vizinho mais próximo,
   deslocamento máximo de ≈ 0,02 m ao longo de uma folha). Áreas usam o tamanho real do pixel; o efeito (6 ppm) é desprezível.
5. A grade tem 66 % de pixels válidos: várias folhas de borda de mar/rio são parciais (ex.: `SO-B-IV` com 671.155 de ~8,6 M).

Articulação das folhas (contorno em EPSG:4326): `terreno/articulacao_folhas.geojson` (48 polígonos).

## 3. Cobertura dos 5 pontos críticos

Coordenadas: seed em `MapiServiceImpl.seedPilotData()` (linhas 573–620), transformadas de WGS84 para 31985 pelo GDAL (`osr`, ordem x=lon).

| Ponto | E / N (m) | Pixel válido | Folha de origem | Cota do pixel (m) |
|---|---|---|---|---|
| AV_RECIFE_IBURA | 287.648 / 9.103.269 | sim | SC-25-V-A-III-1-NO-F-III | 1,427 |
| CIN_UFPE | 284.973 / 9.109.074 | sim | SC-25-V-A-III-1-NO-C-II | 7,665 |
| AGAMENON_DERBY | 291.232 / 9.109.409 | sim | SC-25-V-A-III-1-NO-D-II | 2,640 |
| MASCARENHAS_IMBIRIBEIRA | 290.100 / 9.102.151 | sim | SC-25-V-A-III-1-NO-F-IV | 2,069 |
| **JABOATAO_CENTRO** | 278.160 / 9.103.376 | **não (nodata)** | nenhuma | — |

(fonte: `terreno/cobertura.csv`.) **JABOATAO_CENTRO está dentro do retângulo do mosaico, mas em área sem folha**;
o pixel válido mais próximo está a **1.410 m** (`dist_pixel_valido_mais_proximo_m`). Não há atributo de terreno
para esse ponto com os 48 zips atuais. Os 4 pontos cobertos têm 100 % de pixels válidos na janela de 1 km.

## 4. Recortes COG e tamanhos

`gdal_translate -of COG -co COMPRESS=DEFLATE -co PREDICTOR=3 -projwin ...` (janela 1.000 x 1.000 px alinhada à grade
do mosaico, centrada no ponto; fonte dos limites: `cobertura.csv`).

| Recorte | Bytes medidos |
|---|---|
| AV_RECIFE_IBURA.tif | 3.020.119 |
| CIN_UFPE.tif | 2.802.963 |
| AGAMENON_DERBY.tif | 2.908.331 |
| MASCARENHAS_IMBIRIBEIRA.tif | 2.939.427 |

(fonte: `terreno/recortes_tamanhos_bytes.txt`.) ≈ 2,8–3,0 MB por km², ≈ 2,9 bytes/pixel; float32 com ruído do LiDAR comprime pouco (4 bytes -> ≈ 2,9).

**Estimativas (nada gerado)** para o ADR de armazenamento (F3-GEO-02). Método: bytes por pixel válido de COG
(DEFLATE + PREDICTOR=3, com overviews do driver) de 4 folhas inteiras gravadas em memória (`/vsimem`) — 2,94; 3,12; 3,17 e 3,47 B/pixel —
multiplicados pelos pixels válidos (fonte: `terreno/estimativas_cog.json`).

| Produto | Pixels válidos | Tamanho estimado (min – máx; média) |
|---|---|---|
| COG do mosaico inteiro | 381.892.319 | 1,12 – 1,33 GB; ≈ 1,21 GB (≈ o tamanho dos zips, 1,11 GB) |
| COG do bbox dos 5 pontos + 2 km (E 282.973–293.232; N 9.100.151–9.111.409) | 110.516.788 | 0,32 – 0,38 GB; ≈ 0,35 GB |

A amostra tem 4 folhas, todas da zona urbana; folhas rurais/de relevo mais suave podem comprimir diferente (incerteza não quantificada).
O bbox inclui Jaboatão Centro? Não: o bbox cobre os 4 pontos com terreno (o ponto de Jaboatão está sem dado).

## 5. Atributos por ponto (F3-GEO-04)

Parâmetros (fonte: `ponto_terreno.meta.json`): cota = mediana dos pixels válidos com centro a ≤ 3 m e a ≤ 5 m; declividade =
média de `gdaldem slope -p` em janela quadrada de lado 50 m e 100 m; hidrologia numa **janela de 2 km x 2 km** centrada no
ponto (2.000 x 2.000 px); snap da acumulação: pixel de maior acumulação a ≤ 10 m; curva: passo 5 cm, 0 a 2 m acima do fundo.

| Ponto | cota_rua_m (3 m) | cota_rua_m (5 m) | decliv. 50 m (%) | decliv. 100 m (%) | prof. depressão (m) | área contribuinte (m²) |
|---|---|---|---|---|---|---|
| AV_RECIFE_IBURA | 1,392 | 1,401 | 6,25 | 6,24 | 0,098 | 573.046 |
| CIN_UFPE | 7,679 | 7,683 | 4,08 | 3,69 | 0,155 | 84.947 |
| AGAMENON_DERBY | 2,580 | 2,571 | 4,60 | 4,40 | 0,000 | 61 |
| MASCARENHAS_IMBIRIBEIRA | 2,068 | 2,077 | 3,12 | 3,73 | 0,271 | 34 |
| JABOATAO_CENTRO | sem dado | | | | | |

(fonte: `terreno/ponto_terreno.csv`; mais colunas: mín/máx na vizinhança, nº de pixels, declividade máxima, `fill_no_ponto_m`, exutório.)
Pixels usados: 26–30 (raio 3 m) e 76–81 (raio 5 m); 2.500 e 10.000 (janelas de declividade).

Curva cota-volume (`terreno/curva_cota_volume.csv`, 41 níveis por ponto, colunas `volume_acumulado_m3` e `m3_por_cm`). Definição:
bacia = células cujo caminho D8 passa pelo exutório (pixel de snap); fundo = célula de menor cota da bacia a ≤ 50 m do ponto;
V(z) = soma de (z - cota) das células da bacia ligadas ao fundo por caminho (8 vizinhos) com cota < z.

| Ponto | Fundo (m) e distância ao ponto | Área molhada a +2 m (m²) | Volume a +2 m (m³) |
|---|---|---|---|
| AV_RECIFE_IBURA | -0,130 a 49,9 m | 31.064 | 14.798 |
| CIN_UFPE | 7,540 a 14,3 m | 76.434 | 57.783 |
| AGAMENON_DERBY | 2,168 a 9,8 m | 62 | 101 |
| MASCARENHAS_IMBIRIBEIRA | 2,045 a 3,1 m | 35 | 57 |

Interpretação com cautela:

- **Sensibilidade ao snap da área contribuinte** (colunas `area_contribuinte_m2_snap{0,5,10,20}m`, em m²): Ibura 52 / 572.882 / 573.046 / 583.696;
  CIn 3 / 150 / 84.947 / 85.524; Agamenon 0 / 35 / 61 / 13.396; Mascarenhas 0 / 30 / 34 / 167. A área depende de qual pixel é tomado como "o ponto";
  o valor da tabela (raio 10 m) é uma escolha, não uma medida. Para Agamenon e Mascarenhas a bacia é de dezenas de m²: o ponto não está sobre um eixo de
  drenagem do MDT (que não vê bueiros, galerias nem edificações), então a área derivada **não** representa a bacia de captação real das galerias.
- O MDT é terreno nu (sem edificações): o escoamento por vias entre quadras é plausível, mas o fluxo por galerias subterrâneas não existe no modelo.
- Profundidade de depressão: pequena (0 a 0,27 m) e depende da resolução de 1 m; AGAMENON_DERBY dá 0 (o pixel não está em depressão fechada).
  Máximo num raio de 5 m: 0,286 / 0,240 / 0,000 / 0,302 m (Ibura / CIn / Agamenon / Mascarenhas).
- Curva do Ibura: o fundo está 50 m do ponto e 1,5 m abaixo da cota da rua; a curva descreve o armazenamento da bacia a jusante/em torno, não só a depressão sob o ponto.
- Efeito de borda: janela de 2 km; fora dela o terreno é tratado como saída (nodata). Nenhuma bacia tocou a borda (`bacia_toca_borda_janela = False`
  nos 4 pontos) nem ficou adjacente a nodata (`bacia_adjacente_a_nodata = False`); a maior bacia (Ibura, 0,57 km²) cabe com folga. Em 2 km, uma bacia > 4 km² seria truncada (limite inferior).
- Declividade máxima na janela de 50 m chega a 62 % no Ibura (`declividade_max_pct_50m`: aterro/talude; não é rua).

Registro do que gerou os números (`ponto_terreno.meta.json`): folha de origem por ponto, SHA-256 do VRT e do `terreno.py`
(`c241579742200f89498d13f531b27aa362b08baddb04faffc3580d0777ad21dd`), versões de software e parâmetros. Versão do script: o hash do arquivo (não há commit ainda).

## 6. Comparação com a altitude atual (F3-GEO-05)

Origem da `altitudeM`: `MapiServiceImpl.createFloodPoint` (linhas 187–193) copia `weather.elevation()` da resposta do Open-Meteo `/forecast`
(`WeatherServiceImpl.getWeatherData`) quando o request não traz altitude; o seed não traz. **Não é valor fixo.** Os valores gravados no banco não foram lidos
(banco não tocado). Reconsultei o mesmo endpoint em 2026-10-10 ~13:18 UTC (`terreno/altitude_open_meteo.csv`); presume-se igualdade com o gravado, **sem confirmação**.
Risco adicional: se o Open-Meteo estivesse fora na criação, o fallback (`WeatherServiceImpl`, `getWeatherDataFallback`) devolve `elevation = 0.0` e este seria gravado como altitude.

| Ponto | altitudeM (Open-Meteo) | cota_rua_m (3 m) | Diferença MDT - Open-Meteo (m) |
|---|---|---|---|
| AV_RECIFE_IBURA | 3,0 | 1,392 | -1,608 |
| CIN_UFPE | 11,0 | 7,679 | -3,321 |
| AGAMENON_DERBY | 8,0 | 2,580 | -5,420 |
| MASCARENHAS_IMBIRIBEIRA | 3,0 | 2,068 | -0,932 |
| JABOATAO_CENTRO | 62,0 | sem dado | — |

(fonte: `terreno/comparacao_altitude.csv`.) n = 4: diferença média **-2,82 m** (todas negativas, logo média absoluta também 2,82 m); máximo em módulo **5,42 m** (Agamenon).

Fonte da diferença (o que se sabe e o que é hipótese):

- Escala: o Open-Meteo entrega um valor por célula de DEM global de ordem de 90 m (a documentação do Open-Meteo não foi consultada; valor de ~90 m vem da skill `mapi-geo-pe3d` — `[sem fonte verificada]`), arredondado a metro inteiro nos 5 casos observados.
  O MDT mede 1 m e a mediana de ≈ 28 pixels numa rua. Um ponto de rua plana próximo a morro ou canal recebe a média da célula inteira.
- Hipótese (não testada): o DEM global tende a ser de superfície (copas, telhados), o que o eleva em relação ao terreno nu; as quatro diferenças têm o mesmo sinal, o que é coerente, mas 4 pontos não provam.
- Datum: o datum vertical do DEM global e o do MDT PE3D podem diferir; **não verificado** (ver ADR 0009).
- Tempo: MDT é de 2016; Open-Meteo não tem data de referência declarada aqui.
- Implicação: a `altitudeM` atual não serve como cota da rua; use `cota_rua_m` do MDT (com a ressalva da referência vertical).

## 7. Limitações

1. **Voo de 2016** (ano conforme a skill `mapi-geo-pe3d`; os arquivos de uma folha têm data 30–31/03/2016 no zip, que não é necessariamente a data do voo): obras, aterros, canais e drenagem posteriores não aparecem.
2. **MDT, não MDE**: só terreno nu. `MDE - MDT` (edificação/vegetação) indisponível; não há como separar rua de calçada elevada nem ver telhados.
3. **Sem galerias, bueiros nem canais cobertos**: a hidrologia mostra só escoamento superficial; área contribuinte e curva cota-volume são proxies.
4. **Jaboatão Centro sem cobertura** (1.410 m do dado mais próximo); precisa de outras folhas ou de fonte alternativa.
5. **Efeito de borda**: janela de 2 km; sem truncamento nos 4 pontos, mas não generalizável para pontos em bacias maiores.
6. **Sensibilidade de snap** da área contribuinte (seção 5) e resolução de 1 m para profundidades de depressão de poucos centímetros.
7. **Hidrologia própria**, sem validação cruzada com `richdem`/`whitebox`; testes unitários não escritos.
8. **Referência vertical pendente**: cota ortométrica; nenhuma regra de refluxo deve usar a cota em número absoluto até o ADR 0009 ser aprovado. Este documento não compara nada com maré.
9. Amostra de 4 folhas para estimar o tamanho do COG; ruído do LiDAR domina a taxa de compressão.
10. Duas folhas têm pixel 0,99986 m; VRT reamostra por vizinho mais próximo (efeito de ≈ 0,02 m).
11. `ponto_terreno` está só em CSV; a tabela no banco depende da migração (fora do escopo; banco não alterado).
