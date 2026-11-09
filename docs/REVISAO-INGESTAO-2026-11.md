# Revisão da ingestão, das fontes de chuva e dos rótulos — 2026-11

- Tarefa: F6-DADOS-01 · Data: 2026-11-08 · Decisões propostas: ADR 0018 (vínculo estação↔ponto), ADR 0019 (rótulo multifonte), ADR 0020 (recarga e troca de nome para `mapi_db`)
- Motivo: a tabela de eventos de chuva (ADR 0017, `EVENTOS-DE-CHUVA.md`) mostrou os 5 pontos com a mesma estação da ANA (11,6 a 19,9 km), nenhum evento acima da capacidade de projeto e 18 dias de rótulo em 5 anos, todos sem hora.
- Estado: ADRs 0018–0020 aprovados (2026-11-08). Recarga feita no banco novo `mapi_db` (seção 7); dump `docs/snapshots/2026-11-08-mapi_db-pos-recarga.md`. O `tide_db` e o volume antigo continuam guardados (o dump `pre-recarga` não está no Drive).

## Resposta em uma linha

Depois desta revisão o projeto tem **0 dias com alagamento com hora e com ponto identificado**: nenhuma fonte pública de rótulo traz hora, e a 500 m dos pontos não há mais nenhuma ocorrência (eram 9 no snapshot de 2026-10-10; ver seção 7.3). A hora só pode vir do pedido 05 (Defesa Civil, LAI) ou do pedido 07 (Bombeiros). A hora só pode vir do pedido 05 (Defesa Civil, LAI) ou do novo pedido 07 (Bombeiros).

## 1. APAC — histórico pluviométrico

Investigado por `curl` em 2026-11-08 (13 consultas, uma por vez, pausa de 3 s ou mais).

| Pergunta | Resposta | Origem |
|---|---|---|
| Granularidade | **Diária** (total por dia civil, uma coluna por dia do mês) e mensal. Não há hora nem sub-hora. | `diario.php`, `export_diario.php` |
| Formato | Formulário HTML (`POST` para `diario.php` ou `mensal.php`), com exportação **XLSX** em `export_diario.php`. A exportação só devolve dado depois da consulta em `diario.php` com os mesmos filtros; sem ela, a planilha vem só com o cabeçalho (6,3 kB). Porta 41120, HTTP; HTTPS na mesma porta não responde. Sem captcha. | consultas de teste |
| Filtros | `mesorregiao`, `microrregiao`, `municipio`, `bacia`, `tipoBoletim`, `dataInicial`, `dataFinal` (o limite de 5 anos por consulta está comentado no JavaScript) | HTML do formulário |
| Postos na RMR | **Convencionais**: 20 a 23 em todos os meses testados (2016-01, 2021-01, 2022-05, 2023-05, 2023-09, 2023-11, 2024-01), entre eles Recife (Codecipe/Santo Amaro), Várzea, Alto da Brasileira, Olinda, Camaragibe e Jaboatão (Duas Unas). **Automáticos** (PCD da APAC e do CEMADEN, só o total diário): presentes a partir de **2024-02**, ausentes em 2024-01; 35 a 46 postos | planilhas de teste |
| Diferença para a coleta ao vivo | Os postos automáticos são a mesma rede que o MAPi já coleta ao vivo (CEMADEN pelo endpoint `/cemaden/` da APAC, mesmos códigos `2611606xxA`). Os convencionais não aparecem na coleta ao vivo. | comparação de códigos |
| Termo de uso | Não localizado (continua pendente, `FONTES-E-LICENCAS.md` seção 5) | — |

**Resolve a estação única?** Para a **chuva diária**, sim em parte: AGAMENON_DERBY passa a ter posto convencional a 2,4 km e JABOATAO_CENTRO a 3,2 km; os outros três ficam a 5,9–9,1 km. Para a **chuva horária**, não: a APAC histórica não tem hora. Recomendação: integrar como série diária separada (`precipitation_daily_total`, `P1D`), fora da grade horária, como a regra atual já faz com o total diário.

Cuidados do adaptador (ADR 0006): guardar o XLSX bruto com SHA-256 em `raw_observation` antes de extrair; falhar com erro visível se o cabeçalho mudar (colunas `Código GMMC … 01 … 31 Acumulado`) ou se a planilha vier vazia depois da consulta; uma requisição por vez, com pausa; `-` na célula é ausência, nunca zero; vírgula decimal; fuso `America/Recife` (dia civil local). Nada derivado é publicado até o termo de uso.

## 2. Cemaden — histórico

| Pergunta | Resposta | Origem |
|---|---|---|
| Há download público? | **Sim, com captcha.** `mapainterativo.cemaden.gov.br/download/download_form.php` pede UF, município, mês e ano, e um captcha (`securimage`). Anos para pluviômetros: 2011, 2013–2026. Recife tem `id_municipio` 5550; Jaboatão 5538. | `getperiodos.php`, `getcidades.php` |
| Há API? | **Sim, com cadastro.** A Plataforma de Entrega de Dados (PED, `sws.cemaden.gov.br/PED/rest`) tem `/pcds/dados_pcd` (até 30 dias por chamada), `/pcds/pcds-dados-pg` (paginado) e `/controle-agendamento/pcds-dados-historicos` (arquivo agendado). Toda chamada exige `token`; sem ele a resposta é 400 "O parâmetro token não pode ser vazio". | swagger em `PED/api/ui/swagger.json` |
| Granularidade | Leituras de 10 min (com chuva) ou 60 min (seco), as mesmas que a coleta ao vivo já trata (V22) | `docs/INGESTAO.md` |
| Postos perto dos pontos | Um ou mais a até 5 km de cada ponto (tabela da seção 3) | tabela `station` |
| Termo de uso | Não localizado; conteúdo do site em CC BY-ND 3.0 BR (`FONTES-E-LICENCAS.md` seção 6) | — |

**Resolve a estação única?** Sim, é a melhor fonte possível: medida perto do ponto, 10 min, cobre o período dos rótulos. Mas depende de **ação do Lucas**: cadastro na PED (login com e-mail do pesquisador), porque o captcha não deve ser contornado por robô. Com o token, o adaptador usa `/pcds/pcds-dados-pg` por estação e mês, uma chamada por vez.

## 3. Mapa de estações por ponto

Distância haversine do ponto à estação (coordenadas da tabela `station` e das planilhas da APAC; script em `scratchpad`, 2026-11-08).

| Ponto | ANA (hoje) | CEMADEN mais próxima | ≤ 5 km | APAC convencional (diária) | APAC/CEMADEN no histórico diário (desde 2024-02) |
|---|---|---|---|---|---|
| AV_RECIFE_IBURA | 39187800, 17,3 km | Areias, **0,69 km** | 5 | Santo Amaro, 9,1 km | Imbiribeira, 2,05 km |
| CIN_UFPE | 39187800, 11,6 km | San Martin, 3,49 km | 6 | Camaragibe, 5,9 km | Brega e Chique, 3,64 km |
| AGAMENON_DERBY | 39187800, 17,0 km | Torreão, 2,07 km | 8 | Santo Amaro, **2,36 km** | Torreão, 2,07 km |
| JABOATAO_CENTRO | 39187800, 12,3 km | Cavaleiro, 4,79 km | 1 | Duas Unas, **3,22 km** | Muribeca, 7,50 km |
| MASCARENHAS_IMBIRIBEIRA | 39187800, 19,9 km | Imbiribeira, **1,04 km** | 3 | Santo Amaro, 8,9 km | Imbiribeira, 1,05 km |

Proposta do ADR 0018: raio máximo de **5 km** para chuva medida; acima disso o ponto usa a reanálise, com a origem declarada. Hoje, com a ANA como única série horária histórica, **os 5 pontos ficariam sem chuva medida**. Com o Cemaden histórico, os 5 teriam estação dentro do raio.

## 4. Rótulos

### 4.1 O que existe no banco (snapshot `pos-carga`, 2026-10-10)

| Raio | Ocorrências casadas | Ponto-dia | Origem |
|---|---|---|---|
| 500 m | 9 | **3** | `occurrence_point_match`, linkage 3 |
| 1 km | 10 | 6 | linkage 4 |
| 2 km | 55 | 34 | linkage 5 |
| 3 km | 154 | 52 | linkage 2 |

225 ocorrências distintas, 18 dias, todas com precisão DAY, todas geocodificadas por serviço (`GEOCODING_SERVICE`). Com hora: 0.

### 4.2 Fontes investigadas

| Fonte | Hora? | Tipo alagamento? | Volume (2021–2024) | Situação |
|---|---|---|---|---|
| Defesa Civil (CKAN `registro-de-atendimentos-da-defesa-civil`) | **não** | sim, mas o filtro atual só pega a ocorrência "Alagamentos" | ver 4.3 | em uso; 2025 publicado com **1 linha** (o ano não saiu) |
| 156 da Emlurb (CKAN) | **não** (`DATA_DEMANDA` com 00:00:00) | **não**: só manutenção de drenagem (limpeza de galeria 5 040, abatimento 3 290, tampa 2 576, canaleta 716, canal 550 em 2022) | 96 633 demandas em 2022 | serve no máximo como indício de drenagem obstruída, confiança BAIXA; **não é rótulo de alagamento** |
| Sedec "Solicitações Tempo Real" (Dados Vivos) | **sim** (`solicitacao_hora`) | descrição livre | **173 registros**, um recorte de 2023-03 | sem série histórica; não muda o número |
| Defesa Civil via LAI (pedido 05) | sim (pedido) | sim | — | redigido; envio pelo Lucas |
| Bombeiros de PE (pedido 07, novo) | sim (pedido) | sim | — | redigido nesta tarefa |
| Ocorrências do Cemaden | — | — | — | acesso não localizado; o estudo de limiar (Fonseca Neto et al. 2026) usou base interna |
| Imprensa | data | texto | — | só conferência, confiança BAIXA, nunca treino |

### 4.3 Defesa Civil: filtro atual × filtro amplo

Contagem nos CSVs do CKAN baixados em 2026-11-08, coluna de ocorrência e de solicitação.

| Ano | Registros | Só "Alagamentos" (filtro atual): reg / dias | Qualquer "alag" (inclui "Imóveis Alagados"): reg / dias |
|---|---|---|---|
| 2021 | 64 855 | 1 / 1 | 3 / 3 |
| 2022 | 90 425 | 28 / 3 | **744 / 29** |
| 2023 | 83 164 | 75 / 6 | 82 / 9 |
| 2024 | 80 173 | 121 / 8 | 121 / 8 |

O filtro amplo leva de 18 para cerca de 49 dias na cidade. Três ressalvas antes de usar: (a) "Imóveis Alagados" pode ser inundação de casa por rio ou canal, que a decisão de 2026-10-10 deixou fora (`label_filter`, V6); (b) a data é a da vistoria ou do monitoramento: a chuva de 28/05/2022 aparece em 30 e 31/05; (c) quantos casam a 500 m só se sabe depois de geocodificar os endereços. É uma decisão do Lucas, registrada no ADR 0019.

### 4.4 Negativos verificados

Proposta no ADR 0019: tabela própria, separada dos negativos por ausência, com três origens e confiança declarada: confirmação da Defesa Civil (ALTA, vem do pedido 05); ausência de chamado num dia com chuva acima do limiar, em ponto com histórico de chamado (BAIXA); registro fotográfico datado (MÉDIA). Nenhuma está disponível hoje.

> **Aviso.** Nada ingerido como rótulo (positivo ou negativo) pode realimentar a definição de evento, o limiar de classificação ou a capacidade da galeria. É o mesmo defeito que levou a desativar o realinhamento do rótulo ao pico de chuva (ADR 0006).

## 5. Troca de nome `tide_db` → `mapi_db`

Varredura de `tide_db` em `MAPI/` (fora de `node_modules`, `target`, `.git`):

| Onde | Ocorrências | O que muda |
|---|---|---|
| `projeto-mapi-api/docker-compose.yml` | 4 | `POSTGRES_DB`, healthcheck `pg_isready -d`, `DB_NAME`, `POSTGRES_URL`; volume novo `postgres_mapi_data` |
| `projeto-mapi-api/src/main/resources/application.yml` | 1 | URL padrão |
| `AbstractTimescaleIntegrationTest.java` | 1 | nome do banco do contêiner de teste |
| `docs/CHECKLIST-RELEASE.md` | 2 | comandos de dump e restauração |
| `docs/adr/0005`, `docs/redesign/HANDOFF-redesign.md`, `docs/EVENTOS-DE-CHUVA.md`, `.claude/mapi/PROGRESSO.md` | 1–3 cada | nota de troca, sem reescrever o que já aconteceu |
| `docs/snapshots/*.md` antigos, `MAPI/snapshots/*.sha256`, `backup-drive/` | — | **não mudam** (descrevem o que existia) |
| `projeto-mapi-ai/avaliacao/reproduzir.sh`, `extrair_entradas.sh`, `README.md` | 9 | **não mudam**: procedimento da F1 pré-registrado. Para reproduzir a F1, restaurar o dump `pos-carga` num banco chamado `tide_db` ou passar `PSQL=…-d <banco>` (o script aceita a variável) |
| `.claude/worktrees/*` | cópias | ignorar (worktrees antigas) |

## 7. Recarga do `mapi_db` (2026-11-08)

Execução assíncrona, uma por vez, esperando o estado final (roteiro em `scratchpad`, log por execução). Coletores ao vivo desligados durante a carga. 0 execuções da recarga falharam; 0 registros em quarentena nas séries.

### 7.1 Séries: antes × depois

"Antes" = snapshot `2026-10-10-pos-carga` (`docs/snapshots/2026-10-10-pos-carga.md`) e, para a ANA, a cópia de trabalho revalidada em 2026-11-08 (`EVENTOS-DE-CHUVA.md`). "Depois" = consulta em `mapi_db`, 2026-11-08.

| Fonte | Antes (linhas em `observation`; período) | Depois | Quarentena depois | Observação |
|---|---|---|---|---|
| `OPEN_METEO_ARCHIVE` (7 variáveis) | 1 559 880; 2021-01 a 2026-09 | 1 789 200 (255 600 por variável); 2021-01-01 a 2026-11-01 | 0 | janela estendida até 2026-11 |
| `OPEN_METEO_ERA5LAND` | 219 120; 2021-10 a 2026-09 | 255 600; 2021-01-01 a 2026-11-01 | 0 | idem |
| `ANA_SOAP` chuva `PT15M` | 548 696 (só após REVALIDATE; no snapshot estava toda em quarentena de fuso); 2021-10 a 2026-10-10 | 584 155; 2021-01-01 a 2026-11-08; 4 estações | 0 | 2 540 lacunas registradas pelo pipeline (`gaps`); 3 234 horas incompletas na grade |
| `ANA_SOAP` nível | 517 149 | 548 564 | 0 | `EVENT` |
| `APAC_HIST_DIARIO` (nova) | — | 78 438 totais diários; 77 postos; 2021-01-02 a 2026-11-08 | 0 | ~7,3 a 7,9 mil por ano até 2023; ~19 mil a partir de 2024 (postos automáticos) |
| `DHN_TABUA_PE02` | 6 009 | 6 009 | 0 | igual |
| `TABUAMARE_PE02` | 1 183 | 1 229 | 0 | até 2026-11-14 |
| `TIDE_PE02_HOURLY` | 44 568 | 44 899 | 0 | até 2026-11-14 |
| `CEMADEN` | 8 108 (2 dias ao vivo) | 0 | — | histórico depende do token da PED; o ao vivo volta com o coletor |

Uma execução `LIVE` da TabuaMare falhou na subida da API ("sem dados para 2026-11 após 3 tentativas", sincronização automática); o `BACKFILL` logo depois trouxe novembro. Banco: 2 813 MB.

### 7.2 Rótulos

| Item | Valor |
|---|---|
| Ocorrências (Defesa Civil, filtro "alagamento") | 225, todas `DAY` |
| Precisão espacial | LOGRADOURO 172 · BAIRRO 53 |
| Confiança (regra V23) | MÉDIA 172 · BAIXA 53 · ALTA 0 |
| Grupos após deduplicação (mesmo dia, até 200 m) | 187, em 18 dias |
| Dias com hora | **0** |
| Recursos "tempo real" da Sedec (173 linhas cada) | em quarentena `TS_UNPARSEABLE` (355 brutos distintos), motivo gravado "Data não interpretável (0 caracteres)": o mapeamento de colunas não acha a data nesse layout (`solicitacao_data`/`solicitacao_hora`). Mesmo corrigido, são 173 registros de um único recorte (2023-03), sem tipo "alagamento" explícito |

Ponto-dia por raio (ocorrência casada com o ponto mais próximo; consulta sobre `occurrence_cluster`):

| Ponto | 500 m | 1 km | 2 km | 3 km |
|---|---|---|---|---|
| AGAMENON_DERBY | 0 | 3 | 12 | 13 |
| AV_RECIFE_IBURA | 0 | 2 | 13 | 13 |
| CIN_UFPE | 0 | 0 | 7 | 15 |
| MASCARENHAS_IMBIRIBEIRA | 0 | 1 | 13 | 13 |
| JABOATAO_CENTRO | 0 | 0 | 0 | 0 |

### 7.3 Divergência registrada

O snapshot de 2026-10-10 tinha 9 ocorrências e 3 ponto-dia a 500 m. O `tide_db` de 2026-11-08 (antes da recarga) já tinha 0, e o `mapi_db` também tem 0. Entre o `tide_db` e o `mapi_db`, as 225 ocorrências são as mesmas (mesma chave), mas 50 mudaram de coordenada mais de 50 m e 29 mais de 500 m: a geocodificação (Nominatim) não é reprodutível entre execuções. Os arquivos congelados da F1 não mudam (são entradas com hash); a contagem "3 ponto-dia a 500 m" citada em documentos anteriores vale só para o snapshot de 2026-10-10. Recomendação: guardar a coordenada geocodificada no bruto ou num cache versionado, para que a recarga não a refaça.

## 6. O que fica para depois da aprovação

1. Cópia do dump `2026-11-08-pre-recarga` no Drive (Lucas).
2. Aprovação escrita dos ADRs 0018–0020 e da migração do modelo de rótulo.
3. Cadastro na PED do Cemaden (Lucas) e decisão sobre o filtro amplo da Defesa Civil.
4. Só então: compose com `mapi_db` e volume novo, recarga na ordem do ADR 0020, relatório antes/depois e dump novo.
