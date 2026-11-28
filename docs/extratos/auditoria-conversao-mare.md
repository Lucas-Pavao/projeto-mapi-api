# Auditoria — números de conversão de maré (onda 16, Fase 1.2, 2026-11-22)

Busca nos três repositórios (código, documentos, scripts e ferramentas; fora os PNG do canvas e os JSON/CSV de dados) por
1,144 / 1,14 / 1,106 / 1,25 m / +0,250, escritos com vírgula ou ponto. Regra do ADR 0009: cota_SGB = maré_NR − 0,821 m.

| Onde | O que é | Conversão de maré? | Ação proposta |
|---|---|---|---|
| `docs/adr/0009-referencia-vertical-mdt-mare.md:42` | "−1,14 m": limite da estimativa de trabalho antiga de h_NR, marcada "não aprovada, não usar em código" e substituída pela emenda 1 | não (histórico) | acrescentar, na mesma linha, "substituída pela emenda 1 (Δ = 0,821 m)", para ninguém copiar o número |
| `docs/extratos/referencia-vertical.md:112-116` | o próprio registro de que 1,14 e 1,25 m **não** são usados | não | nenhuma |
| `projeto-mapi-ai/reports/v4/rain/RELATORIO.md:60` | "1,106" é um erro de quantil (mm) do rain_v1 | não | nenhuma |
| `docs/extratos/esig-drenagem/geojson/*` | `VLHFUNDO: 1.14` (profundidade do tubo no ESIG) e coordenadas UTM com "…,144…" | não | nenhuma |
| `docs/snapshots/2026-10-10-primeira-carga.md:105` | tempos de resposta ("11,14 s") | não | nenhuma |
| `+0,250` | nenhuma ocorrência | — | — |

**Uso de Δ no código:** só `AlertaProperties.java:83` (`deltaM = 0.821`). A soleira em NR é fundo do ESIG + viés do MDT +
Δ (`AlertaCycleServiceImpl.java:245`). O sinal confere com a regra.
**Nível médio:** o código usa 1,28 m (Z0 da F-41) em `AbstractTideExtremesAdapter.java:59`. Não é conversão de referência.

**Conclusão:** nenhum uso errado de 1,144 m (ou dos outros números) como conversão de maré no código. A única ação
proposta é a nota no ADR 0009, que espera o ok do ponto B. A tabela de η_maré do relatório "Parâmetros Hidráulicos" não
está no repositório e não entra.
