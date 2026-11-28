"""Capacidade efetiva por ponto como faixa (docs/PRE-REGISTRO-CAPACIDADE-EFETIVA.md, onda 16, Fase 4.1).

    python3 -I scripts/capacidade-galeria/capacidade_efetiva.py <ponto_terreno.csv da Fase 3> <saída .json>

Reaproveita a escolha dos elementos do ESIG de capacidade.py (pré-registro de 21/11) com o fundo do terreno da Fase 3.
Sem rede. Constantes abaixo congeladas no pré-registro de 23/11.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import capacidade as CAP  # noqa: E402

PONTOS = ["AV_RECIFE_IBURA", "CIN_UFPE", "MASCARENHAS_IMBIRIBEIRA", "JABOATAO_CENTRO"]
C = 0.86
D_BL = 0.15                      # h_crit
L_MIN, L_MAX = 0.6, 1.5          # prior sem medição
F_MIN, F_MAX = 0.50, 0.80        # DAEE/CETESB, ponto baixo: grelha 0,50 ... guia 0,80 (tipo não cadastrado)
N_MIN, N_MAX = 0.013, 0.015
SEDIMENTO_M = 0.20               # Silva et al. (2020), Revista DAE 225, p. 156
TRS = (2.0, 10.0)
TE_MIN = 10.0
FONTES = {
    "fator_reducao_bl": "Prefeitura de Suzano, Manual de Manejo das Águas Pluviais (2022), Tabela 10, p. 36 do PDF, citando DAEE/CETESB (1980)",
    "vertedor_guia": "HEC-22, Eq. 4-30 (SI): Q = 1,60 L d^1,5",
    "manning_n": "Suzano (2022), Tabela 8, p. 31 do PDF; Silva et al. (2020), Revista DAE 225, p. 156 (n = 0,014)",
    "sedimento_20cm": "Silva et al. (2020), Revista DAE v. 68 n. 225, p. 156, DOI 10.36659/dae.2020.059",
    "idf": "Silva e Araújo (2013), Eq. 16 (PARAMETROS-MODELO-FISICO.md)",
    "comprimento_abertura_L": "prior sem medição (0,6 a 1,5 m): o ESIG não traz o comprimento",
}


def idf(tr: float, t_min: float) -> float:
    return 1380.2176 * tr ** 0.19369 / (t_min + 22.0) ** 0.78201


def manning_secao(d: float, s: float, n: float, sedimento: float = 0.0) -> float:
    """Manning a seção plena de tubo circular; com sedimento, o segmento de baixo (altura `sedimento`) sai da área e a
    superfície do sedimento entra no perímetro molhado."""
    r = d / 2
    area, perim = math.pi * r * r, math.pi * d
    if sedimento > 0:
        if sedimento >= d:
            return 0.0
        th = 2 * math.acos((r - sedimento) / r)
        seg = r * r / 2 * (th - math.sin(th))
        corda = 2 * r * math.sin(th / 2)
        area -= seg
        perim = perim - r * th + corda
    return 1 / n * area * (area / perim) ** (2 / 3) * math.sqrt(s)


def entrada(n_bl: int) -> tuple[float, float]:
    unit = lambda L, f: 1.60 * L * D_BL ** 1.5 * f
    return n_bl * unit(L_MIN, F_MIN), n_bl * unit(L_MAX, F_MAX)


def faixa(r: dict, area: float) -> dict:
    out = {"componentes": {}, "observacao": []}
    q_e = q_c = None
    if r.get("bl_no_alcance"):
        q_e = entrada(int(r["bl_no_alcance"]))
        out["componentes"]["entrada"] = {"bocas": int(r["bl_no_alcance"]), "objectids": r.get("bl_objectids"),
                                         "q_min_m3s": round(q_e[0], 4), "q_max_m3s": round(q_e[1], 4)}
    if r.get("q_cano_m3s") is not None:
        diams = [float(x) for x in str(r["diametros_m"]).split(" + ")]
        s = float(r["declividade_m_m"])
        qmin = sum(manning_secao(d, s, N_MAX, SEDIMENTO_M if d > SEDIMENTO_M else 0.0) for d in diams)
        qmax = sum(manning_secao(d, s, N_MIN) for d in diams)
        q_c = (qmin, qmax)
        perda = {f"{d:g}": round(1 - manning_secao(d, 1.0, 1.0, SEDIMENTO_M) / manning_secao(d, 1.0, 1.0), 3) for d in diams}
        out["componentes"]["cano"] = {"diametros_m": diams, "declividade_m_m": s, "q_min_m3s": round(qmin, 4),
                                      "q_max_m3s": round(qmax, 4), "perda_pela_geometria_com_20cm": perda}
    if q_e and q_c:
        lo, hi, base = min(q_e[0], q_c[0]), min(q_e[1], q_c[1]), "ESIG: mínimo entre entrada e cano"
    elif q_e or q_c:
        lo, hi = q_e or q_c
        falta = "cano" if q_e else "entrada"
        base = f"ESIG: limite superior ({falta} sem cadastro)"
    else:
        lo, hi = (C * idf(tr, TE_MIN) * area / 3.6e6 for tr in TRS)
        base = "sem cadastro: capacidade de projeto, IDF TR 2 a 10 anos em Te = 10 min"
    out.update({"base": base, "q_min_m3s": round(lo, 4), "q_max_m3s": round(hi, 4), "area_m2": area,
                "i_eq_min_mm_h": round(lo / (C * area) * 3.6e6, 1), "i_eq_max_mm_h": round(hi / (C * area) * 3.6e6, 1),
                "i_projeto_tr5_mm_h": round(idf(5.0, TE_MIN), 1), "motivos_sem_dado_esig": r.get("motivo_sem_dado")})
    return out


def main(terreno_csv: Path, saida: Path) -> None:
    terreno = {x["slug"]: x for x in csv.DictReader(terreno_csv.open(encoding="utf-8"))}
    mdt = {(m["ponto"], m["objectid"]): m for m in csv.DictReader((CAP.ENT / "mdt_nos.csv").open(encoding="utf-8"))}
    res = {"pre_registro": "projeto-mapi-api/docs/PRE-REGISTRO-CAPACIDADE-EFETIVA.md", "fontes": FONTES,
           "constantes": {"C": C, "d_bl_m": D_BL, "L_m": [L_MIN, L_MAX], "fator_bl": [F_MIN, F_MAX], "n": [N_MIN, N_MAX],
                          "sedimento_m": SEDIMENTO_M, "tr_anos_sem_cadastro": TRS, "te_min": TE_MIN},
           "terreno": {"arquivo": str(terreno_csv.name), "sha256": hashlib.sha256(terreno_csv.read_bytes()).hexdigest()},
           "pontos": {}}
    for p in PONTOS:
        t = terreno[p]
        area = float(t["area_contribuinte_m2"])
        tem_esig = (CAP.GEO / f"{p}__MI_c0.geojson").exists()
        r = CAP.calcula(p, terreno, {}, mdt) if tem_esig else {"motivo_sem_dado": "fora do cadastro ESIG (Jaboatão)"}
        res["pontos"][p] = faixa(r, area)
        f = res["pontos"][p]
        print(p, f["base"], f["q_min_m3s"], f["q_max_m3s"], f["i_eq_min_mm_h"], f["i_eq_max_mm_h"], "|", r.get("motivo_sem_dado"))
    saida.parent.mkdir(parents=True, exist_ok=True)
    saida.write_text(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
