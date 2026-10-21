# 0014 — Simulação do balde (M1a) servida pela IA

- Status: **aprovado** (2026-10-21, Lucas, pergunta 32)
- Data: 2026-10-21
- Decisores: Orquestrador do redesign (autor), Lucas
- Tarefa: F0-REDESIGN-05

## Contexto

A tela "Simulador do balde (M1a)" deixa o usuário montar uma chuva e ver a lâmina num ponto. O modelo M1a está em `projeto-mapi-ai/avaliacao/balde.py`: `simular_ponto(P_mm_h, pt, p)`, passo horário, curva de volume do terreno (`Ponto.z_curva_m`, `v_curva_m3`), `Params` (C = 0,86; iT = 60,1 mm/h; f = 1,0; h_crit = 150 mm), `g ≡ 1`.

O protótipo do canvas (`sim.js`, função `balde`) é diferente: passo de 5 min, curva prismática e freio de maré (`tideBrake`). O freio de maré é a variante B2b, que fica fora até fechar Δ (decisão de 2026-10-10; ADR 0009, emendas 1 e 2). Os parâmetros foram congelados antes da avaliação (`docs/PARAMETROS-MODELO-FISICO.md`, pré-registro).

## Opções consideradas

### A — Reimplementar o balde em TypeScript no front
A favor: sem rede. Contra: segunda implementação da fórmula; risco de divergir da avaliada.

### B — Reimplementar em Java no backend
A favor: um serviço a menos na chamada. Contra: mesmo problema de A.

### C — IA expõe `POST /v1/simulate/bucket` importando `avaliacao/balde.py`; backend repassa
A favor: uma única implementação, a mesma da avaliação; teste de regressão direto. Contra: uma chamada a mais; a IA precisa da curva do terreno, que está no banco do backend.

## Decisão

Opção C.

- IA: `POST /v1/simulate/bucket` com `P_mm_h` (lista horária, 1 a 72 valores, sem NaN), `point` (`slug`, `area_m2`, `z_curva_m`, `v_curva_m3`, `rua_rel_m`) e `params` opcionais só dentro das faixas de sensibilidade já registradas: `f` de 0 a 1 em passos de 0,2 e `curve_scale` de 0,7 a 1,3 (`docs/PARAMETROS-MODELO-FISICO.md:66,69`); `C`, `iT_mm_h`, `hcrit_mm` e `snap_m` ficam fixos nos valores de `Params`. Valor fora da faixa → 422. Resposta: `h_mm[]`, `n_fora_da_curva`, `hmax_mm`, `t_cross_h` (índice 0-based da primeira hora com `h ≥ h_crit`, ou `null`), `params_usados`, `model = "M1a"`.
- Backend: `POST /api/model/simulate/bucket` com `slug` e `P_mm_h`; monta a curva a partir de `point_terrain_curve` e repassa. Ponto sem terreno (Jaboatão) → 422.
- Sem controle de maré. Sem escrita no banco. Resposta marcada `illustrative = true`; o front mostra "Simulação — não é previsão".
- Teste de regressão (pytest): para os pontos e chuvas dos dias de teste já usados na avaliação, a saída do endpoint é idêntica (`np.array_equal`) à de `simular_ponto`. O endpoint não muda `balde.py`; `HASHES.sha256` continua batendo (ele lista arquivos de `MAPI/avaliacao-f1/`; conferir com `sha256sum -c` a partir dessa pasta).

## Consequências

- Melhor: uma fórmula só; o simulador mostra exatamente o modelo avaliado.
- Pior: a tela depende da IA no ar.
- Muda: `mapi_ai/app.py` ganha uma rota; backend ganha um cliente e um endpoint.
- Medida: pytest de regressão verde; `cd avaliacao-f1 && sha256sum -c ../projeto-mapi-ai/avaliacao/HASHES.sha256` verde (5/5 em 2026-10-21).

## Condições que reabririam a decisão

- Δ fechado por fonte (ADR 0009): aí entra a variante com maré, por novo ADR.
- Necessidade de rodar o simulador sem a IA (modo demonstração usa o `sim.js`, rotulado como cenário sintético).
