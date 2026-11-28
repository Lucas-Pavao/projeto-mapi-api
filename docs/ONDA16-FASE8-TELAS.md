# Onda 16, Fase 8 — explicação na tela

- Data: 2026-11-28 · Status: feito (ponto H coberto pelo aval geral do Lucas de 22/11; fotos abaixo)
- Branches: `onda16/fase8-explicabilidade` (api, ia, front)
- Fotos: `docs/onda16/fase8/` (dados sintéticos do MSW, gerados pelo código real da IA com entradas controladas; não são
  previsões). Comando em `projeto-mapi-front/e2e/fidelidade/onda16.spec.ts`.

## O que entrou, item por item

| Item do prompt | Onde | De onde vem o dado |
|---|---|---|
| 8.1 Semáforo com 3 motivos | aba Por quê, logo abaixo do selo (`MotivosBlock`) | chuva medida na última hora, fatores maré e limpeza (ADR 0030), chuva prevista, 1º do SHAP local |
| 8.2 Quanto falta para alagar | aba Por quê (`FaltaBar`) | q50 de 1 h e o contrafactual da IA (prevista + o que falta) |
| 8.3 Leque q50 a q95 | aba Por quê (`LequeChart`) | quantis por prazo; frase "19 de cada 20" pela cobertura do q95 no teste (96,4% a 97,1%, `rain_v1_card.json`) |
| 8.4 Linha do tempo única | aba Balde (`LinhaDoTempo`) | decomposição a cada 10 min (chuva, maré prevista, lâmina) e a soleira do cadastro |
| 8.5 Tornado de sensibilidade | aba Balde (`TornadoChart`) | novo `explanation.tornado` da IA (abaixo) |
| 8.6 Para especialistas | waterfall do SHAP em Detalhes; página Modelo: confiabilidade do rain_v1 e índice da Fase 5 | `shapBaseMm` e `shapPredictionMm` (novos); cartões gerados por `projeto-mapi-ai/scripts/cartoes_modelo.py` |
| 8.7 SEM DADO para gente comum | aba Por quê | `reasonPlain` (um texto por motivo, exaustivo pelo enum) |
| 8.8 Simulador | aviso fixo no topo do formulário | ADR 0014 |
| 8.9 LIME | fora | TreeSHAP já é exato para árvores |

## Regras escritas antes de desenhar

- **Ordem dos motivos:** chuva agora, maré, limpeza, chuva prevista, SHAP; ficam os 3 primeiros que existem. Limiares da
  chuva agora pelo ADR 0017 (moderada a partir de 5,03 mm/h, forte a partir de 25,15 mm/h).
- **Fator que não muda o nível** aparece com o selo "só informa".
- **Tornado:** lâmina em 1 h, cenário provável, cada parâmetro a −20% e +20% (um por vez) a partir da configuração do ALERTA
  (capacidade máxima da faixa), **na chuva do contrafactual**, a que leva a lâmina a 15 cm. Assim o tornado diz o que empurra
  a lâmina em volta do limiar mesmo em dia seco; na chuva prevista de um dia seco todas as barras dariam zero. Parâmetros:
  capacidade da galeria, coeficiente C (limitado a 1), área contribuinte, volume da depressão. No modo IDF (CIn e Jaboatão),
  C e área mexem também na capacidade (C·i·A), como o modelo serve.
- **Sem "%" ao lado da previsão:** a fração de estações com chuva passou a "7 em cada 10" ou "todas" (na tela, "100%" se
  confundia com chance de alagar). Ficam com "%" só a redução da saída pela maré, a chance de entupimento e o ±20% do
  tornado, que já eram exceção nos testes.
- **Ausente nunca vira zero no desenho:** leque com valor ausente mostra "sem dado"; maré ausente não é desenhada.
- **Confiabilidade:** faixa com menos de 30 instantes fica vazada e fora da linha (na chuva forte de 1 h, uma faixa tem 2).

## Ao vivo (28/11, 12h48 UTC, implante api 87ad7c7, ia 24b2b07, front cbcebd7)

- O tornado sai nos 3 pontos com terreno. **A capacidade não é o que mais pesa sozinha:** em CIn e Mascarenhas, área, C e
  capacidade mexem quase igual na lâmina em volta do limiar (CIn: 56,1 × 56,0 cm), e a resposta é muito não linear (de 0 a
  56 cm com ±20%). Em Ibura nem 300 mm em 1 h levam a lâmina a 15 cm. O texto passou a dizer quando há empate (amplitudes a
  menos de 10% uma da outra; `heaviest` nulo e `tied` com os empatados) e quando o limiar não é alcançável (ia @ c4ad4ef,
  96efc07; entra no próximo implante).
- Conta de teste `claude-testes` (ADMIN), criada a pedido do Lucas em 28/11 para os testes ao vivo; senha fora do repositório.

## Contrato (campos novos, todos podem vir nulos)

`explanation.tornado {deltaPct, rain1hMm, centralCm, heaviest, text, bars[{parameter, label, minus20Cm, plus20Cm, swingCm}]}`,
`explanation.shapBaseMm`, `explanation.shapPredictionMm`. Resposta antiga sem esses campos continua valendo (teste
`respostaAntigaSemTornadoContinuaValendo`).

## Achado ao regenerar os exemplos

As respostas de exemplo da IA (`src/test/resources/alagamento/ia/`) não tinham sido regeneradas depois da paridade treino ×
serviço da Fase 7.2. Regeneradas pelo código real: além do tornado, mudam só os valores do SHAP e a lista de entradas
ausentes; níveis e lâminas iguais.

## Testes

mvn 606 (eram 604), pytest 177 com `tests` e `avaliacao` (eram 162; 11 da Fase 6 e 4 desta), vitest 617 (eram 599). `npx tsc -b`
limpo.
