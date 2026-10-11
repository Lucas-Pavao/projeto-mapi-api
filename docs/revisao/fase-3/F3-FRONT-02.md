# F3-FRONT-02 — C3 (tiles PMTiles pré-gerados): medição e correções visuais

Veredito: **C3 APROVADO** nos limites fixados do ADR 0008 (5 MB; 3 s), sem alterá-los: tráfego do zoom 14 ao 17 = **3,39 MB** (limite 5 MB) e tempo até aparecer = **2,89 s** de mediana (limite 3 s). A folga de tempo é pequena: rodadas 2.851 / 2.891 / 3.021 ms, a 3ª passou do limite; a regra do critério é a mediana. C1 (20,95 MB; 5,8 s) segue reprovado.

## A) Tiles (projeto-mapi-api, branch `feat/terreno-cog3857`, commit `cd3c58c`, sem push)

- Etapa `tiles` em `tools/terreno/run.sh` (mais `paleta_hipsometrica.txt`, `manifesto_tiles.py`, `cortaz.py`, nota no README). Comando: `DADOS=... OUT=... tools/terreno/run.sh tiles` (depois de `vrt` e `cog3857`).
- Cadeia: `gdaldem color-relief -alpha` (COG 3857 + paleta) -> RGBA temporário -> MBTiles PNG no zoom 17 -> `gdaladdo -r average` -> corta z < 12 -> `pmtiles convert`. Intermediários apagados; checa 1,5 GB livres antes e entre as fases (disco ficou em 3,4 GB livres).
- Ferramentas: GDAL 3.10.0 (imagem por digest, a mesma do COG) e go-pmtiles 1.31.2 (binário baixado com o tarball conferido por SHA-256 no script).
- Resultado (`OUT/tiles/terrain_hypsometry.pmtiles` + `terrain_manifest.json`): zooms 12–17; bbox dos pontos + 2 km (WGS84 -35,0315 / -8,1356 / -34,8761 / -8,0346); **2.449 tiles endereçados (1.976 únicos)**; **66.925.601 bytes (63,8 MiB)**; SHA-256 `3e375bf11ff8acf5f8cfaae41234cad5a7ca1a7246021a2005a9d1c87651d219`; **tempo de geração 46,1 s** (duas execuções idênticas em hash). O manifesto traz também a paleta (texto e SHA-256), hash do VRT e do COG de entrada, versões, resampling (NEAREST no z17, AVERAGE nos menores).
- Paleta nova (a anterior, Sunset 0–30 m, mal se distinguia): 7 paradas, amarelo (0 m) a azul-índigo (20 m ou mais), mais resolução nas cotas baixas (mediana do recorte 7,7 m). Fonte única `tools/terreno/paleta_hipsometrica.txt`; o front repete em `TERRAIN_COLOR_STOPS` e a legenda é gerada dali (um teste confere a interpolação). Mudar uma exige mudar a outra e regenerar.

## B) Front (projeto-mapi-front, não commitado; patch `scratchpad/F3-FRONT-02.patch`, cópias em `scratchpad/front-before-tiles/`)

- Fonte: `pmtiles://<origin>/terrain/terrain_hypsometry.pmtiles` com o protocolo `pmtiles` do pacote **`pmtiles@4.5.0`** (versão exata, **BSD-3-Clause**, publicado em 2026-08; peer: nenhum conflito com maplibre-gl 5.24). `@geomatico/maplibre-cog-protocol` **removido** (package.json e lock). O PMTiles informa bounds e zooms ao MapLibre, que não pede tiles fora da área.
- Mantidos: interruptor (`role=switch`), desligado por padrão, fonte criada só na 1ª ativação, alternar por `visibility`, cleanup, estados carregando/erro (erro agora esconde a legenda), legenda em metros com "Datum não confirmado — cotas relativas", nada de maré (teste).
- Opacidade 0,7 e paleta nova; camada abaixo dos rótulos. Reamostragem linear (tiles já coloridos).
- nginx: `location /terrain/` ajustado para o `.pmtiles` (Range, `gzip off`); `.gitignore`/`.dockerignore` agora ignoram `public/terrain/*.pmtiles`. `public/terrain/` recebeu o `.pmtiles` e o novo manifesto (o manifesto antigo do COG foi sobrescrito).
- Observação: com `tileSize: 256` o MapLibre usa tiles de zoom = zoom do mapa + 1; no mapa em zoom 16 já usa os z17 e acima disso só amplia. Por isso o tráfego não cresce de 16 para 17.
- `tsc -b --noEmit`: 0 erros. `npm run lint`: 0 erros (3 warnings já existentes). `npm test`: 15 arquivos, **66 testes** (eram 63; +3: recolhimento no celular, abertura automática no erro, paleta/URL).

## C) Correções visuais

1. **Legenda x zoom (1920)**: a coluna da direita agora termina 17 rem acima da borda inferior; os cartões rolam dentro dela e não chegam aos botões +/−.
2. **390 px**: o cartão de terreno começa recolhido num botão de ícone (abre sob demanda; abre sozinho se a camada falhar); a legenda de risco já começava recolhida (mantido); banner compacto (uma linha de título, uma de horário, botão só com ícone; texto completo para leitor de tela e a partir de 768 px).
3. **Cartão x banner (1920)**: o banner agora termina 21 rem antes da borda direita, fora da coluna dos cartões (folga de ~40 px).
4. **Banner x botão Camadas (390)**: a coluna da direita começa em `top-48` no celular, abaixo do banner; o botão fica livre (conferido com o painel aberto).
5. Título do cartão não quebra mais (`whitespace-nowrap`); ticks da legenda posicionados pela cota real.

## D) Medição (mesmos script e método do F3-FRONT-01)

`scratchpad/pw/measure.mjs` sem alteração: Playwright 1.48.2 + Chromium headless (SwiftShader), Vite em `localhost:5179`, 1280x800, cache desligado, aquecimento sem limitação, depois 9 Mbps / 1,5 Mbps / 170 ms + CPU 4x, API desligada, AGAMENON_DERBY zoom 14 -> 17, 3 rodadas; "apareceu" = do clique até `data-terrain-status=ready` (1º `idle` com a fonte carregada).

| Rodada | Até apareceu (ms) | 1ª req. concluída (ms) | Bytes z14 | z15 | z16 | z17 | Requisições |
|---|---|---|---|---|---|---|---|
| 1 | 2.851 | 542 | 997.945 | 2.195.148 | 3.387.886 | 3.387.886 | 75 |
| 2 | 2.891 | 408 | 997.945 | 2.195.148 | 3.387.886 | 3.387.886 | 75 |
| 3 | 3.021 | 404 | 997.945 | 2.195.148 | 3.387.886 | 3.387.886 | 75 |
| **Mediana** | **2.891** | 408 | 997.945 | 2.195.148 | **3.387.886 (3,39 MB)** | | 75 |

Todas 206; cabeçalho + corpo (`encodedDataLength`). Erros de console: só os esperados com a API desligada. Comparação: (i) 3,39 MB <= 5 MB passa (C1: 20,95 MB); (ii) 2,89 s <= 3 s passa por 0,11 s (C1: 5,8 s); (iii) `pmtiles` BSD-3, compatível com MapLibre 5.

Limitações: as mesmas do F3-FRONT-01 (máquina de desenvolvimento sem GPU, CPU 4x e rede emuladas, não celular real). O tempo tem margem curta e variação de ±0,1 s; num celular real pode passar de 3 s. O pico de tráfego não inclui o mapa-base.

## E) Screenshots v2 (`docs/revisao/fase-4/screenshots/*-v2.png`, API desligada, zoom 15)

`mapa-{sem-terreno,com-terreno,terreno-falha}-api-desligada-{390x844,1920x1080}-v2.png` e `mapa-com-terreno-camadas-...-v2.png` (painel de camadas aberto). Conferido: hipsometria bem distinguível em 1920 (laranja a roxo sobre o mapa escuro), ruas e rótulos legíveis; banner não cobre o botão de camadas; zoom livre; cartão e banner separados; 390 px com bastante mapa visível.

O que ainda está errado ou discutível:
- 1920 px: com o terreno ligado, a legenda de risco é cortada no fim da coluna (rola por dentro, sem barra de rolagem visível); o texto inferior fica oculto até rolar. Alternativa: começar a legenda de risco recolhida quando o terreno estiver ligado, decisão de UX do autor.
- A coluna termina cerca de 340 px acima do fundo, mais alto que o mínimo necessário (sobra espaço vazio acima dos botões de zoom).
- 390 px: o painel de Camadas, ao abrir, cobre o cartão de terreno e a legenda (sobreposição esperada, é um popover); o rótulo "PONTOS CRÍTICOS" aparece truncado (anterior a esta tarefa).
- Opacidade 0,7 cobre parte do traço das ruas (as linhas do mapa-base ficam sob o raster; rótulos ficam acima); legível, mas é escolha visual a validar com o autor.
- Sem API: nenhum marcador de ponto ou sensor aparece (compose não foi subido).

## Reexecução

`DADOS=... OUT=... tools/terreno/run.sh tiles`; copiar `OUT/tiles/terrain_hypsometry.pmtiles` e `terrain_manifest.json` para `public/terrain/`; `npx vite --port 5179 --strictPort`; `node scratchpad/pw/measure.mjs` e `shots2.mjs`. Vite parado pelo PID; compose não iniciado.
