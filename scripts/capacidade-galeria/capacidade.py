"""Capacidade da galeria por ponto pelo cadastro ESIG (docs/PRE-REGISTRO-CAPACIDADE-GALERIA.md, aprovado em 2026-11-21).

    python3 -I scripts/capacidade-galeria/capacidade.py

Sem rede. Lê só arquivos salvos:
  docs/extratos/esig-drenagem/geojson/<PONTO>__MI_c0.geojson e __MI_c1.geojson (prompt 1, tarefa 1)
  scripts/capacidade-galeria/entrada/ponto_terreno.csv  (terrain_run 2; fundo da bacia e área snap 10 m)
  scripts/capacidade-galeria/entrada/area_regras.csv    (área da regra candidata (c), prompt 2)
  scripts/capacidade-galeria/entrada/mdt_nos.csv        (MDT nos nós, amostra_mdt.py)
Escreve scripts/capacidade-galeria/capacidade_por_ponto.csv. Entupimento fora (capacidade com tudo limpo).
"""
from __future__ import annotations

import csv
import datetime as dt
import json
import math
import statistics
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
GEO = RAIZ / "docs" / "extratos" / "esig-drenagem" / "geojson"
ENT = AQUI / "entrada"
PONTOS = ["AV_RECIFE_IBURA", "CIN_UFPE", "AGAMENON_DERBY", "MASCARENHAS_IMBIRIBEIRA"]

# Constantes congeladas no pré-registro (seção 1)
KQ = 0.312                   # HEC-22 Eq. 7-1, SI
N_CENTRAL, N_SENS = 0.015, (0.011, 0.013)
CW_GUIA = 1.60               # HEC-22 Eq. 4-30, sem rebaixo
L_GUIA, L_SENS = 1.0, (0.6, 1.5)   # [sem fonte]
D_REF = 0.15                 # h_crit
C = 0.86
RAIO_NO_M = 30.0
RAIO_BL_M = 50.0
SNAP_LINHA_M = 0.5
D_MIN = 0.2
S_MAX = 0.10
DIF_FUNDO_MAX = 0.5
FUNDO_MIN = -5.0
DATA_EXCEL_ZERO = -2209161600000


def manning_plena(d_m: float, s: float, n: float) -> float:
    """Vazão (m³/s) de tubo circular a seção plena, HEC-22 Eq. 7-1 (SI): Q = (0,312/n) D^(8/3) S^(1/2)."""
    return KQ / n * d_m ** (8 / 3) * math.sqrt(s)


def vertedor_guia(l_m: float, d_m: float) -> float:
    """Boca de lobo de guia em ponto baixo, sem rebaixo, como vertedor (HEC-22 Eq. 4-30): Q = 1,60 L d^1,5."""
    return CW_GUIA * l_m * d_m ** 1.5


def para_mm_h(q_m3s: float, area_m2: float, c: float = C) -> float:
    """Intensidade equivalente do balde: i = Q / (C·A), em mm/h."""
    return q_m3s / (c * area_m2) * 3.6e6


def capacidade_ponto(q_entrada: float | None, q_cano: float | None) -> float | None:
    """Mínimo entre entrada e cano; desconhecido se faltar um dos dois (pré-registro, seção 5)."""
    if q_entrada is None or q_cano is None:
        return None
    return min(q_entrada, q_cano)


def fundo(p: dict) -> tuple[float | None, str]:
    z, h, cf = p.get("Z"), p.get("VLHFUNDO"), p.get("COTA_FUNDO")
    derivado = (z - h) if (z is not None and h) else None
    if cf is not None:
        if cf < FUNDO_MIN or (derivado is not None and abs(cf - derivado) > DIF_FUNDO_MAX):
            return (derivado, "Z-VLHFUNDO (COTA_FUNDO implausível)") if derivado is not None and derivado >= FUNDO_MIN else (None, "COTA_FUNDO implausível")
        return cf, "COTA_FUNDO"
    if derivado is not None:
        return derivado, "Z-VLHFUNDO"
    return None, "sem cota de fundo"


def data(p: dict) -> str:
    v = p.get("DATA_LEVANTAMENTO")
    if v in (None, DATA_EXCEL_ZERO):
        return ""
    return (dt.datetime(1970, 1, 1) + dt.timedelta(milliseconds=v)).strftime("%Y-%m-%d")


def calcula(ponto: str, terreno: dict, regras: dict, mdt: dict) -> dict:
    nos = [(f["geometry"]["coordinates"][:2], f["properties"])
           for f in json.loads((GEO / f"{ponto}__MI_c0.geojson").read_text(encoding="utf-8"))["features"]]
    linhas = [f for f in json.loads((GEO / f"{ponto}__MI_c1.geojson").read_text(encoding="utf-8"))["features"]]
    t = terreno[ponto]
    fx, fy, fz = float(t["fundo_bacia_x_utm"]), float(t["fundo_bacia_y_utm"]), float(t["fundo_bacia_cota_m"])
    r: dict = {"ponto": ponto, "fundo_bacia_x": fx, "fundo_bacia_y": fy, "fundo_bacia_cota_mdt_m": fz}
    motivos = []

    # ---- cano (seções 2 e 3)
    cand = sorted((math.dist(xy, (fx, fy)), p, xy) for xy, p in nos if (p.get("VLTBJUSAN1") or 0) > 0)
    q_cano = None
    if not cand or cand[0][0] > RAIO_NO_M:
        motivos.append(f"cano: nenhum nó com tubo a {RAIO_NO_M:.0f} m do ponto baixo"
                       + (f" (mais próximo a {cand[0][0]:.1f} m)" if cand else ""))
    else:
        dist, p, xy = cand[0]
        r.update(no_objectid=p["OBJECTID"], no_tipo=p["TIPO"], no_dist_m=round(dist, 1), no_levantamento=data(p))
        diams = [v for v in (p.get("VLTBJUSAN1"), p.get("VLTBJUSAN2")) if v]
        r["diametros_m"] = " + ".join(f"{d:g}" for d in diams)
        f0, orig0 = fundo(p)
        r["fundo_montante_m"] = None if f0 is None else round(f0, 3); r["fundo_montante_origem"] = orig0
        if any(d < D_MIN for d in diams):
            motivos.append(f"cano: diâmetro implausível ({r['diametros_m']} m < {D_MIN} m)")
        elif f0 is None:
            motivos.append("cano: nó receptor sem cota de fundo")
        else:
            saidas = []
            for ln in linhas:
                cs = ln["geometry"]["coordinates"]
                a, b = cs[0][:2], cs[-1][:2]
                for ponta, outra in ((a, b), (b, a)):
                    if math.dist(ponta, xy) <= SNAP_LINHA_M:
                        viz = min(((math.dist(outra, q), pp) for q, pp in nos if q != xy), key=lambda u: u[0], default=None)
                        if viz and viz[0] <= SNAP_LINHA_M:
                            f1, orig1 = fundo(viz[1])
                            saidas.append((f1 if f1 is not None else math.inf, ln["properties"], viz[1], orig1))
            if not saidas:
                motivos.append("cano: nenhum trecho ligado ao nó receptor pela geometria")
            else:
                f1, lp, pj, orig1 = min(saidas, key=lambda u: u[0])
                comp = lp.get("VLCOMPRIM")
                r.update(trecho_objectid=lp["OBJECTID"], trecho_comprimento_m=comp, no_jusante_objectid=pj["OBJECTID"],
                         fundo_jusante_m=None if f1 == math.inf else round(f1, 3), fundo_jusante_origem=orig1,
                         no_jusante_levantamento=data(pj))
                if f1 == math.inf or not comp:
                    motivos.append("cano: trecho sem cota de fundo na outra ponta ou sem comprimento")
                else:
                    s = (f0 - f1) / comp
                    r["declividade_m_m"] = round(s, 5)
                    if s <= 0 or s > S_MAX:
                        motivos.append(f"cano: declividade {s:.4f} fora de (0; {S_MAX}]")
                    else:
                        q_cano = sum(manning_plena(d, s, N_CENTRAL) for d in diams)
                        r["q_cano_m3s"] = round(q_cano, 4)
                        for n in N_SENS:
                            r[f"q_cano_m3s_n{n}"] = round(sum(manning_plena(d, s, n) for d in diams), 4)

    # ---- entrada (seção 4)
    zm = [float(m["z_esig"]) - float(m["mdt_m"]) for m in mdt.values()
          if m["ponto"] == ponto and m["z_esig"] not in ("", "None") and m["mdt_m"]
          and math.dist((float(m["x"]), float(m["y"])), (fx, fy)) <= RAIO_BL_M]
    desnivel = statistics.median(zm) if zm else 0.0
    r["desnivel_z_esig_menos_mdt_m"] = round(desnivel, 3); r["n_nos_desnivel"] = len(zm)
    bls = []
    for xy, p in nos:
        if p["TIPO"] != "BL" or math.dist(xy, (fx, fy)) > RAIO_BL_M:
            continue
        z = p.get("Z")
        if z is None:
            continue
        if z - desnivel <= fz + D_REF:
            bls.append(p)
    r["bl_no_alcance"] = len(bls)
    r["bl_objectids"] = " ".join(str(p["OBJECTID"]) for p in bls)
    q_ent = None
    if not bls:
        motivos.append("entrada: nenhuma BL cadastrada no alcance (50 m e cota ≤ fundo + 0,15 m)")
    else:
        q_ent = len(bls) * vertedor_guia(L_GUIA, D_REF)
        r["q_entrada_m3s"] = round(q_ent, 4)
        for l in L_SENS:
            r[f"q_entrada_m3s_L{l}"] = round(len(bls) * vertedor_guia(l, D_REF), 4)

    # ---- ponto (seções 5 e 6)
    q = capacidade_ponto(q_ent, q_cano)
    r["q_ponto_m3s"] = None if q is None else round(q, 4)
    lim = [v for v in (q_ent, q_cano) if v is not None]
    if q is None and lim:
        r["q_limite_superior_m3s"] = round(lim[0], 4)
    a10 = float(t["area_contribuinte_m2"]) if t.get("area_contribuinte_m2") else None
    ac = float(regras[ponto]["regra_c_area_m2"]) if regras.get(ponto, {}).get("regra_c_area_m2") else None
    r["area_snap10_m2"], r["area_regra_c_m2"] = a10, ac
    for nome, a in (("snap10", a10), ("regra_c", ac)):
        r[f"i_eq_mm_h_{nome}"] = round(para_mm_h(q, a), 1) if (q is not None and a) else None
    r["motivo_sem_dado"] = "; ".join(motivos)
    return r


def main() -> None:
    terreno = {x["slug"]: x for x in csv.DictReader((ENT / "ponto_terreno.csv").open(encoding="utf-8"))}
    regras = {x["ponto"]: x for x in csv.DictReader((ENT / "area_regras.csv").open(encoding="utf-8"))}
    mdt = {(m["ponto"], m["objectid"]): m for m in csv.DictReader((ENT / "mdt_nos.csv").open(encoding="utf-8"))}
    rows = [calcula(p, terreno, regras, mdt) for p in PONTOS]
    cols: list[str] = []
    for r in rows:
        cols += [k for k in r if k not in cols]
    with (AQUI / "capacidade_por_ponto.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n")
        w.writeheader()
        for r in rows:
            w.writerow({k: ("" if r.get(k) is None else r.get(k)) for k in cols})
    for r in rows:
        print({k: v for k, v in r.items() if k in ("ponto", "q_cano_m3s", "q_entrada_m3s", "q_ponto_m3s", "i_eq_mm_h_snap10",
                                                  "i_eq_mm_h_regra_c", "motivo_sem_dado")})


if __name__ == "__main__":
    main()
