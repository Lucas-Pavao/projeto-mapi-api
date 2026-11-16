# Geocodificação das ocorrências: cache e contagens de casamento

Medido em 2026-11-16. Consultas: `docs/extratos/sql/casamento-ocorrencias-por-raio.sql`.

## O problema

As 225 ocorrências de alagamento do CKAN têm só endereço; a coordenada vem do Nominatim. Entre o `tide_db` de 2026-11-08 (antes da recarga) e o banco atual, as 225 são as mesmas (mesma chave), mas 50 mudaram de coordenada mais de 50 m e 29 mais de 500 m. A contagem de ocorrências perto dos pontos mudava a cada carga.

## O que mudou (V28)

- Tabela `geocode_cache`: endereço saneado, bairro e cidade normalizados → coordenada, nível, fonte, origem, data e versão. Uma linha ativa por chave.
- A carga de ocorrências lê o cache (`GeocodeCacheService`) e só chama o Nominatim para endereço novo; o resultado é gravado com fonte e data. Falha do geocodificador não entra no cache.
- Na subida da aplicação com o cache vazio, ele é preenchido com as coordenadas de `flood_occurrence` (182 chaves distintas de endereço e bairro; nenhuma com duas coordenadas), que são as dos casamentos vigentes. A origem de cada linha diz a carga de onde veio.

## Snapshot de 2026-10-10: não recuperado

O pedido era preencher o cache com as coordenadas do snapshot de 2026-10-10, que produziram os números já citados (3 ponto-dia a 500 m). O arquivo `MAPI/snapshots/tide_db-2026-10-10-pos-carga.dump` não está mais na pasta (resta só o `.sha256`), o dump de 2026-10-21 tem `flood_occurrence` vazia, e os arquivos congelados da F1 guardam ponto-dia, não coordenada por ocorrência. O cache foi preenchido com as coordenadas atuais. Se o dump de 2026-10-10 reaparecer, as coordenadas dele entram como nova versão do cache, sem apagar a atual.

## Casamento por raio, lado a lado

Ocorrência dentro do raio de um ponto crítico ativo (5 pontos). "Ponto-dia" = pares distintos de ponto e dia local.

| Raio | Snapshot 2026-10-10 (documentado) | `tide_db` 2026-11-08, antes da recarga | Banco atual (2026-11-16, no cache) |
|---|---|---|---|
| 500 m | 9 pares, 3 ponto-dia | 0 | 0 |
| 1 km | 6 ponto-dia | 9 pares, 5 ponto-dia, 5 dias, 3 pontos | 10 pares, 6 ponto-dia, 6 dias, 3 pontos |
| 2 km | 34 ponto-dia, 13 dias | 95 pares, 45 ponto-dia, 13 dias, 4 pontos | 96 pares, 45 ponto-dia, 13 dias, 4 pontos |
| 3 km | 52 ponto-dia, 14 dias | 204 pares, 54 ponto-dia, 16 dias, 4 pontos | 199 pares, 54 ponto-dia, 16 dias, 4 pontos |

A coluna de 2026-10-10 vem de `docs/snapshots/2026-10-10-primeira-carga.md` e `docs/MEMO-ESCASSEZ-DE-ROTULO.md` (F2-BACK-16 §2a) e não pôde ser recalculada. As outras duas foram calculadas agora, com a mesma consulta.

**Leitura.** A contagem citada em documentos anteriores ("3 ponto-dia a 500 m") vale só para a geocodificação de 2026-10-10. Toda contagem de casamento depende da geocodificação; a referência reprodutível daqui em diante é a do cache (`geocode_cache`, versão ativa).
