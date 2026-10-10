# F2-BACK-16 — Correções da carga CKAN, deduplicação e sensibilidade ao raio

Branch `fix/ckan-carga` (a partir de `integracao/fase-2`, `70c1cbc`). Banco: `mapi-db`, volume `projeto-mapi-api_postgres_ha_data` (nenhum volume novo; `docker volume ls` antes e depois idêntico). Nada foi apagado: sem `DELETE`, `DROP`, `TRUNCATE` ou `down -v`. Os números abaixo vêm de consultas ao banco em 2026-10-10, após o reprocessamento do CKAN 2021–2026 (`POST /api/admin/ingestion/historical-civil-defense?years=6`, duas execuções: a primeira foi interrompida pelo portal no recurso de 2023 com `I/O error`, a segunda completou).

## Resumo para decisão

1. **A unidade "ponto a 500 m" não sustenta o desenho**: 197 ocorrências distintas de alagamento, só 3 casam a 500 m (1,5%), 10 a 1 km, 55 a 2 km, 154 a 3 km. O gargalo é o rótulo, não o raio sozinho: ver item 3.
2. **A cidade inteira tem só 15 dias com alagamento registrado** (1 em 2021, 6 em 2023, 8 em 2024; 2022, 2025 e 2026 sem dado utilizável). Cada dia tem 1 a 28 ocorrências em até 20 bairros. O rótulo "dia × cidade" tem 15 positivos, mas "dia × ponto" a 3 km tem 52 pares ponto-dia em 14 dias: os pontos não adicionam informação independente (os mesmos 13 a 14 dias aparecem em todos os pontos).
3. **A geocodificação é grosseira**: as 197 ocorrências têm 77 coordenadas distintas; 131 delas dividem uma coordenada com outras 2 ou mais (até 13 na mesma). O geocodificador cai para o centróide do bairro quando não acha a rua (log `Fallback Nível 3 (Bairro)`), e o banco não grava o nível. Distância mediana da ocorrência ao ponto mais próximo: 2 456 m. Casar a 500 m com essas coordenadas mede o acaso do centróide, não o alagamento no ponto.
4. **O CKAN 2022 não é recuperável com o parser atual** (achado novo, ver "2022"): as colunas do recurso estão deslocadas. Não é um registro isolado.

## Código (commit `97e74b6`)

**a) Data ilegível nunca derruba o lote.** `OccurrenceIngestionServiceImpl.classify`: se o texto de `Data` não parseia ou a forma normalizada passa de 40 caracteres, o registro vai para `ingestion_quarantine` com regra `TS` e motivo `TS_UNPARSEABLE`, qualquer que seja o tipo de ocorrência (antes só os "alagamento" chegavam a ser avaliados). `source_ts_text` e `source_ts_norm` são aparados em 80 e 40 caracteres (V4); o texto completo permanece em `payload` (JSONB), que é a evidência. A identidade do bruto (`station_id = recurso#_id`) não depende do texto, então o aparo não colide. Teste: `OccurrenceIngestionIntegrationTest.dataTextoLivreLongoVaiParaQuarentenaENaoDerrubaOLote` (texto de 90 caracteres, como o `_id` 27476 de 2022).

**b) Deduplicação entre recursos.** Identidade de origem da ocorrência = **`_id` do datastore + início do intervalo (`occurred_from`)**. O `_id` sozinho repete entre anos (é contador por recurso); com a data, a verificação no banco é exata: 591 linhas legadas colapsam em exatamente 197 identidades (`count(DISTINCT (ckan_record_id, occurred_from))`), igual ao ÷3 do relatório da primeira carga. A ocorrência **canônica** é a de menor `id`. Na gravação, uma ocorrência já registrada por outro recurso não é regravada nem geocodificada de novo (`OccurrenceRepository.knownInOtherResources`); o bruto continua registrando a repetição (auditoria). Nas leituras, `geocoded()` (rematch) e `matchedOccurrences()` (export) só enxergam canônicas (`CANONICAL` em `OccurrenceRepository`). Testes: `recursosDuplicadosDoMesmoAnoEntramUmaVezSo`, `duplicatasJaGravadasNaoContamNoCasamento`.

**Sem migração, sem RETRACTED.** `flood_occurrence` não tem coluna de status (só `observation` tem), então não há "marcar RETRACTED" para ocorrências. As 394 linhas duplicadas legadas (591 − 197) continuam no banco como evidência e **deixam de contar** pelo filtro canônico em todo leitor do código. **Consulta SQL manual (inclusive as deste relatório) precisa repetir o filtro**; sem ele, 591 em vez de 197. Se o autor preferir uma marca persistente (`status` ou `canonical_id`), é uma migração V11 aditiva, que não foi criada.

`./mvnw -q clean test`: verde (166 testes).

## Reprocessamento (execução 2)

| Ano | Recursos | Registros por recurso | Resultado do pipeline |
|---|---|---|---|
| 2021 | 3 | 64 855 | `OK`, 0 brutos novos, 2 em quarentena `TS_UNPARSEABLE` por recurso, 0 ocorrências gravadas (já havia) |
| 2022 | 3 | 90 425 | `OK` (antes `FAILED`), 90 425 brutos novos por recurso, **90 425 em quarentena por recurso** (ver abaixo) |
| 2023 | 3 | 83 164 | `OK`, 0 brutos novos, 0 quarentena, 0 gravadas (duplicatas e existentes) |
| 2024 | 3 | 80 173 | `OK`, 0 brutos novos, 1 quarentena por recurso (`_id` 75524, `Data` nula), 0 gravadas |
| 2025 | 3 | 1 | `OK`, 1 bruto inalterado por recurso |
| 2026 | 0 | n/a | o portal não publica recurso |

`ingestion_quarantine` `TS_UNPARSEABLE`: 271 275 linhas novas (3 × 90 425 do 2022 + as de 2021/2024). `raw_observation` do CKAN: 956 200 linhas, 391 MB de payload; `hypertable_size` 1 746 MB (era 1 486 MB). A primeira passagem do 2024 mostrou que registros com `Data` nula (1 em 2024, 2 em 2021) eram silenciosamente ignorados antes; agora ficam visíveis na quarentena.

### 2022: colunas deslocadas no recurso

No recurso de 2022 os nomes das colunas não correspondem ao conteúdo. Exemplo (`_id` 35006): `Regional` = 2022, `Ano` = "Julho", `"MÃªs"` (nome com mojibake) = `2022-07-12T00:00:00`, `Data` = "Alagamentos", `Ocorrencia` = "Vistoria", `Solicitacao` = "Avenida Joaquim Ribeiro", `Endereco` = lixo (` ?_$."`). Em 84 879 dos 90 425 registros `Data` é vazia. O `_id` 27476 (texto "Desabamento Parcial de Obras de Conteno / Drenagem" em `Data`) era sintoma disso, não um registro isolado. A data real está em `"MÃªs"` e o tipo em `Data`, mas o endereço está corrompido e só a rua (sem número) sobrevive em `Solicitacao`. Há **28 registros** com "alagamento" em `Data` por recurso (28 `_id` distintos, 3 dias: 2022-05-28 a 2022-07-12). Isso é pouco para justificar um parser com mapeamento de colunas alternativo e geocodificação só por rua; fica como **decisão do autor** (não implementado). Enquanto isso, 2022 está fora do rótulo, corretamente em quarentena com o payload preservado.

## 1. Ocorrências distintas por ano, precisão e localização

Consulta (canônicas; `OWN_COORDINATES` não existe no CKAN: o dataset tem `Endereco`/`Bairro`/`Localidade`, sem latitude/longitude):

```sql
SELECT extract(year FROM o.occurred_from AT TIME ZONE 'America/Recife')::int AS ano,
       o.temporal_precision AS precisao, count(*) AS distintas,
       count(*) FILTER (WHERE o.geocode_method = 'OWN_COORDINATES')  AS coord_propria,
       count(*) FILTER (WHERE o.geocode_method = 'GEOCODING_SERVICE') AS geocodificada,
       count(*) FILTER (WHERE o.geocode_method = 'NOT_GEOCODED')      AS sem_localizacao
FROM flood_occurrence o
WHERE NOT EXISTS (SELECT 1 FROM flood_occurrence d WHERE d.ckan_record_id = o.ckan_record_id
                  AND d.occurred_from = o.occurred_from AND d.id < o.id)
GROUP BY 1, 2 ORDER BY 1, 2;
```

| Ano | Precisão | Linhas legadas (3 recursos) | Distintas | Coordenada própria | Geocodificada | Sem localização |
|---|---|---|---|---|---|---|
| 2021 | DAY | 3 | 1 | 0 | 1 | 0 |
| 2022 | n/d | 0 | 0 (quarentena, ver acima) | n/d | n/d | n/d |
| 2023 | DAY | 225 | 75 | 0 | 75 | 0 |
| 2024 | DAY | 363 | 121 | 0 | 121 | 0 |
| 2025 | n/d | 0 | 0 (1 registro, sem alagamento) | n/d | n/d | n/d |
| 2026 | n/d | n/a | n/a (sem recurso no portal) | n/d | n/d | n/d |
| **Total** | DAY 197, HOUR 0 | 591 | **197** | 0 | 197 | 0 |

"Geocodificada" inclui o fallback por bairro (item 3 do resumo); o banco não distingue o nível. Indicador: 77 coordenadas distintas para 197 ocorrências.

```sql
WITH canon AS (<canônicas como acima>), c AS (SELECT geocoded_lat, geocoded_lon, count(*) n FROM canon GROUP BY 1,2)
SELECT count(*) AS coordenadas_distintas, sum(n) AS ocorrencias,
       sum(n) FILTER (WHERE n >= 3) AS em_coordenada_compartilhada_ge3, max(n) AS max_na_mesma_coordenada FROM c;
-- 77 | 197 | 131 | 13
```

## 2. Sensibilidade ao raio

`linkage_config`: 500 m (id 3, pipeline), 1 000 m (id 4), 2 000 m (id 5), 3 000 m (id 2, da V6); os ids 4 e 5 são novos (`created_by = 'F2-BACK-16'`), os antigos ficaram intactos. O `rematch` do serviço só roda com o raio de `mapi.ingestion.occurrence-radius-m` e não tem endpoint; os casamentos de 1 000, 2 000 e 3 000 m foram gravados por SQL com a mesma regra (Haversine, R = 6 371 000 m, ponto mais próximo dentro do raio, só canônicas geocodificadas), arquivo `rematch` abaixo. O de 500 m confere com o do código: as 3 canônicas do id 3 aparecem no SQL e nenhuma outra (as 9 linhas do id 3 incluem 6 duplicatas legadas, ignoradas pelo filtro canônico).

```sql
INSERT INTO linkage_config (kind, radius_m, method, created_by, note)
SELECT 'OCCURRENCE_POINT', r, 'NEAREST_WITHIN_RADIUS', 'F2-BACK-16', 'sensibilidade de raio (F2-BACK-16); [sem fonte] como critério'
FROM (VALUES (1000.0), (2000.0)) v(r)
WHERE NOT EXISTS (SELECT 1 FROM linkage_config c WHERE c.kind='OCCURRENCE_POINT' AND c.radius_m = v.r AND c.method='NEAREST_WITHIN_RADIUS');

CREATE TEMP VIEW occ AS
SELECT o.id, o.geocoded_lat lat, o.geocoded_lon lon FROM flood_occurrence o
WHERE o.geocoded_lat IS NOT NULL AND NOT EXISTS (SELECT 1 FROM flood_occurrence d WHERE d.ckan_record_id=o.ckan_record_id
      AND d.occurred_from=o.occurred_from AND d.id<o.id);
CREATE TEMP VIEW dist AS
SELECT o.id occ_id, p.id point_id,
  2*6371000.0*asin(sqrt(power(sin(radians(p.latitude-o.lat)/2),2)
   + cos(radians(o.lat))*cos(radians(p.latitude))*power(sin(radians(p.longitude-o.lon)/2),2))) d
FROM occ o CROSS JOIN flood_points p;
INSERT INTO occurrence_point_match (occurrence_id, flood_point_id, distance_m, radius_m, linkage_config_id, method, rule_version, matched_at)
SELECT n.occ_id, n.point_id, n.d, c.radius_m, c.id, 'NEAREST_WITHIN_RADIUS', '2026.10.1', now()
FROM linkage_config c
JOIN LATERAL (SELECT DISTINCT ON (occ_id) occ_id, point_id, d FROM dist ORDER BY occ_id, d) n ON n.d <= c.radius_m
WHERE c.kind='OCCURRENCE_POINT' AND c.radius_m IN (500,1000,2000,3000)
ON CONFLICT ON CONSTRAINT occurrence_match_unique DO NOTHING;
```

### 2a. Total por raio (casamento do pipeline: cada ocorrência vai ao ponto mais próximo)

```sql
-- canon como acima
SELECT c.radius_m::int AS raio_m,
       count(DISTINCT m.occurrence_id) AS ocorrencias_casadas,
       count(DISTINCT (m.flood_point_id, (k.occurred_from AT TIME ZONE 'America/Recife')::date)) AS eventos_dia_ponto,
       count(DISTINCT (k.occurred_from AT TIME ZONE 'America/Recife')::date) AS dias_distintos
FROM occurrence_point_match m JOIN linkage_config c ON c.id=m.linkage_config_id AND c.kind='OCCURRENCE_POINT'
JOIN canon k ON k.id=m.occurrence_id GROUP BY 1 ORDER BY 1;
```

| Raio | Ocorrências casadas | % das 197 | Eventos-dia por ponto (soma) | Dias distintos |
|---|---|---|---|---|
| 500 m | 3 | 1,5 | 3 | 3 |
| 1 000 m | 10 | 5,1 | 6 | 6 |
| 2 000 m | 55 | 27,9 | 34 | 13 |
| 3 000 m | 154 | 78,2 | 52 | 14 |

Distância da ocorrência ao ponto mais próximo (197 geocodificadas): mínimo 300 m, p10 1 527 m, mediana 2 456 m, p90 3 609 m, máximo 6 389 m.

### 2b. Eventos-dia casados por ponto e ano (casamento exclusivo do pipeline)

```sql
SELECT c.radius_m::int AS raio_m, p.name AS ponto,
       extract(year FROM k.occurred_from AT TIME ZONE 'America/Recife')::int AS ano,
       count(*) AS ocorrencias, count(DISTINCT (k.occurred_from AT TIME ZONE 'America/Recife')::date) AS eventos_dia
FROM occurrence_point_match m
JOIN linkage_config c ON c.id = m.linkage_config_id AND c.kind='OCCURRENCE_POINT'
JOIN canon k ON k.id = m.occurrence_id JOIN flood_points p ON p.id = m.flood_point_id
GROUP BY 1,2,3 ORDER BY 1,2,3;
```

Eventos-dia (ocorrências entre parênteses). `JABOATAO_CENTRO` tem 0 em todos os raios (nenhuma ocorrência cai dentro de 3 km do ponto). 2021 não casa em nenhum raio.

| Ponto | Ano | 500 m | 1 000 m | 2 000 m | 3 000 m |
|---|---|---|---|---|---|
| AGAMENON_DERBY | 2023 | 0 | 1 (1) | 4 (5) | 6 (16) |
| AGAMENON_DERBY | 2024 | 2 (2) | 2 (6) | 6 (17) | 7 (39) |
| AV_RECIFE_IBURA | 2023 | 0 | 1 (1) | 4 (6) | 6 (13) |
| AV_RECIFE_IBURA | 2024 | 1 (1) | 1 (1) | 6 (10) | 7 (35) |
| MASCARENHAS_IMBIRIBEIRA | 2023 | 0 | 1 (1) | 6 (7) | 6 (11) |
| MASCARENHAS_IMBIRIBEIRA | 2024 | 0 | 0 | 7 (8) | 7 (8) |
| CIN_UFPE | 2023 | 0 | 0 | 1 (2) | 6 (15) |
| CIN_UFPE | 2024 | 0 | 0 | 0 | 7 (17) |
| JABOATAO_CENTRO | 2023–2024 | 0 | 0 | 0 | 0 |

(A consulta devolve `flood_points.name` ("Av. Agamenon Magalhães (Derby)" etc.); a tabela usa os slugs por brevidade.)

### 2c. Sobreposição entre pontos

O casamento do pipeline é exclusivo (a ocorrência vai só ao ponto mais próximo), então a sobreposição foi medida à parte: ocorrências canônicas dentro do raio de mais de um ponto.

```sql
SELECT r.raio_m, count(*) FILTER (WHERE n>=1) AS dentro_de_algum, count(*) FILTER (WHERE n>=2) AS dentro_de_2_ou_mais,
       count(*) FILTER (WHERE n>=3) AS dentro_de_3_ou_mais
FROM (VALUES (500),(1000),(2000),(3000)) r(raio_m)
CROSS JOIN LATERAL (SELECT occ_id, count(*) FILTER (WHERE d <= r.raio_m) n FROM dist GROUP BY occ_id) x
GROUP BY r.raio_m ORDER BY 1;
```

| Raio | Dentro de algum ponto | Dentro de 2 ou mais | Dentro de 3 ou mais |
|---|---|---|---|
| 500 m | 3 | 0 | 0 |
| 1 000 m | 10 | 0 | 0 |
| 2 000 m | 55 | 21 | 0 |
| 3 000 m | 154 | 22 | 0 |

Eventos-dia por ponto **sem** exclusividade (todo ponto dentro do raio):

| Ponto | 500 m | 1 000 m | 2 000 m | 3 000 m |
|---|---|---|---|---|
| AGAMENON_DERBY | 2 | 3 | 10 | 13 |
| AV_RECIFE_IBURA | 1 | 2 | 13 | 13 |
| MASCARENHAS_IMBIRIBEIRA | 0 | 1 | 13 | 13 |
| CIN_UFPE | 0 | 0 | 1 | 13 |
| JABOATAO_CENTRO | 0 | 0 | 0 | 0 |

A 3 km, quatro pontos têm os mesmos 13 dias (de 14 na cidade): os rótulos dos pontos são quase o mesmo rótulo.

## 3. Série diária da cidade e por bairro

```sql
WITH canon AS (<canônicas>), dia AS (SELECT (occurred_from AT TIME ZONE 'America/Recife')::date AS dia, count(*) AS n FROM canon GROUP BY 1)
SELECT extract(year FROM dia)::int AS ano, count(*) AS dias_com_ocorrencia, sum(n) AS ocorrencias, max(n) AS max_no_dia,
       round(avg(n),1) AS media, percentile_cont(0.5) WITHIN GROUP (ORDER BY n) AS mediana, count(*) FILTER (WHERE n >= 5) AS dias_ge5
FROM dia GROUP BY 1 ORDER BY 1;
```

| Ano | Dias com ≥ 1 ocorrência (de 365/366) | Ocorrências | Máx. no dia | Média por dia com ocorrência | Mediana | Dias com ≥ 5 |
|---|---|---|---|---|---|---|
| 2021 | 1 | 1 | 1 | 1,0 | 1 | 0 |
| 2022 | sem dado (quarentena) | n/d | n/d | n/d | n/d | n/d |
| 2023 | 6 | 75 | 16 | 12,5 | 12 | 6 |
| 2024 | 8 | 121 | 28 | 15,1 | 13 | 7 |
| 2025 | 0 (1 registro, sem alagamento) | 0 | 0 | n/a | n/a | 0 |
| 2026 | sem recurso | n/a | n/a | n/a | n/a | n/a |
| **Total** | **15** | **197** | | | | |

Série diária (dias com ocorrência; bairros distintos no dia, campo `Bairro` do payload bruto):

| Dia | Ocorrências | Bairros |
|---|---|---|
| 2021-05-13 | 1 | 1 |
| 2023-02-06 | 13 | 10 |
| 2023-05-24 | 11 | 11 |
| 2023-06-14 | 9 | 8 |
| 2023-06-27 | 10 | 9 |
| 2023-06-30 | 16 | 11 |
| 2023-07-07 | 16 | 12 |
| 2024-01-15 | 3 | 1 |
| 2024-02-16 | 21 | 14 |
| 2024-02-27 | 9 | 6 |
| 2024-04-04 | 13 | 9 |
| 2024-05-07 | 13 | 10 |
| 2024-05-28 | 6 | 4 |
| 2024-06-15 | 28 | 20 |
| 2024-06-25 | 28 | 20 |

Por bairro: há campo `Bairro` no CKAN (payload bruto; `flood_occurrence` não tem coluna de bairro, a consulta junta `raw_observation` por `station_id = recurso || '#' || _id`). 39 bairros distintos nas 197 ocorrências, 0 sem bairro. Os 12 maiores (ocorrências / dias distintos): São José 21/11, Areias 20/13, Imbiribeira 20/13, Afogados 15/6, Campina do Barreto 10/10, Caxangá 9/8, Encruzilhada 9/9, Torrões 9/9, Estância 7/4, Ipsep 7/7, Pina 7/6, Várzea 7/5. Nenhum bairro passa de 21 ocorrências nem de 14 dias em 2 anos.

```sql
WITH canon AS (<canônicas>), b AS (
  SELECT o.id, (o.occurred_from AT TIME ZONE 'America/Recife')::date AS dia,
         coalesce(nullif(initcap(lower(trim(r.payload->>'Bairro'))),''),'(sem bairro)') AS bairro
  FROM canon o LEFT JOIN LATERAL (SELECT payload FROM raw_observation r WHERE r.source='CKAN_RECIFE'
       AND r.station_id = o.ckan_resource_id||'#'||o.ckan_record_id ORDER BY ingested_at DESC LIMIT 1) r ON true)
SELECT bairro, count(*) AS ocorrencias, count(DISTINCT dia) AS dias FROM b GROUP BY 1 ORDER BY 2 DESC, 1;
```

## Implicações para o desenho de pesquisa (leitura dos números, não decisão)

- Com N = 15 dias positivos na cidade (14 em 2023–2024), qualquer unidade de análise tem poucos positivos. A regra N_min = 20 da A.1.4 não é atingida nem por cidade-dia (15), nem por ponto-dia a 3 km (52 pares, mas em 14 dias, com forte dependência entre pontos).
- Mudar a unidade para a cidade (dia × Recife) ou para o bairro dá rótulo mais denso, mas os bairros têm no máximo 14 dias; a unidade "cidade-dia" é a única com positivos independentes (15 dias).
- A resolução espacial real do rótulo é a do bairro, não a da rua (item 3 do resumo); raios menores que ~1 km não são interpretáveis com esta geocodificação.
- O CKAN não cobre 2025 e 2026 e 2022 exige decisão sobre o parser; a janela útil de rótulo continua 2023–2024 (e 1 dia de 2021).

## Pendências para o autor

1. 2022: implementar mapeamento de colunas alternativo (28 candidatos, rua sem número) ou aceitar a lacuna?
2. Persistir a marca de duplicata (migração V11 aditiva) ou manter o filtro canônico só no código/consulta?
3. Registrar o nível de geocodificação (rua, rua sem número, bairro) em `geocode_method` para filtrar centróides antes do casamento (mudança de código, sem migração se couber em `geocode_method` varchar).
4. O `rematch` não tem endpoint; os configs de 1 000 e 2 000 m foram gravados por SQL. Expor `POST /api/admin/linkage/rematch?radius=` se a análise de sensibilidade virar rotina.
5. `MapiServiceImpl` falha em todo ciclo de predição com `failed to lazily initialize a collection of role: TideTable.geoLocations` (log, fora do escopo desta tarefa).
6. Quarentena de 271 mil linhas `TS_UNPARSEABLE` só do CKAN 2022 (3 recursos × 90 425); se o mapeamento for implementado, `REVALIDATE` resolve.
