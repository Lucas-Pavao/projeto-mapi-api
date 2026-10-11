# Handoff — Fase 4: IA e avaliação (2026-10-11)

**Desenho:** F1, escolhido pelo Lucas. O modelo físico do balde é o modelo principal, parametrizado só
por fontes externas (`docs/PARAMETROS-MODELO-FISICO.md`). Os baselines são a climatologia sazonal (INMET)
e uma regra de chuva com o limiar da IDF (50,3 mm/h). Os 18 dias com alagamento registrado são o
conjunto de teste. A rede de Petri ficou fora do escopo por decisão do autor.

**1ª análise** (pré-registro congelado em `77e41de`, escores cegos `4615d416…`, rótulos `3753ada8…`):
- **H1 inconclusiva** (ΔAUC +0,113, IC 95% [−0,010; +0,252]) e degenerada: o balde nunca transborda,
  porque a reanálise não passa de 16,7 mm/h.
- **H2 não testada**, pela regra de parada.
- As 24 variantes de sensibilidade dão a mesma categoria (houve errata: antes estava "30").
- Reprodutível por `avaliacao/reproduzir.sh` (projeto-mapi-ai `feat/avaliacao-f1`), com hashes idênticos.

**2ª análise** (pluviômetro ANA; pré-registro `2b9352c`): **não testada**. O portão do protocolo
reprovou: o critério C2d da F2-DADOS-11 falhou, e o Arquiteto não aceitou o desvio depois de ver o
número. A ANA saiu da quarentena de fuso (UTC−3 verificado) e a chuva horária está no código, mas
sem uso confirmatório.

**Interpretação** (`docs/INTERPRETACAO-F1.md`): a forçante, e não o modelo, é a limitação dominante.
A condição de transbordar vira P > f·i_T. A reanálise tem cerca de 1 h de viés. O enquadramento G
(dados auditáveis + resultado inconclusivo com a causa medida) é a contribuição sustentável
(`docs/PRONTIDAO-ACADEMICA.md`).

**Produção:**
- A IA não imputa mais em silêncio: devolve 422 com `missing_features`.
- A resposta traz `modelValidationStatus = NOT_VALIDATED`, com trava no código.
- O painel mostra "Previsão experimental — modelo não validado".
- Rótulos rastreáveis: chuva com grandeza e fonte; maré em "m (NR da DHN)"; área contribuinte com a faixa do snap.

**Entregáveis:**
- `docs/MODELAGEM-IA.md`, com exemplo real do dataset;
- `docs/medidas/comparacao-modelos-f1.xlsx`;
- `docs/apresentacao/MAPi-disciplina.pptx` (não renderizado; conferir os slides 6, 8 e 9).

**Pendências:** o pedido LAI 05 (registros com hora e endereço), que é a única via para um teste
com poder; validar a camada de terreno em celular real; reabrir a análise com pluviômetro só por
novo pré-registro com um critério de plausibilidade que tenha fonte.
