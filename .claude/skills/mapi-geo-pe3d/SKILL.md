---
name: mapi-geo-pe3d
description: Procedimento para usar o terreno LiDAR do PE3D (MDT 1 m, GeoTIFF float32, nodata -9999, EPSG:31985 sem .prj, voo 2016) no MAPI. Use sempre que for abrir, recortar, mosaicar, reprojetar ou amostrar os rasters do PE3D, extrair cota/declividade/bacia contribuinte por ponto crítico, comparar cota com maré, criar a tabela ponto_terreno ou a camada de terreno do mapa — mesmo que o pedido diga só "pega a altitude do ponto".
---

# MAPI — terreno PE3D

## 1. Dado de entrada

- Local: `../Modelo digital de terreno (RASTER)/MDT-*.zip` (48 folhas, ~1,1 GB zipado,
  nomenclatura de articulação SB-25-Y-C / SC-25-V-A). Não versionar no git.
- GeoTIFF float32, 1 m/pixel, nodata = **-9999**, CRS **ausente** no arquivo.
- MDT (terreno nu). MDE (superfície, com telhados/árvores) não está nesta pasta; se for
  obtido, `MDE − MDT` é proxy de edificação/vegetação.
- Voo de 2016: declarar como limitação (obras, aterros e canais posteriores não aparecem).

## 2. Cuidados obrigatórios

1. **Atribuir CRS, não reprojetar às cegas**: `EPSG:31985` (SIRGAS 2000 / UTM 25S).
   `gdal_edit.py -a_srs EPSG:31985 -a_nodata -9999 arquivo.tif` (ou `rio edit-info`).
   Confira que as coordenadas caem na RMR (E ≈ 280–300 km, N ≈ 9 090–9 130 km) antes de seguir.
2. **Nodata**: abrir sempre com máscara (`rasterio` `masked=True`). Estatística sem máscara
   é inválida — um único -9999 destrói média e declividade.
3. **Ponto crítico em WGS84** (lat/lon do banco) → transformar para 31985 com `pyproj`
   (`always_xy=True`) antes de amostrar.
4. **Referência vertical**: cota do MDT é ortométrica (referida ao datum vertical
   brasileiro — Imbituba/geoide, confirmar na documentação do PE3D). Altura da tábua de
   maré é referida ao **nível de redução (NR) da DHN** do porto do Recife. São referências
   diferentes. Enquanto o desnível NR↔datum do MDT não estiver documentado em ADR aprovado,
   **nenhuma regra de refluxo usa cota em número absoluto**; use só diferenças relativas
   entre pontos.
5. Mosaico: `gdalbuildvrt` sobre as folhas (sem copiar dado), depois recorte por área de
   interesse; gerar COG com `gdal_translate -of COG -co COMPRESS=DEFLATE -co PREDICTOR=3`.

## 3. Atributos por ponto (tabela `ponto_terreno`)

| Atributo | Procedimento | Unidade |
|---|---|---|
| `cota_rua_m` | mediana do MDT num raio de 3–5 m do ponto (evita pixel único ruidoso) | m (datum do MDT) |
| `declividade_media_pct` | `gdaldem slope -p` e média numa janela de 50–100 m | % |
| `area_contribuinte_m2` | fill sinks → flow direction (D8) → flow accumulation; área da bacia do pixel de saída mais próximo | m² |
| `curva_cota_volume` | volume acumulado abaixo de cada cota dentro da depressão (passo 5 cm) | m³ por cm |
| `profundidade_depressao_m` | `fill − MDT` no ponto | m |

Registre junto: arquivo-fonte (folha), hash do VRT, data do voo, parâmetros (raio,
janela), versão do script. Esses atributos são features do modelo e precisam entrar no CSV
de treino via junção.

## 4. Validação

Compare `cota_rua_m` com a altitude que a API estima hoje via Open-Meteo para os mesmos
pontos e reporte a diferença (média, máx.) — espera-se diferença de metros, pois o
Open-Meteo usa DEM de ~90 m.

## 5. Ferramentas

`rasterio`, `pyproj`, `numpy`, `richdem` ou `whitebox` para hidrologia; GDAL CLI para
mosaico/COG. Scripts ficam versionados com seed/parâmetros fixos; os dados não.
