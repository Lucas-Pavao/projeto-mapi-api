# S2iD / Atlas Digital de Desastres — RMR (extrato)

Tarefa F4-IA-12. Acesso em 2026-10-11. Nenhum cruzamento com chuva ou modelo foi feito (regra metodológica).

## O que é público
- **S2iD** (https://s2id.mi.gov.br): sem login só há consulta de reconhecimentos vigentes (decreto, portaria, DOU), "Série Histórica" (arquivos de reconhecimentos federais de SE/ECP desde 2013, por ano e UF, baixados por formulário) e relatórios. Danos detalhados e FIDE por município ficam em área restrita (login). Não há dados abertos em CSV/API no S2iD; a API do dados.gov.br respondeu 401 e não localizei conjunto S2iD nela.
- **Atlas Digital de Desastres** (http://atlasdigital.mdr.gov.br/paginas/downloads.xhtml): base completa 1991-2025, versão 1.1 de 2026-08-06, em CSV (`;`, ISO-8859-1) e XLSX. Um registro por município e evento (protocolo S2iD). Baixado em `/home/lucas/Documents/Projetos/MAPI/rotulos-externos/s2id/` (`atlas.csv`, 76.190 linhas, 86 MB; `manual.pdf`). URL do CSV: http://atlasdigital.mdr.gov.br/arquivos/2026/BD_Atlas_1991_2025_v1.1_2026.08.06_Consolidado.csv
- **Licença/termo:** a página de downloads não declara licença; são dados oficiais da Sedec/MIDR, publicados abertamente. Citar a fonte; [Lucas: confirmar o termo de uso no manual ou com a Ouvidoria antes de redistribuir].

## Contagens (cinco municípios, Data_Evento de 2021 a 2025; a base termina em 2025)
Registros de alagamento (COBRADE 12300), inundação (12100) ou enxurrada (12200) em 2021-2025: **1** (Jaboatão dos Guararapes, inundação, 22/05/2022). Alagamento e enxurrada: 0.
Os demais registros do período são chuvas intensas (13214), de 2021-2025: 15 (Camaragibe 8, Jaboatão 3, Paulista 2, Olinda 1, Recife 1). Outros 10 são deslizamento/movimento de massa (11321: 6; 11410: 4) e 1 de código 24100.

Por ano e município (tipologias de interesse + chuvas intensas), 2021-2025:
- 2021: Camaragibe 2 (13214)
- 2022: Camaragibe 2, Jaboatão 1 (13214) + 1 (12100), Olinda 1, Paulista 1, Recife 1 (todos 13214)
- 2023: Camaragibe 2, Jaboatão 1 (13214)
- 2024: Jaboatão 1 (13214)
- 2025: Camaragibe 2, Paulista 1 (13214)

**Recife:** em toda a série há 16 registros; o único em 2021-2025 é 29/05/2022 (chuvas intensas, 13214). Os eventos de 29/05/2022 aparecem nos cinco municípios (evento regional).

## Limitações para a validação dia × cidade
- Granularidade: **data do evento (dia), sem hora e sem localização** além do município (há coluna de setores censitários, não analisada). Serve, no máximo, para marcar dias de evento severo em escala de município.
- Só reconhecimentos de situação de emergência/calamidade: eventos graves e raros (1 registro de Recife em 4 anos). Não captura os alagamentos corriqueiros que o CKAN traz (225 em 5 anos). Não aumenta de forma útil o número de dias positivos de Recife.
- Tipologia dominante é chuvas intensas (13214), não alagamento (12300); a ligação com alagamento urbano é indireta.
- Cobertura termina em 2025; 2025-2026 incompletos. A data do evento é a informada pelo município, sujeita a erro de preenchimento (ver log de correções em /arquivos/2026/2026.08-logs-correcoes.xlsx).
- Uso recomendado: no máximo, checagem de plausibilidade do dia 29/05/2022; o ganho real de rótulo vem do pedido LAI (`docs/pedidos/05-lai-defesa-civil-registros-alagamento.md`).
