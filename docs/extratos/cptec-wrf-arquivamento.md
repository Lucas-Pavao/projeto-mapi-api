# Extrato — Investigação e proposta de arquivamento do WRF do CPTEC/INPE

- Data da medição: 2026-11-16 (entre 21:05 e 21:13 BRT / 2026-11-17 00:05–00:13 UTC)
- Tarefa: P16-FONTES-03 (`feat/fontes-previsao-oficial`)
- Regra seguida: investigação com números medidos e proposta escrita; download GRIB2 **não** implementado sem aprovação.

## 1. Achados medidos nos servidores do CPTEC/INPE

| Produto / Caminho | Estado observado em 2026-11-16 | Formato e grade | Retenção | Rodadas / Tamanho |
|---|---|---|---|---|
| `ftp.cptec.inpe.br/modelos/tempo/WRF/ams_05km/` | Vazio (0 arquivos) | — | — | — |
| `dataserver.cptec.inpe.br/dataserver_modelos/wrf/ams_07km/brutos/` (redirecionado de `ftp.cptec.inpe.br/.../WRF/ams_07km/brutos/`) | **Ativo**, TLS válido (`Nov 9 2026` a `Feb 7 2027`) | GRIB2 + `.inv` + `.ctl`; grade `0.07°` (~7,8 km), `1019 x 1081`, 89 variáveis, `181` passos horários (`t+0` a `t+180h`) | **2023-01 a 2026-11-16** (~4 anos) | 1 rodada/dia (`00/` UTC, publicada entre `05:18` e `07:04 UTC`); ~158–199 MB/hora (~35 GB/rodada cheia) |
| Idem, recorte via `Accept-Ranges: bytes` + `.inv` (`APCP:surface`) | **Verificado HTTP 206** (`Content-Length` completo 199 679 721 B) | Registro 258 do `.inv` (`APCP:surface:0-1 hour acc fcst`, kg/m² = mm) | Idem (`2023–2026`) | **478 570 bytes** (~467 KiB) por hora de previsão |
| `ftp.cptec.inpe.br/modelos/tempo/WRF/ams_07km/recortes/prec/` | TLS **expirado** em `2026-11-17 00:02:43 GMT` | GRIB2 só com `APCPsfc` (`797 x 957`, `0.07°`) | `2025/` e `2026/` até `2026-11-16` | `314 KB` a `509 KB` por hora (~78 MB/rodada), publicado ~10:05 UTC |
| `ftp.cptec.inpe.br/modelos/tempo/WRF/ams_07km/recortes/grh/json/` | Desatualizado (TLS expirado) | JSON por ponto | Até `2026-08-27` | Sem coleta recente |
| `BRAMS/ams_08km/brutos/` (`dataserver`) e `recortes/grh/json/` | `brutos/` em `2025–2026`; `grh/json/` parou em `2026-08` | GRIB2 / JSON | `2025–2026` (`brutos`) | Não detalhado (WRF 7 km já entrega `APCP` horário com 4 anos de arquivo) |
| `dataserver.cptec.inpe.br/thredds/` e `/opendap/` | HTTP 404 | — | — | Inexistente |

### Correção importante sobre o levantamento anterior

1. O WRF operacional publicado hoje no FTP/dataserver do CPTEC é o **`ams_07km` (`0.07°` ≈ 7,8 km)**, não 5 km (`ams_05km` está vazio).
2. Ao contrário da premissa de que "só fica o mês corrente", o `dataserver.cptec.inpe.br` mantém **quase 4 anos (`2023-01` a `2026-11`)** em `brutos/`, com suporte a **`Accept-Ranges: bytes`** e índices **`.inv`**.
3. O host `ftp.cptec.inpe.br` ficou com certificado TLS vencido em `2026-11-17 00:02:43 GMT`; `dataserver.cptec.inpe.br` tem certificado válido até `2027-02-07`.

## 2. Por que não foi implementado agora

Os arquivos são **GRIB2 binários** (não há JSON/CSV ativo para Recife em `grh/json/` desde agosto/2026). Ler GRIB2 requer `eccodes`/`cfgrib` (ou `wgrib2`) no Python da IA ou biblioteca GRIB2 no Java (ausente no `pom.xml`). Conforme a regra da tarefa, fica apenas a proposta abaixo.

## 3. Proposta concreta de arquivamento (`CPTEC_WRF_07KM`)

- **Onde roda:** job diário em contêiner Python (serviço `projeto-mapi-ai` ou worker leve com `eccodes`/`cfgrib`, acionado às `08:00 UTC` após a publicação da rodada `00Z` das `~07:04 UTC`), expondo ou gravando via endpoint interno/tabela dedicada na API (`cptec_wrf_forecast_hour`, fora de `observation`).
- **Como baixa sem custo alto:**
  1. Baixa apenas os `.inv` (~16 KB cada) das primeiras **24 h** (`t+1..t+24`) — ou **6 h** (`t+1..t+6`) — da rodada `00Z`.
  2. Localiza os offsets da linha `:APCP:surface:` (ex.: `172784854` a `173263423`) e faz `GET` com `Range: bytes=<inicio>-<fim>`.
  3. **Tráfego medido por dia:** `6 × 478 KB ≈ 2,9 MB/dia` (horizonte de 6 h) ou `24 × 478 KB ≈ 11,5 MB/dia` (24 h). Para o histórico completo de `2023-01` a `2026-11` (~1.415 dias): ~4,1 GB (6 h) ou ~16,3 GB (24 h) em download pontual e retomável.
- **Rastreabilidade e anti-vazamento:**
  - Bruto: guardar o recorte ou o JSON extraído por ponto (lat/lon da grade mais próxima aos 5 pontos críticos) com `raw_hash` SHA-256.
  - Emissão: registrar `run_utc` (ex.: `2026-11-16T00:00:00Z`) e **`published_at_utc` / `issued_before_utc`** (ex.: `07:15Z`, lido do cabeçalho `Last-Modified` do `.grib2` no servidor — medido `2026-11-16 05:19` a `07:04 UTC`), para que na avaliação nenhuma hora prevista entre `00Z` e `07Z` seja usada como se já existisse antes da publicação.
  - Fonte no `source_registry`: `CPTEC_WRF_07KM` (`source_tz = 'UTC'`, `verification_state = 'DECLARED'`).
