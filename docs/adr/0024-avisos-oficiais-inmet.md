# 0024 — Avisos meteorológicos oficiais do INMET (exibição e arquivamento)

- Status: proposto (implementado na branch `feat/fontes-previsao-oficial`, 2026-11-16)
- Data: 2026-11-16
- Decisores: Lucas, agente de implementação
- Tarefa: P16-FONTES-01

## Contexto

A Fase 2 (`docs/extratos/fase2-previsao-de-chuva-descoberta.md`) mostrou que nenhum órgão oficial brasileiro expõe histórico de previsões ou avisos passados em formato legível por máquina para a Região Metropolitana do Recife (RMR). Sem histórico, não há como treinar nem validar modelo estatístico sem vazamento temporal.

No levantamento de fontes operacionais em tempo real, o endpoint `https://apiprevmet3.inmet.gov.br/avisos/ativos` respondeu com HTTP 200 (461 373 bytes em 2026-11-16 21:05 BRT, exigindo header `User-Agent` de navegador; sem ele a conexão expira), trazendo `{"hoje": [...], "futuro": [...]}` com polígonos GeoJSON, severidade, cor (`#FFFE00`, `#F96602` etc.), riscos e lista de códigos IBGE (`geocodes`).

## Decisão

1. **Nova fonte `INMET_AVISOS`**: cadastrada em `source_registry` (`V31__avisos_oficiais_inmet.sql`) com fuso `America/Sao_Paulo` (`verification_state = DECLARED`) e idade máxima aceita `max_age_minutes = 30` (para job a cada 10 min: três falhas consecutivas acionam o alarme de fonte parada com severidade `WARNING`).
2. **Não entra no modelo de alagamento nem no `rain_v1`**: ausência de histórico passado (impossível avaliar sem *data leakage*) e natureza categórica (faixas de risco por polígono/município, não série horária contínua em mm/h por ponto). Serve apenas para:
   - exibição no mapa ("Aviso oficial do INMET vigente", distinto da previsão experimental do MAPi);
   - arquivamento contínuo a partir de hoje, para viabilizar avaliação futura após acúmulo suficiente.
3. **Proxy de emissão (`first_seen_at`)**: no JSON de `/avisos/ativos`, `created_at` e `updated_at` vêm truncados à meia-noite UTC (`2026-11-16T00:00:00.000Z`, sem hora), e no feed `/avisos/rss` o `<pubDate>` repete o horário de início de vigência. Portanto, a hora real de emissão não está disponível na API pública. O MAPi grava `first_seen_at` (primeiro instante UTC em que `(id_aviso, id_sequencia)` foi visto pelo coletor a cada 10 min) e `last_seen_at` (atualizado a cada visita), declarando explicitamente na API e na documentação que `first_seen_at` é proxy posterior à emissão real (atraso máximo de ~10 min, salvo indisponibilidade).
4. **Filtro geográfico**: persiste na tabela `official_warning` apenas avisos cujos `geocodes` tocam os municípios configurados para os pontos do MAPi (`2611606` Recife, `2607901` Jaboatão dos Guararapes, `2609600` Olinda e municípios dos `flood_points` ativos), evitando gravar o país inteiro. O snapshot bruto da resposta permanece auditável em `raw_observation` com `content_hash` SHA-256 e `ingestion_run`.
5. **Fuso horário**: `inicio` e `fim` chegam em formato `YYYY-MM-DD HH:MM` sem offset (horário civil oficial de Brasília, `America/Sao_Paulo`, equivalente a `UTC-03:00` desde o fim do horário de verão em 2019) e são convertidos para `TIMESTAMPTZ` UTC (`valid_from`, `valid_to`).
6. **Termos de uso**: o cabeçalho do feed oficial `/avisos/rss` do INMET (`https://apiprevmet3.inmet.gov.br/avisos/rss`, verificado em 2026-11-16) declara `<copyright>public domain</copyright>` e *"Licenca de Uso: O conteudo deste site, podera ser reproduzido desde que citada a fonte, excetuando os casos especificados em contrario e os conteudos replicados de outras fontes. O INMET nao se responsabiliza por eventuais danos que o conteudo hospedado por terceiros possa causar, sejam estes morais ou materiais."* A citação `"INMET — Instituto Nacional de Meteorologia"` e a nota de isenção acompanham a resposta da API e a exibição no front.

## Consequências

- **Melhor**: operadores passam a ver avisos severos oficiais vigentes no mesmo painel, separados visualmente do risco experimental do MAPi; o banco passa a acumular série histórica auditável de avisos com proxy de emissão.
- **Pior**: acopla um job a cada 10 min ao endpoint público do INMET (mitigado por timeout explícito, Resilience4j e desabilitação por propriedade `app.inmet.enabled=false`).

## Condições que reabririam a decisão

- Disponibilização pelo INMET de arquivo histórico oficial com timestamp exato de emissão CAP ou mudança do endpoint `apiprevmet3.inmet.gov.br`.
- Acúmulo de ≥ 12–24 meses de histórico local permitindo testar o valor preditivo do aviso em validação temporal separada.
