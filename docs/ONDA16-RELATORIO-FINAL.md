# Onda 16 — relatório final (previsão de alagamento de ponta a ponta)

- Período: 2026-11-22 a 2026-11-28 · Prompt: "Onda 16: previsão de alagamento funcionando, de ponta a ponta" (22/11)
- Modo sombra: ligado a onda inteira (regra 5). Pontos de parada A a H: A e B aprovados pelo Lucas; C a H cobertos pelo aval
  geral de 22/11 ("tem aval para completar todas as fases"); aval do orientador pendente onde indicado.
- Trilha de cada decisão: `.claude/mapi/PROGRESSO.md` (linhas O16-F0 a O16-F10).

## O que a onda entregou, em uma frase por objetivo

| Objetivo do prompt | Resultado |
|---|---|
| Um alerta só | Feito (ADR 0030): o que se calcula, valida e mostra é o `/v1/flood/forecast`; maré e limpeza viraram fatores com contribuição; o ADR 0026 parou de gravar nível. |
| Que pode disparar | Feito (ADR 0032): capacidade em faixa e pico previsto. Em 5 anos (só frequência), Mascarenhas dá 9,75 episódios de ALERTA por ponto-mês e fica em ALERTA ou ATENÇÃO 27% do tempo; CIn 0,13; Ibura e Jaboatão 0. **Dispara demais num ponto e quase nada nos outros.** |
| Que mostra por que disparou | Feito (Fase 8): motivos em linguagem simples, previsto × necessário, leque, linha do tempo, tornado, waterfall; fotos com o ciclo ao vivo. |
| Com validação honesta pré-registrada | Proposta escrita (`docs/PRE-REGISTRO-VALIDACAO-2027.md`), procedimento de congelamento e relatório semanal no ar; os números [fechar] são do Lucas com o orientador. |

## Tabela de cobertura (seção 4 do prompt)

| Origem | Item | Fase | Status | Onde |
|---|---|---|---|---|
| Relatório geral, ajuste 1 | Dois alertas; refluxo e limpeza no que não aparece | 2 | **feito** | ADR 0030; api V47 |
| Relatório geral, ajuste 2 | O balde não acumula (capacidade, perfil da chuva) | 4 | **feito** (agora acumula; em Mascarenhas, demais) | ADR 0032; `projeto-mapi-ai/reports/reconstrucao/` |
| Relatório geral, ajuste 3 | 3 de 5 pontos sem dado | 3 | **parcial**: Ibura e Jaboatão destravados; Derby em revisão (fundo no canal); Jaboatão fica em SEM_DADO `CHUVA_AUSENTE` ao vivo | `docs/ONDA16-FASE3-PONTOS.md` |
| Relatório geral, ajuste 4 | Validação oficial desatualizada | 9 | **proposta** (aguarda os números do orientador) | `docs/PRE-REGISTRO-VALIDACAO-2027.md` |
| Relatório geral, ajuste 5 | Diferenças entre treino e serviço no rain_v1 | 7.2 | **feito** (paridade testada; 1,13 → 0,28 mm) | `docs/ONDA16-FASE7-COLETA.md` |
| Relatório geral, ajuste 6 | Card com chuva do Open-Meteo | 7.3 | **feito** (3, 6 e 24 h medidos) | `GET /api/pontos/{slug}/chuva-medida` |
| Relatório geral, ajuste 7 | Refluxo com viés 0 e sem validação | 2, 4 | **feito** no que dá sem rótulo: cenário −0,369 m sempre junto; validação só em 2027 | ADR 0030 |
| Relatório geral, ajuste 8 | Limpeza saturada pela última limpeza velha | 1.4, 2 | **feito** (regime permanente; Ibura ainda satura pela regra); limpeza só informa | `docs/PRE-REGISTRO-ENTUPIMENTO-156.md` (emenda 1) |
| Relatório geral, ajuste 9 | `best_match` e saída do ICON em 30/11 | 1.1 | **feito** (ICON trocado; quebra de série V43; conferência semanal V44) | ADR 0029 (emenda 1) |
| Relatório geral, ajuste 10 | CIn sem município (e bairro errado na 156) | 1.3 | **feito** (RECIFE; CIDADE UNIVERSITARIA pelos bairros oficiais) | api V45 |
| Relatório geral, ajuste 11 | APAC meteorologia em quarentena | 7.1 | **inconclusivo**, segue em quarentena (1 estação, 0 mm em 141 leituras) | `projeto-mapi-ai/mapi_ai/rain/fuso_apac.py` |
| Relatório geral, ajuste 12 | Limite de conexões ao vivo | 7.7 | **feito** (3 → 10) | Fase 7 |
| Relatório geral, ajuste 13 | Simulador com outro balde | 8.8 | **feito** (aviso fixo) | `SimulatorPage` |
| Relatório geral, ajuste 14 | Histórico Open-Meteo por modelo e OpenAPI | 7.7 | **feito** | `docs/openapi/openapi-2026-11-23.json` |
| Pesquisa 22/11 | Δ = 0,821 m; auditar 1,144; conferir RN 2603F; pedir F-41 | 1.2 | **feito** (nenhum 1,144 no código; RN 2603F 2,7977 m, σ 0,089; pedido 01 com constantes, não enviado) | ADR 0009 |
| Pesquisa 22/11 | Teste de fuso APAC × PED | 7.1 | **inconclusivo** (sem sobreposição de dado) | Fase 7 |
| Pesquisa 22/11 | Dados Vivos da SEDEC como rótulo com hora | 7.4 | **não dá**: conjuntos parados em 2021 e 2023 | Fase 7 |
| Pesquisa 22/11 | Índice caixa branca (logística e EBM) | 5 | **feito, só comparação** (aval do orientador pendente) | ADR 0031 |
| Pesquisa 22/11 | Pré-registro da validação de 2027 | 9 | **proposta** | idem |
| Pesquisa 22/11 | Telas: semáforo, "faltam X mm", leque, linha do tempo | 8 | **feito** | `docs/ONDA16-FASE8-TELAS.md` |
| Pesquisa 22/11 | GOES-19 RRQPE e radar (ImageServers do ESIG) | 6 | **[completar com o rain_v2]**; radar parado (sem legenda em dBZ); RRQPE aposentado pela NOAA em 02/11/2026, sem coletor ao vivo | ADR 0033 |
| Pesquisa 22/11 | pySTEPS (opcional) | 6.3 | **fora** (depende do radar em dBZ) | ADR 0033 |
| Pesquisa 22/11 | Capacidade por campo e calibração inversa | 4 | **feito sem κ adotado** (sem discriminação em Ibura e CIn) | `docs/PRE-REGISTRO-CALIBRACAO-INVERSA.md` |
| Pesquisa 22/11 | Maré harmônica local | 7.5 | **esperando o CHM** (pedido 01 não enviado); tábua coberta até 31/12/2027 | Fase 7 |
| Pesquisa 22/11 | Conformal (CQR) com cobertura em chuva forte | 9.3 | **feito**: Q ≈ 0; 93–94% com chuva caindo; 19–41% quando a chuva forte vem | `projeto-mapi-ai/reports/cqr/RELATORIO.md` |
| Pesquisa 22/11 | Data de quebra da Mascarenhas (reservatório) | 3.4 | **feito** (`obras`, data vazia; validação separa antes e depois) | `projeto-mapi-ai/data/obras.json` |
| Relatório anexado | Tabela mestre como prior, nunca como valor | regra 2 | **cumprido** | `docs/PARAMETROS-MODELO-FISICO.md` |
| Relatório anexado | Fatores DAEE/CETESB (com fonte) | 4.1 | **feito** (pela Tab. 10 de Suzano; o PDF da CETESB não tem texto) | ADR 0032 |
| Relatório anexado | Revista DAE 225: só se achar no PDF | 4.1 | **achado em parte**: 20 cm de sedimento está (p. 156); o "−40% de vazão" não está | ADR 0032 |
| Relatório anexado | Limiares do Duas Unas: só tela até confirmar | 7.6 | **cumprido**; pergunta no pedido 10 (não enviado) | pedido 10 |
| Conferência de hoje | Mascarenhas com depressão 0,0 m | 3.1 | **feito** (ponto na via citada: depressão real de 0,62 m) | `docs/ONDA16-FASE3-PONTOS.md` |
| Conferência de hoje | Datas do alinhamento fora da validação | regra 1 | **cumprido** | pré-registro da validação, seção 7 |

## Achados que mudam a leitura do trabalho

1. **O alerta agora enche, mas de forma desigual.** Mascarenhas: cano pequeno, depressão rasa e maré no nível fazem a
   ATENÇÃO (duas pontas pessimistas juntas) ligar 27% do tempo. Isso não serve como alerta para gente. A regra não foi mudada
   depois de ver (estava fixada); a variante de uma só ponta entra na validação como comparação.
2. **A faixa pessimista da chuva é curta justamente quando importa:** nas horas em que a chuva forte veio, o q95 cobriu só
   19% a 41% (CQR, Fase 9.3).
3. **A capacidade não pesa sozinha:** ao vivo, em volta do limiar, área, C e capacidade mexem quase igual na lâmina.
4. **O índice caixa branca não bate a regra de 25 mm em 6 h** (AUC 0,964 e 0,917 contra 0,968; mesmo número de falsos).
5. **Satélite:** [completar com o rain_v2].

## Operação

- O ICON Global sai do Open-Meteo em 30/11/2026: coletor já trocado, quebra de série registrada.
- A pilha ficou parada de ~24/11 01h a 28/11 12h UTC; o relatório semanal do modo sombra (V49) mede isso.
- Disco: volume antigo `postgres_ha_data` apagado em 28/11 (pedido de limpeza); **o dump do `tide_db` só existe no disco
  local** (pendência 18, urgente). Conta de teste `claude-testes` (ADMIN) criada a pedido (pendência 27).

## O que fica com o Lucas e o orientador

Pendências 18 (cópia do dump), 23 a 27 em `docs/PENDENCIAS-DE-INFORMACAO.md`; números [fechar] do pré-registro de 2027;
merge dos PRs da onda; enviar os pedidos 01 e 10.

## Fotos

`docs/onda16/fase2/`, `fase3/`, `fase4/`, `fase8/` (MSW e, na Fase 8, também o ciclo ao vivo de 28/11:
`aovivo-porque-*.png`, `aovivo-balde-*.png`).
