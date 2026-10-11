# F3-FRONT-01 — Camada de terreno (hipsometria) no mapa e medição do critério do ADR 0008

Veredito: **C1 REPROVADO** nos dois limites do critério 4 (tráfego e tempo). Pelo ADR 0008, a decisão passa para **C3 (tiles pré-gerados)**. Os limites (5 MB; 3 s) não foram alterados. A camada foi implementada e funciona; o que reprovou foi o custo de leitura do COG pelo cliente.

## A) COG em EPSG:3857 (repositório projeto-mapi-api)

Branch `feat/terreno-cog3857` (a partir de `feat/terreno-pe3d`), commit `564b318`, sem push. Worktree em `scratchpad/wt-cog`.

- Nova etapa `cog3857` em `tools/terreno/run.sh` (mais `manifesto_cog.py` e nota no README da pasta). Comando: `DADOS=... OUT=... tools/terreno/run.sh cog3857` (DADOS e OUT definidos explicitamente; RAIZ erra em worktree).
- `gdalwarp -of COG -r near`, DEFLATE, PREDICTOR=3, blocos 256, overviews internas por vizinho mais próximo, recorte = bbox dos 5 pontos + 2 km (EPSG:31985: 276159,7 / 9100150,6 / 293231,9 / 9111408,6). GDAL 3.10.0 (imagem fixada por digest).
- Resultado: `terreno/cog3857/terrain_3857.tif` = **459.269.892 bytes (438 MB)**; 17.072 x 11.215 px; pixel 1,013 m; 7 overviews; SHA-256 `e0244f364ad377012cfc4c778798f9e8b5deab7aa17a6b4db5fc57b39ecd2b0b`. VRT SHA-256 `d63e3b12...c8070`. **Tempo de geração medido: 21,8 s** (o ADR dizia "medir"; estimativa de tamanho era 0,35 GB, real 0,46 GB).
- A etapa aborta com menos de 1,5 GB livres (`checa_disco`); o driver COG usa um temporário de ~0,8 GB. Disco após a geração: 3,6 GB livres.
- `terrain_manifest.json` ao lado do COG (hash do VRT, bbox nos dois CRS, CRS, resampling, versão do GDAL, overviews, SHA-256 do COG, tempo). Não versionado o `.tif`.

## B) Front (projeto-mapi-front, não commitado)

Patch: `scratchpad/F3-FRONT-01.patch` (`diff -uN` contra as cópias em `scratchpad/front-before-cog/`).

- Dependência: o pacote `maplibre-cog-protocol` não existe no npm (404). O pacote real é **`@geomatico/maplibre-cog-protocol@0.10.0`** (versão exata; publicada em 2026-09-18). Licença **MIT**. Peer dependency `maplibre-gl ^4.5 || ^5 || ^6`; o front usa 5.24.0, instalado sem conflito (`npm ls` deduped). Mantenedores: 5 (Geomatico), não "mantenedor único" como supunha o ADR. Critério (iii): **passou**. Observação: a biblioteca exige COG em EPSG:3857, como o ADR previa.
- Arquivos novos: `utils/terrain.ts`, `components/TerrainLayer.tsx`, `TerrainControl.tsx`, `TerrainLegend.tsx` e dois arquivos de teste; editados: `MapView.tsx` (3 linhas de uso), `package.json`, `package-lock.json`, `.gitignore` (`public/terrain/*.tif`), `.dockerignore`, `nginx.conf`; novo `public/terrain/terrain_manifest.json`.
- Comportamento: interruptor rotulado "Terreno (hipsometria)" (`role=switch`, `aria-checked`), desligado por padrão. A fonte só é criada na primeira vez que liga (nada é baixado antes); depois alterna `visibility` sem recriar mapa, fonte ou camada; cleanup remove camada e fonte. Estados "Carregando terreno…" e "Não foi possível carregar o terreno" (`role=alert`; erro é sticky). Camada inserida abaixo dos rótulos do mapa-base, opacidade 0,65.
- Paleta CARTOColors Sunset (amarelo a roxo), contínua, 0 a 30 m (satura acima). Legenda gerada da mesma escala da camada, com "Cota do terreno (m)" e "Datum não confirmado — cotas relativas". Nenhuma referência a maré (um teste trava isso, ADR 0009). Paleta e faixa são escolha minha, não do ADR; a mediana do terreno no recorte é 7,7 m e o P75 é 31 m.
- nginx: `location /terrain/` com `alias /usr/share/nginx/terrain/`, `gzip off`, `Cache-Control: public, max-age=3600, must-revalidate`. **Confirmado com nginx 1.27 real**: `Range: bytes=0-1023` retorna `206`, `Content-Range: bytes 0-1023/459269892`, `Accept-Ranges: bytes`, ETag e Last-Modified presentes. O COG não entra na imagem Docker (`.dockerignore`); deve ser montado como volume. Vite (dev) também responde 206 em `public/terrain/`.
- `tsc -b --noEmit`: 0 erros. `npm run lint`: 0 erros (3 warnings já existentes). `npm test`: 15 arquivos, 63 testes (eram 52; +11: interruptor, legenda, unidade, ausência de "maré", carregando/erro, setData/alternância sem recriar, cleanup, URL e paleta).

## C) Medição

Método: Playwright 1.48.2 + Chromium (headless, SwiftShader), Vite em `localhost:5179`, viewport 1280x800, sem extensão. Cada rodada é um navegador novo, cache desligado (CDP). Aquecimento sem limitação (app + mapa-base); depois CDP `Network.emulateNetworkConditions` com **9 Mbps de descida, 1,5 Mbps de subida, 170 ms de RTT** e `Emulation.setCPUThrottlingRate` 4. A API ficou desligada (`/api/*` recusada, `/api/auth/me` simulado para passar da tela de login); isso não toca no terreno. Ponto: AGAMENON_DERBY (-8,052554; -34,894371), zoom 14. A medição usa `window.__map` injetado só pela rota do Playwright (o código do app não tem essa instrumentação).

- "Apareceu": do clique no interruptor até o primeiro evento `idle` do mapa com a fonte do terreno totalmente carregada (`isSourceLoaded`), isto é, tiles da vista baixados, decodificados e desenhados. Marcado dentro da página (`performance.timeOrigin + now`).
- Bytes: soma de `encodedDataLength` (cabeçalho + corpo) de todas as respostas de `/terrain/` (CDP `loadingFinished`); todas foram `206`. Navegação: zoom 14, depois `easeTo` 15, 16, 17, esperando a rede do terreno ficar ociosa em cada passo (zoom lido de volta: 14, 15, 16, 17).

| Rodada | Até apareceu (ms) | 1ª requisição ao COG concluída (ms) | Bytes no zoom 14 | zoom 15 | zoom 16 | zoom 17 | Requisições |
|---|---|---|---|---|---|---|---|
| 1 | 5.802 | 668 | 4.530.923 | 12.609.029 | 20.952.254 | 20.952.254 | 129 |
| 2 | 5.690 | 637 | 4.530.923 | 12.609.029 | 20.952.254 | 20.952.254 | 129 |
| 3 | 5.964 | 675 | 4.530.923 | 12.609.029 | 20.952.254 | 20.952.254 | 129 |
| **Mediana** | **5.802** | 668 | 4.530.923 | 12.609.029 | **20.952.254 (20,0 MiB; 20,95 MB)** | | 129 |

Os bytes são idênticos nas 3 rodadas (a leitura é determinística para a mesma vista). Uma execução anterior, com a versão antiga do script (paleta Brewer YlOrBr, 0 a 40 m; mesmos blocos do COG), deu o mesmo tráfego e 6,2 a 8,0 s: a paleta não altera o tráfego.

Comparação com os limites fixados:

| Critério | Limite | Medido (mediana) | Resultado |
|---|---|---|---|
| (i) tráfego da camada, zoom 14 a 17 | ≤ 5 MB | 20,95 MB (4,2 vezes o limite); só abrir no zoom 14 já dá 4,53 MB | **reprova** |
| (ii) tempo até aparecer, Fast 4G + CPU 4x | ≤ 3 s | 5,8 s | **reprova** |
| (iii) biblioteca mantida, licença, compatível com MapLibre | — | MIT, peer `^5` ok, v0.10.0 recente | passa |

**Veredito: C1 reprovado em (i) e (ii). Decisão passa para C3 (tiles pré-gerados), conforme o ADR 0008.** Limitações honestas: Chromium headless com SwiftShader (sem GPU) e a limitação de CPU 4x ficam sobre uma máquina de desenvolvimento, não sobre um celular real; a rede é emulada pelo CDP. Mesmo só pela rede, 4,5 MB a 9 Mbps levam 4 s, então (ii) reprova independentemente da CPU. Não testei ajustes no COG (blocos de 512, menos overviews, quantização) porque isso seria mudar o candidato depois de ver o resultado; se o autor quiser reabrir C1, é decisão dele e deve ser registrada no ADR.

## D) Screenshots (API desligada)

`docs/revisao/fase-4/screenshots/` (Playwright, sem limitação, zoom 15 no AGAMENON_DERBY): `mapa-sem-terreno-api-desligada-{390x844,1920x1080}.png`, `mapa-com-terreno-api-desligada-...png`, `mapa-terreno-falha-api-desligada-...png` (COG bloqueado, mostra a mensagem de erro do terreno). Sem a API, todos mostram o estado de erro (banner "Não foi possível atualizar os dados", "Sem conexão com a API", lista vazia). Não subi `postgres`/`mapi-api`; marcadores de ponto/sensor não aparecem em nenhuma imagem.

Problemas visuais encontrados:

1. **390 px: o banner de erro cobre o botão "Camadas"** (coluna da direita começa em `top-44` e o banner ocupa y 97 a 225). Com erro, o painel de camadas fica inacessível no celular. Anterior a esta tarefa.
2. **390 px: os cartões de terreno e legenda de risco ocupam cerca de 45% da altura** (junto do banner, cobrem ~65% da tela útil); o mapa fica pouco visível. O título "TERRENO (HIPSOMETRIA)" quebra em duas linhas centralizadas.
3. **1920 px: a legenda de risco aberta cobre os botões de zoom (+/−)** no canto inferior direito quando o cartão de terreno está ligado (o "+" fica quase invisível sob a legenda).
4. **1920 px: o cartão de terreno encosta no banner de erro** (borda superior a y≈196, sem folga) e a legenda fica sobre o botão de camadas por poucos pixels de distância.
5. Hipsometria: com a paleta Sunset, a vista urbana (cota 0 a 10 m) fica em tons de laranja/rosa, legível sobre o mapa escuro; rótulos de ruas continuam legíveis. A cobertura termina na borda do COG (bbox + 2 km); fora dela o mapa-base aparece sem cor.
6. Texto: nenhum texto cortado e contraste da legenda ok (zinc-300/400 sobre fundo escuro, mínimo 12 px).

## Para executar de novo

`DADOS=... OUT=... tools/terreno/run.sh vrt cog3857`; copiar o `.tif` para `projeto-mapi-front/public/terrain/`; `npx vite --port 5179 --strictPort`; scripts de medição em `scratchpad/pw/{measure,shots}.mjs`. Vite parado (PID 137056). Compose não foi iniciado.
