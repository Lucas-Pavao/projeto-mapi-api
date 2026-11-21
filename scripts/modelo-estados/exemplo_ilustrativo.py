"""Conta de exemplo de docs/MODELO-DISPONIBILIDADE-GALERIA.md, seção 5. Valores ILUSTRATIVOS, sem fonte.

    python3 -I scripts/modelo-estados/exemplo_ilustrativo.py
"""
lam1, lam2, mu, nu = 1 / 60, 1 / 90, 1 / 20, 1 / 180   # por dia: L->P, P->E, E->L corretiva, P/E->L preventiva
# regime permanente de pi.Q = 0 (balanço por estado)
pL = 1.0
pP = pL * lam1 / (lam2 + nu)
pE = pP * lam2 / (mu + nu)
s = pL + pP + pE
pi = {"L": pL / s, "P": pP / s, "E": pE / s}

C, A, ICAP, DT, AREA_DEPRESSAO = 0.86, 10000.0, 125.4, 10.0, 400.0
CHUVA = [5, 15, 25, 10, 5, 0]   # mm por passo de 10 min, sintética
FATOR = {"L": 1.0, "P": 0.5, "E": 0.0}


def lamina_max_mm(f: float) -> float:
    V = hmax = 0.0
    for p in CHUVA:
        V = max(0.0, V + C * p * A / 1000 - f * C * ICAP * A / 1000 * DT / 60)
        hmax = max(hmax, V / AREA_DEPRESSAO)
    return 1000 * hmax


if __name__ == "__main__":
    h = {s_: lamina_max_mm(f) for s_, f in FATOR.items()}
    print("pi:", {k: round(v, 3) for k, v in pi.items()})
    print("lâmina máx. (mm):", {k: round(v) for k, v in h.items()})
    print("P(h >= 150 mm):", round(sum(pi[k] for k in h if h[k] >= 150), 3))
