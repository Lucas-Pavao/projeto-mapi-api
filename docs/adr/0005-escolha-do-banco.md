# 0005 — Escolha do banco: PostgreSQL 16 + TimescaleDB + PostGIS (e imagem fixa)

- Status: proposto
- Data: 2026-10-10
- Decisores: Engenheiro de Dados (autor), checkpoint Máximo (pendente), Lucas
- Tarefa: F2-DADOS-02
- Checkpoint: Máximo, 2026-10-10 — aprovado com 4 ressalvas, aplicadas neste texto (assimetria de buffer, numeração V7, procedimento da troca de imagem, condição 5 verificável). Status continua "proposto" até Lucas aprovar a troca de imagem.

## Contexto
O autor questionou se vale manter PostgreSQL 16 + TimescaleDB (hipótese: sair perderia recursos centrais
sem ganho proporcional) ou migrar para MySQL. Evidência completa em `docs/medidas/benchmark-banco-2026-10-10.md`
e `docs/medidas/comparacao-banco.xlsx`. Números principais:

- **Estado atual**: imagem `timescale/timescaledb:latest-pg16` (tag flutuante; PG 16.14 + TimescaleDB 2.27.2,
  **sem PostGIS**); 5 pontos críticos; banco local com 555 leituras de sensor e 40 de clima, sem histórico
  (fonte: `docs/snapshots/2026-10-10-pre-revisao.md`).
- **Código acoplado**: o V1 usa `create_hypertable` em 4 tabelas (`V1…sql:47,66,97,124`) e 1 continuous
  aggregate com `time_bucket` (`:69-70`); `src/main/java` tem **0** `time_bucket` e 2 consultas nativas, ambas
  com `DISTINCT ON` do PostgreSQL (`SensorDataRepository.java:17,47`). O driver e o `flyway-database-postgresql`
  estão no `pom.xml:50,60`.
- **Volume** (conta na seção 9 do benchmark): 5 pontos = 219 000 linhas/5 anos; 50 = 2,19 M; 200 = 8,76 M.
  `weather_data` tem 8 variáveis; `sensor_data`, 14. Extrapolado: 200 pontos = 2,75 GB sem compressão.
- **Benchmark** (50 pontos × 5 anos, 2 191 200 linhas, imagem `timescaledb-ha:pg16.15-ts2.30.2`,
  MySQL 8.4.11): compressão nativa 688 MB → 126 MB (5,46×) contra 240 MB do melhor MySQL (InnoDB comprimido);
  consulta de 1 ponto × 5 anos = 50–59 ms contra ~91 ms com o buffer pool padrão do MySQL (128 MB) e 75–90 ms com 2 GB (assimetria: o PG rodou com `shared_buffers` 5,9 GB; ver benchmark §4); 1 ponto × 30 dias ≈ 1,2 ms nos dois; refresh de
  cagg: 12,0 s (3,16 M linhas), incremental de 1 dia 54 ms.
- **Fase 3**: raster PE3D (MDT 1 m, EPSG:31985) exige extrair cota, declividade e bacia por ponto
  (skill `mapi-geo-pe3d`). No PostGIS 3.6.4 da imagem testada, `ST_Value`, `ST_Clip`, `ST_Slope` e
  `ST_Transform` funcionaram num raster sintético em 31985 (benchmark, seção 7). A documentação do MySQL 8.4
  descreve só tipos geométricos vetoriais; não menciona raster.

## Opções consideradas
### A — Manter PostgreSQL + TimescaleDB e adicionar PostGIS (imagem fixa)
A favor: custo de migração zero (V1/V2 rodaram sem alteração na imagem nova); PostGIS raster pronto;
compressão 5,5× e cagg incremental automático; `pg_dump`/`pg_restore` testados (15,6 s e 11,4 s para 5,3 M
linhas). Contra: a hospedagem gerenciada é restrita — o Amazon RDS lista PostGIS/`postgis_raster` 3.4.6 para
PG 16, mas `timescaledb` não aparece em 200 000 dos 208 823 caracteres lidos da página de extensões do RDS (os últimos 8 823 não foram lidos), então tratamos o RDS como sem TimescaleDB; o Azure só roda a edição Apache,
sem compressão (fonte: busca, ver benchmark). Self-host ou Tiger Cloud resolvem. A imagem `-ha` pesa 4,38 GB
contra 1,94 GB (a atual).
Custo: trocar a tag no compose e nos Testcontainers; 0 linhas de código.

### B — Migrar para MySQL 8.4
A favor: MySQL é bem conhecido e amplamente hospedado (RDS, Cloud SQL, etc.); 8.4 é LTS; no volume do MAPI
(< 3 GB em 200 pontos) consulta e carga são rápidas — 1 ponto × 5 anos em 75–90 ms (buffer pool 2 GB); carga de 2,19 M linhas em 13 s.
Contra: (1) sem raster: cota, declividade e bacia teriam de sair em Python, sem consulta ad hoc em SQL e
sem validação no banco; (2) sem hypertable, `time_bucket` nem cagg: partição por ano manual (a chave de
partição precisa estar em toda chave única) e tabela-resumo mantida por job nosso; (3) compressão 2,05× contra
5,46× (240 × 126 MB); (4) reescrever o V1 inteiro (BIGSERIAL, `DISTINCT ON` → janela, `TIMESTAMP`/`timestamptz`
do ADR 0001, `flyway-database-mysql`), refazer o ADR 0003/0004 e os Testcontainers; (5) latência: **não é contra** — com `innodb_buffer_pool_size`=2 GB o MySQL fez 1 ponto × 5 anos em 75–90 ms (Timescale 50–59 ms), mesma ordem de grandeza; a comparação original tinha buffer pool de 128 MB contra 5,9 GB do PG e fica só registrada. Custo: V1 + 2 consultas + testes + export; não medido em horas `[sem fonte]`.

### C — PostgreSQL 16 + PostGIS, sem TimescaleDB
Opção de recuo, registrada porque o benchmark mostra que o volume é pequeno. A favor: máxima portabilidade
(RDS, Azure, Cloud SQL), imagem `postgis/postgis` menor. Contra: perde compressão (688 MB → 126 MB em 50 pontos),
cagg incremental e `time_bucket`; exige reescrever o V1 (4 `create_hypertable` e 1 cagg) e partição
manual se o volume crescer. Custo baixo, mas não zero, e sem ganho hoje.

## Decisão
**A.** Manter PostgreSQL 16 + TimescaleDB e adicionar PostGIS, com **imagem de tag fixa
`timescale/timescaledb-ha:pg16.15-ts2.30.2`** (PG 16.15, TimescaleDB 2.30.2, PostGIS 3.6.4, digest
`sha256:ce47ce9026b04a5a1a2e1f2318a70cb50d77f0e73673ad1bd006c3acc493652e`).

A hipótese do autor fica **parcialmente confirmada**: o raster (PostGIS) é recurso central e decisivo, e a
compressão e o cagg valem a pena. Mas o desempenho de consulta e o volume **não** exigem Timescale (latência da mesma ordem nos dois com buffer pool equiparável) — um MySQL
(ou PG puro) aguentaria. O argumento forte contra MySQL é raster + custo de reescrita, não velocidade.

## Consequências
- Melhor: Fase 3 consulta cota/declividade em SQL; histórico de 5 anos fica ~5× menor; imagem reproduzível.
- Pior: imagem 4,38 GB (a atual: 1,94 GB); hospedagem gerenciada limitada (ver condições); a tag fixa exige
  atualização consciente (patch de segurança do PG).
- Mudar (não feito aqui; esta tarefa não altera compose, código nem migrations):
  1. **[aprovar]** Troca da imagem do serviço `postgres` para `timescale/timescaledb-ha:pg16.15-ts2.30.2`
     (compose e Testcontainers, ADR 0004, com `asCompatibleSubstituteFor("postgres")`). A imagem `-ha` usa PGDATA em
     `/home/postgres/pgdata/data` e usuário `postgres` (uid 1000), então o volume atual não é reaproveitável.
     Procedimento (nada disto foi executado):
     a. parar a aplicação (`mapi-api`, `mapi-ai`) e deixar só o banco;
     b. `pg_dump -Fc` do `tide_db` e registrar o SHA-256 do arquivo (o snapshot de 2026-10-10 tem 408 355 B e
        `3ace3cde…4dedd`; gerar um dump novo);
     c. criar um volume NOVO (ex.: `projeto-mapi-api_postgres_ha_data`); **não apagar** `projeto-mapi-api_postgres_data`;
     d. subir a imagem nova com o volume novo (ajustar dono para uid 1000 se for bind mount),
        `CREATE EXTENSION timescaledb`, `SELECT timescaledb_pre_restore()`, `pg_restore --no-owner`,
        `SELECT timescaledb_post_restore()`;
     e. conferir as contagens contra `docs/snapshots/2026-10-10-pre-revisao.md` (sensor_data 555, weather_data 40,
        flood_predictions 25 102, flood_points 5, users 8, tide 1/12/366/1 415) e o Flyway (V1, V2 `success`);
     f. só então apontar o compose. A **remoção do volume antigo exige aprovação separada**. O autor decidiu descartar o
        dado de desenvolvimento no pipeline novo (ADR 0006), mas o volume antigo é preservado até essa aprovação.
  2. Migration **V7** com `CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS postgis_raster;`
     (V3–V6 são do pipeline, ADR 0006; V7 é a próxima livre).
  3. Raster real fora do banco (VRT/COG) ou via `raster2pgsql`: decidir na Fase 3.
- Benefício futuro (não é consequência atual): compressão nativa de `weather_data`/`sensor_data` (5,46× e 6,26× no
  sintético; `segmentby` por ponto/sensor) fica condicionada aos gatilhos do ADR 0006 §9; nenhuma migration de
  compressão é proposta aqui.
- Custo da imagem no CI: 4,38 GB (a atual: 1,94 GB) a cada runner sem cache. O tempo de `docker pull` não foi
  medido (camadas já em cache local) `[sem fonte]`; medir no primeiro run do CI e usar cache de imagem.
- Medir: tamanho (`hypertable_compression_stats`), p95 do export 1 ponto × 5 anos (hoje 50–59 ms no sintético) e
  tempo de refresh do cagg na carga real de 5 anos.
- Hospedagem: assume-se self-host/VM (Docker) até a Prefeitura definir; Tiger Cloud é a alternativa gerenciada.
- Não verificado: carga real de 5 anos (ruído sintético); MDT real no PostGIS; MySQL com índice espacial/
  consulta de pontos; preços de hospedagem; o que a Prefeitura/Defesa Civil já opera `[sem fonte]`.

## Condições que reabririam a decisão
1. Hospedagem institucional (Prefeitura do Recife/Defesa Civil) que só ofereça MySQL, ou PostgreSQL gerenciado
   sem `timescaledb` e sem possibilidade de VM: reabrir para a opção C (PG + PostGIS), não para MySQL.
2. Se a Fase 3 resolver terreno 100% fora do banco (rasterio) e o PostGIS só guardar pontos (vetor):
   o argumento contra MySQL enfraquece; reavaliar B.
3. Volume real > 10× o projetado (> 2 bilhões de valores, > 200 pontos) ou ingestão contínua mais rápida que
   o refresh de cagg suporta: reavaliar com novo benchmark.
4. Mudança de licença ou política do TimescaleDB que retire compressão/cagg da edição usada, ou a imagem `-ha`
   deixar de ser publicada com PG 16.
5. A hospedagem escolhida pela Prefeitura/Defesa Civil não oferecer TimescaleDB e não permitir instalar extensão nem rodar container/VM próprio (verificável: pedido de `CREATE EXTENSION timescaledb` negado, ou plano sem acesso a container). Ver condição 1.
6. Tempo de pull da imagem de 4,38 GB no CI acima do aceito pelo autor, sem alternativa de cache.

Operação: `pg_dump -Fc` + `pg_restore` com `timescaledb_pre_restore()/post_restore()` testados (benchmark §6).
A Prefeitura/Defesa Civil, se operar o banco, precisará de procedimento escrito de restauração; o MySQL é mais
comum em TI institucional `[sem fonte]`, mas o dump do PostgreSQL é de um único comando.
