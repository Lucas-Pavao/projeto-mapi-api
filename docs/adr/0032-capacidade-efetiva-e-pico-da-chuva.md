# 0032 — Capacidade efetiva da galeria em faixa e pico de 10 min previsto

- Status: aprovado (aval geral do Lucas de 22/11 para o ponto E; calibração inversa **sem κ adotado**, aval do orientador
  pendente)
- Data: 2026-11-28 (decisões tomadas em 23/11, na Fase 4; este ADR as registra na Fase 10)
- Decisores: Claude (autor), Lucas
- Tarefa: O16-F4, O16-F10

## Contexto

Até a onda 16 o balde de 10 min nunca enchia: entrada (C·P·A) e saída (C·i_T·A, 125,4 mm/h) usavam o mesmo C e a mesma área,
então a rua só acumulava com mais de 20,9 mm em 10 min; e a chuva prevista era espalhada por igual no prazo, o que apagava o
pico. Em 5 anos: 0 de 17 eventos fortes e 0 de 41 ponto-dias da Defesa Civil (prompt de 22/11, seção 0).

## Opções consideradas

### A — Manter a capacidade de projeto (IDF) e calibrar um fator por ponto nos 41 ponto-dias
A favor: simples. Contra: os 41 ponto-dias são os únicos rótulos e ficariam gastos na calibração; o fator não teria fonte.

### B — Capacidade efetiva pelo cadastro, em faixa com fonte (escolhida)
Bocas de lobo (`TIPO = BL` do ESIG) com capacidade por tipo e fator de redução DAEE/CETESB, e o cano por Manning onde há
diâmetro e declividade. Resultado como faixa por ponto (Q_min a Q_max), nunca um número só.

### C — Perfil de projeto para a chuva (bloco alternado ou Huff) contra pico previsto pelo modelo
Ver "Decisão", item 2: decidido por critério escrito antes.

## Decisão

1. **Capacidade em faixa** (`docs/PRE-REGISTRO-CAPACIDADE-EFETIVA.md`, commitado antes de rodar, api @ 1c2967c):
   - fatores de redução pela Tabela 10 do manual de drenagem de Suzano (p. 36), porque o PDF da CETESB não tem texto;
   - Manning n de 0,013 a 0,015 (Suzano, Tab. 8, p. 31; Silva et al. 2020, p. 156);
   - obstrução: 20 cm de sedimento em Silva et al. (Revista DAE 225, p. 156). **O "−40% de vazão" atribuído à revista não
     está no artigo**; pela geometria, 20 cm num tubo de 0,6 m dão −40% de área, e é só isso que se usa;
   - resultado: Ibura 0,08 a 0,33 m³/s, Mascarenhas 0,14 a 0,26 m³/s (só cano), CIn e Jaboatão pela IDF com TR de 2 a
     10 anos (sem cadastro no alcance).
   - **Regra do nível:** ALERTA se a lâmina q50 chega a 15 cm com Q_max; ATENÇÃO se a q95 chega com Q_min. A tela mostra a
     lâmina nas duas pontas da faixa.
2. **Pico de 10 min previsto** (`docs/PRE-REGISTRO-PERFIL-CHUVA.md`): o rain_v1p (saída nova do rain_v1, mesmo protocolo)
   venceu o bloco alternado no critério escrito antes (erro do pico de 10 min no teste, só com chuva): MAE 1,69 contra
   1,87 mm, IC da diferença −0,19 a −0,17 mm. A chuva prevista passa a ser distribuída pelo pico previsto.
3. **Calibração inversa** (`docs/PRE-REGISTRO-CALIBRACAO-INVERSA.md`): rodada só nos 18 dias de calibração e nos controles
   negativos por regra. **Nenhum κ adotado:** Ibura e CIn sem discriminação; Mascarenhas com IC de 0,30 a 0,95.

## Consequências

- O balde pode encher. Reconstrução de 5 anos (só frequência, `projeto-mapi-ai/reports/reconstrucao/RELATORIO.md`):
  Mascarenhas com 9,75 episódios de ALERTA e 60,1 de ALERTA ou ATENÇÃO por ponto-mês (ligado 27% do tempo: maré no nível,
  cano pequeno e as duas pontas pessimistas juntas na ATENÇÃO); CIn 0,13; Ibura e Jaboatão 0.
- A regra da ATENÇÃO **não foi mudada** depois de ver a frequência (foi fixada antes). A variante "uma só ponta pessimista"
  entra no pré-registro da validação de 2027 só como comparação (seção 5).
- O contrato ganhou a faixa (`capacity`, `servedCapacity`) e as pontas da lâmina (`sheetQ50CmQmin`, `sheetQ95CmQmax`), e o
  pico (`peak10Q50Mm`, `peak10Q95Mm`). Modelo no ar: `flood_reg_v2` (modo sombra).
- O tornado da Fase 8.5 (±20% na chuva que leva a 15 cm) **não confirma** que a capacidade pesa sozinha: ao vivo, em
  28/11 12h48 UTC, CIn e Mascarenhas tiveram área, C e capacidade mexendo quase igual (CIn: área 56,1 cm e capacidade
  56,0 cm de amplitude), e a resposta é muito não linear no limiar (de 0 a 56 cm). Em Ibura nem 300 mm em 1 h chegam a
  15 cm. A capacidade pesa tanto quanto a entrada de água (C·A), não mais.

## Condições que reabririam a decisão

- Cadastro novo da galeria (ficha de campo, resposta a pedido) com diâmetro, declividade ou bocas de lobo no alcance.
- Aval do orientador para usar um κ calibrado.
- A validação de 2027 mostrar que a ATENÇÃO de Mascarenhas não serve (falsos demais), com a variante de uma só ponta melhor.
