# Onda 16, Fase 6 — ver a chuva chegando do mar

- Data: 2026-11-23, atualizado em 2026-11-28 · Status: inventário feito; histórico do GOES por faixa de bytes em
  andamento; rain_v2 pré-registrado (`docs/PRE-REGISTRO-RAIN-V2-SATELITE.md`), só para comparação (ADR 0033)

## 6.1 Inventário do ESIG (sem mudar nada)

Consultado em 2026-11-23 às 04h20–07h20 UTC por `scripts/esig-inventario/inventario.py` (só metadados; resultado bruto em
`docs/extratos/esig-inventario/inventario.json`). Base: `https://esigportal2.recife.pe.gov.br/arcgis/rest/services`.

| Serviço pedido | O que é | Campos e tempo | Histórico | Serve? |
|---|---|---|---|---|
| `pgdbesig_esi2_MD_APAC_v2` (ImageServer) | Mosaico das imagens do radar da APAC; PE e mar, WGS84, pixel de 0,0064° (~710 m) | catálogo com `datahora` (UTC, nome `APAC_AAAAMMDD_HHMMSS`), `uf`, `municipio`, `fonte`; `timeInfo` com intervalo de 20 min; capacidades Image, Metadata, Catalog, Mensuration (`exportImage` funciona) | janela móvel: 31 imagens de 02h05 a 07h05 UTC de 23/11 | Georreferência sim; **valor não**: pixels RGBA de 8 bits (figura colorida, 362 cores numa exportação, com mapa de fundo). Precisa da tabela de cores em dBZ (pedido 10) |
| `pgdbesig_esi2_MD_DECEA` (ImageServer) | Mosaico previsto para o radar do DECEA | mesmos campos | **0 itens** no catálogo | Não |
| `PainelChuvas/MD_APAC` (ImageServer) | Painel de chuvas antigo | `DATAHORA` | 16 imagens, todas de 14/05/2024 | Não (parado) |
| `DADOS_CTTU` (MapServer) | Sinistros de trânsito 2017–2022, urbanismo tático, redesenhos, manutenção | sem `timeInfo`; datas por camada (ex.: `Data` 2017-01-01 a 2022-12-31, 12.413 registros) | até 2022 | Não: nenhuma camada de alagamento |
| `Cttu_Análise_Vias_Recife` (MapServer) | Hierarquia viária, manutenção de vias 2022–2025 | sem tempo | — | Só como base viária |
| `COP/BASES_ESTACAOMETEOROLOGICA_CONSULTA` | — | — | — | **Não existe mais** na pasta COP |
| `INMET/BASES_INMET_ALERTAS_CONSULTA` | — | — | — | **Não existe mais** (pasta INMET vazia) |

Achado a mais: `COP/BASE_SENSORES_PLUVIOMETRICOS_RECIFE`, camada "Pluviometros Histórico" (7.808 registros; `CODESTACAO`,
`CHUVA`, `DATAHORA`), e `INPE_CPTEC/BASES_PREVISAO_CHUVA_CPTEC`. Nenhum foi aberto além dos metadados.

**Conclusão:** nenhum serviço traz refletividade de radar em valor; o ImageServer da APAC resolve a georreferência das imagens,
não a conversão. O radar fica parado até a APAC mandar a legenda em dBZ.

## 6.2 GOES-East ABI RRQPE

- Extração: `projeto-mapi-ai/mapi_ai/satelite/rrqpe.py` (projeção do ABI pelo GOES-R PUG; janela da região; caixas C1 0–25 km,
  C2 25–60 km, C3 60–120 km a leste da costa, latitude do Recife ± 0,35°; fração > 1 mm/h, p90, fração sem dado). Testes sem
  rede em `tests/test_rrqpe.py`.
- Disponibilidade: GOES-16 de 2017 a 06/04/2025; GOES-19 de 07/04/2025 até **16h UTC de 02/11/2026**, quando a NOAA
  aposentou o RRQPE legado (aviso da NESDIS de 01/09/2026; detalhes no ADR 0033). O substituto só sai pelo PDA, com
  cadastro: **sem coletor ao vivo**.
- Histórico de 31/08/2021 a 31/10/2026: ~271 mil arquivos, lidos por faixa de bytes (~196 KB por arquivo em vez de
  ~1,5 MB; ~53 GB e ~5 h no total). Saída idêntica à do arquivo inteiro nas 432 varreduras do piloto (30/04 a 02/05/2026).
- rain_v2: critérios A (escolha na validação) e B (aviso de FORTE antes de começar a chover) fixados antes de rodar.

## 6.3 Radar e pySTEPS

Radar parado (seção 6.1). pySTEPS fica fora desta onda (opcional; depende do radar em dBZ).
