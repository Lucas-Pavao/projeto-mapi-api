# Pré-registro: capacidade da galeria pelo cadastro ESIG

- Data: 2026-11-21 · Prompt 1, tarefa 2 · Status: **aprovado pelo Lucas em 2026-11-21** ("todos aprovados"), antes do cálculo. Resultado em `PARAMETROS-MODELO-FISICO.md`, seção "Capacidade pelo cadastro ESIG".
- Este método é escrito e congelado **antes** de rodar sobre os pontos. Nenhum dia de alagamento foi aberto para escolher qualquer valor.
- Base: o que o cadastro tem de fato (`docs/extratos/esig-drenagem/RELATORIO-PREENCHIMENTO.md`). As cotas de montante e jusante estão
  vazias, os trechos não têm diâmetro e CIn e Imbiribeira quase não têm dado hidráulico.
- Saída prevista: `scripts/capacidade-galeria/capacidade_por_ponto.csv`, sem rede no cálculo, lendo só os GeoJSON salvos.

## 1. Fórmulas e constantes (com fonte)

| Item | Fórmula ou valor | Fonte |
|---|---|---|
| Cano a seção plena (Manning, SI) | `Q = (0,312 / n) · D^(8/3) · S^(1/2)` (m³/s; D em m; S em m/m) | FHWA HEC-22, 3ª ed. (2009, rev. 2013), Eq. 7-1, p. 7-2 |
| n de Manning, tubo de concreto | **central 0,015**; sensibilidade 0,011 e 0,013 | HEC-22, Tabela 3-4 (p. 3-14): concreto 0,010–0,015; Tabela 7-1 (p. 7-4): concreto liso **novo, em laboratório** 0,010–0,011, e o texto da p. 7-3 diz que o valor de campo varia com abrasão, corrosão e juntas. Escolho o topo da faixa porque a rede é antiga e sem inspeção |
| Boca de lobo de guia em ponto baixo, vertedor | `Q = Cw · L · d^1,5`, Cw = 1,60 (sem rebaixo) | HEC-22, Eq. 4-30, p. 4-60 |
| Boca de guia como orifício (d > 1,4 h) | `Q = Co · h · L · (2 g d_o)^0,5`, Co = 0,67 | HEC-22, Eq. 4-31a, p. 4-60 |
| Grelha em ponto baixo | vertedor `Q = 1,66 · P · d^1,5`; orifício `Q = 0,67 · Ag · (2 g d)^0,5` | HEC-22, Eq. 4-26 e 4-27, p. 4-57 |
| Lâmina de referência na boca de lobo | d = 0,15 m (h_crit) | `PARAMETROS-MODELO-FISICO.md`, item 9 |
| Comprimento da abertura da boca de guia | **L = 1,0 m** `[sem fonte]`; sensibilidade 0,6 e 1,5 m | o ESIG não tem tipo nem dimensão; vem da ficha de campo (prompt 3) ou do pedido 06 |
| Altura da abertura | h = 0,15 m `[sem fonte]` | idem; com d = h = 0,15 m a boca opera como vertedor (d ≤ h) |
| Conversão para o balde | `i_eq (mm/h) = Q / (C · A) · 3,6·10⁶` | mesma unidade do balde (`bucket10.py`: saída = C · i · A); C = 0,86 |

Caso à mão (vai para o teste unitário): D = 0,6 m, S = 0,005, n = 0,015 → Q = 0,377 m³/s. Uma boca de guia com L = 1,0 m e
d = 0,15 m → Q = 0,0930 m³/s. Q = 0,3 m³/s, C = 0,86, A = 10.000 m² → i_eq = 125,6 mm/h.

## 2. Qual elemento é o "ponto baixo"

1. Ponto baixo do terreno = o **fundo da bacia** já calculado pelo terreno (`fundo_bacia_x_utm`, `fundo_bacia_y_utm` em
   `ponto_terreno.csv`, a mesma regra de área que sair do prompt 2).
2. **Nó receptor** = o elemento do ESIG (BL, PV ou CG) mais próximo desse ponto, a até 30 m, **que tenha `VLTBJUSAN1` > 0**. Sem nenhum
   a 30 m: capacidade do cano = "sem dado (nenhum nó com tubo a 30 m do ponto baixo)".

## 3. Capacidade do cano

1. **Diâmetro:** `VLTBJUSAN1` do nó receptor (em metros; relatório, seção 4). Se houver `VLTBJUSAN2` > 0, as duas saídas somam.
   D < 0,2 m é implausível para galeria pluvial (há um registro de 0,1 m no Ibura): sem dado.
2. **Trecho de saída:** a linha da camada 1 com uma ponta a ≤ 0,5 m do nó receptor e a outra ponta a ≤ 0,5 m de outro nó (94% das
   pontas casam assim; relatório, seção 1). Se houver mais de uma, a de **menor cota de fundo** na outra ponta (é para lá que a água
   desce). Comprimento: `VLCOMPRIM`.
3. **Cota de fundo de cada nó:** `COTA_FUNDO`, se preenchida e plausível; senão `Z − VLHFUNDO`. Implausível: `COTA_FUNDO` diferente de
   `Z − VLHFUNDO` em mais de 0,5 m, ou abaixo de −5 m (o −93,6 m do OBJECTID 3352).
4. **Declividade:** `S = (fundo_montante − fundo_jusante) / VLCOMPRIM`. S ≤ 0 (contrapendente ou plana) ou S > 0,10: sem dado, com o
   motivo. Não troco por declividade mínima de projeto: isso seria inventar valor.
5. Q do cano pela Eq. 7-1 com n = 0,015.

## 4. Capacidade de entrada

1. **Quais bocas de lobo drenam para o ponto baixo:** as do ESIG com `TIPO = BL` a até 50 m do ponto baixo **e** com cota da tampa
   `Z` ≤ cota do fundo + 0,15 m. São as que ficam dentro da lâmina quando ela chega a 15 cm: a água empoçada entra por elas. A cota `Z`
   do ESIG e a do MDT podem ter referências diferentes (relatório, seção 1, item 3): a comparação usa o desnível entre os dois medido
   nos nós a até 50 m (mediana de `Z − MDT`), declarado na saída.
2. **Capacidade por boca:** tipo-padrão boca de guia sem rebaixo, Eq. 4-30, L = 1,0 m, d = 0,15 m → 0,093 m³/s por boca. Quando a
   ficha de campo trouxer tipo e dimensão, a boca usa a equação do tipo dela (Eq. 4-26/4-27 para grelha).
3. **Entrada** = soma das bocas. Zero bocas: "sem dado (nenhuma BL cadastrada no alcance)", **não** zero de capacidade, porque o
   cadastro está incompleto (a Imbiribeira não tem nenhuma BL cadastrada a 300 m).

## 5. Capacidade do ponto

`Q_ponto = mínimo(Q_entrada, Q_cano)`. Não é média: a soma das bocas limita o que entra, o cano limita o que sai. Se só um dos dois
existir, o ponto fica "sem dado" (o mínimo de um valor e de um desconhecido é desconhecido), e o valor conhecido sai como limite
superior em coluna própria.

**Entupimento fora:** esta é a capacidade com tudo limpo. O fator de entupimento entra só no modelo de estados (prompt 3,
`MODELO-DISPONIBILIDADE-GALERIA.md`).

## 6. Área usada na conversão para mm/h

A conversão divide pela área contribuinte, e no Derby e no Ibura essa área está em revisão (62 e 208 m² com snap de 10 m; 14.875 e
10.880 m² com 20 m; `PROPOSTA-REGRA-AREA-CONTRIBUICAO.md`). Proposta: **mostrar as duas versões** até a regra da área ser aprovada:
`i_eq` com a área atual (snap 10 m) e com a área da regra candidata (c) do prompt 2. A capacidade em m³/s não depende da área e é a
grandeza principal do CSV. Com área pequena, `i_eq` fica enorme (por exemplo, 0,093 m³/s em 62 m² dá ~6.300 mm/h): isso é sinal de
área errada, não de galeria folgada, e o texto da saída diz isso.

## 7. Saída e testes

- `capacidade_por_ponto.csv`, uma linha por ponto: nó receptor (OBJECTID, tipo, distância), D, trecho (OBJECTID da linha, comprimento),
  fundos e origem de cada fundo (`COTA_FUNDO` ou `Z − VLHFUNDO`), S, Q do cano (n 0,011/0,013/0,015), nº de BL no alcance por tipo,
  Q de entrada, Q do ponto, `i_eq` nas duas áreas, data de levantamento dos nós usados, e `motivo_sem_dado` quando faltar.
- Testes (`python3 -I -m unittest`): Manning e conversão contra o caso à mão da seção 1; vertedor da boca de guia; regra do mínimo e
  do "sem dado".
- Comparação, sem rodar o alerta nem a validação: `i_eq` contra os 125,4 mm/h atuais e contra a faixa `[sem fonte]` de
  `PARAMETROS-MODELO-FISICO.md` (0 a 60 mm/h).

## 8. O que eu espero, antes de rodar (para não ser surpresa)

- Ibura: há dado para os dois lados; o resultado depende muito de L (sem fonte) e do nó receptor.
- Derby: o ponto baixo do MDT fica longe do cadastro (elemento mais próximo do ponto a 54,5 m); o nó receptor pode não existir a 30 m.
- CIn e Imbiribeira: sem dado no cano (CIn) e na entrada (os dois). É o resultado honesto com o cadastro público.
