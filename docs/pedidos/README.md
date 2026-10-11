# Pedidos institucionais (ADR 0009 — referência vertical)

Rascunhos para o Lucas revisar, completar os campos entre colchetes e enviar. Nenhum foi enviado.
Objetivo comum: fechar o desnível Δ entre o Nível de Redução (NR) da DHN no Porto do Recife e o
datum altimétrico do MDT do PE3D, sem o qual o MAPi não compara cota da rua com maré (ADR 0009).

| Arquivo | Destino | O que pede | Destrava |
|---|---|---|---|
| `01-chm-dhn-ficha-f41-recife.md` | Centro de Hidrografia da Marinha (CHM/DHN) | Ficha F-41 da estação do Porto do Recife (RN da estação acima do NR; NMM sobre o NR e período) | item D da cadeia do IBGE; confirma o `mean_level` 1,28 m |
| `02-ibge-rn-maregrafo-nmm-recife.md` | IBGE — Coordenação de Geodésia | Altitude no SGB da RN ligada ao marégrafo do Recife; NMM local no SGB; vínculo NR↔SGB, se houver | item E da cadeia; fecha Δ com fonte primária |
| `03-pe3d-relatorio-tecnico-mdt.md` | Governo de Pernambuco — PE3D | Relatório técnico do LiDAR/MDT 2016: datum vertical, modelo geoidal, precisão, data dos voos | confirma que o MDT está no SGB (senão, erro de metros) |
| `04-defesa-civil-tolerancia-falso-alarme.md` | Defesa Civil do Recife | Taxa de falso alarme aceitável por ponto/mês para Moderado e Alto; antecedência útil | limiares MEDIUM/HIGH (MODELAGEM-IA, decisão 6) |
| `05-lai-defesa-civil-registros-alagamento.md` | Defesa Civil do Recife (via LAI, Portal da Transparência) | Registros de alagamento 2021-01 a 2026-10 com data e hora, endereço/coordenada, COBRADE, bairro, lâmina e duração (CSV, anonimizado) | rótulos com hora e local para validar a previsão (MEMO-ESCASSEZ-DE-ROTULO, opção C; F4-IA-12) |

Ao receber as respostas: guardar os PDFs em `docs/extratos/` (extrair só uma vez) e atualizar o ADR 0009.
