# Pedidos institucionais (ADR 0009 — referência vertical)

Rascunhos para o Lucas revisar, completar os campos entre colchetes e enviar. Nenhum foi enviado.
Objetivo comum: fechar o desnível Δ entre o Nível de Redução (NR) da DHN no Porto do Recife e o
datum altimétrico do MDT do PE3D, sem o qual o MAPi não compara cota da rua com maré (ADR 0009).

| Arquivo | Destino | O que pede | Destrava |
|---|---|---|---|
| `01-chm-dhn-ficha-f41-recife.md` | Centro de Hidrografia da Marinha (CHM/DHN) | ~~Ficha F-41~~ (obtida em 2026-10-11; `docs/extratos/f41-porto-recife.md`). Resta: autorização para reutilizar as tábuas 2021–2026 | publicar os CSVs de maré derivados |
| `02-ibge-rn-maregrafo-nmm-recife.md` | IBGE — Coordenação de Geodésia | **Opcional desde 2026-10-11**: confirmar que as RN 2603F e 393T do BDG são as da F-41; NMM local no SGB | só confirma Δ = +0,821 m (o item E já veio do BDG) |
| `03-pe3d-relatorio-tecnico-mdt.md` | Governo de Pernambuco — PE3D | Relatório técnico do LiDAR/MDT 2016: datum vertical, modelo geoidal, precisão, data dos voos | **bloqueio principal**: viés MDT↔SGB (elipsoidal já descartado, ADR 0009 emenda 1) |
| `04-defesa-civil-tolerancia-falso-alarme.md` | Defesa Civil do Recife | Taxa de falso alarme aceitável por ponto/mês para Moderado e Alto; antecedência útil | limiares MEDIUM/HIGH (MODELAGEM-IA, decisão 6) |
| `05-lai-defesa-civil-registros-alagamento.md` | Defesa Civil do Recife (via LAI, Portal da Transparência) | Registros de alagamento 2021-01 a 2026-10 com data e hora, endereço/coordenada, COBRADE, bairro, lâmina e duração (CSV, anonimizado) | rótulos com hora e local para validar a previsão (MEMO-ESCASSEZ-DE-ROTULO, opção C; F4-IA-12) |
| `06-lai-emlurb-cadastro-microdrenagem.md` | EMLURB / URB Recife (via LAI, Portal da Transparência; destinatário a confirmar no formulário) | Cadastro de microdrenagem nos entornos de Derby, Imbiribeira, Ibura e CIn-UFPE: traçado, diâmetros, cotas de fundo, soleiras/exutórios, ano de implantação e referencial vertical (CSV/GeoJSON/SHP) | cota de fundo e diâmetro (`f` e soleira com fonte; PRE-REGISTRO §10, item 9; PRONTIDAO §5.2, T6) |

Ao receber as respostas: guardar os PDFs em `docs/extratos/` (extrair só uma vez) e atualizar o ADR 0009.
