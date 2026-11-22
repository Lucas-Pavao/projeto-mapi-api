"""Relatório de preenchimento dos GeoJSON do ESIG salvos por baixa_esig.py (sem rede).

    python3 -I scripts/capacidade-galeria/relatorio_preenchimento.py

Escreve docs/extratos/esig-drenagem/preenchimento_por_campo.csv: uma linha por
(ponto, serviço, camada, campo) com registros, nulos, zeros, % sem dado, mínimo, máximo,
valores distintos (até 12). "Nulo" = null, string vazia ou só espaços. Datas (epoch ms)
convertidas para AAAA-MM-DD; 1899-12-30 (epoch -2209161600000) é o "zero" de data do Excel
e é contado à parte como placeholder.
"""
from __future__ import annotations

import collections
import csv
import datetime as dt
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
BASE = RAIZ / "docs" / "extratos" / "esig-drenagem"
GEO = BASE / "geojson"
DATA_EXCEL_ZERO = -2209161600000
CAMPOS_DATA = {"DATA_LEVANTAMENTO", "CREATED_DATE", "LAST_EDITED_DATE"}


def fmt_data(ms: float) -> str:
    return (dt.datetime(1970, 1, 1) + dt.timedelta(milliseconds=ms)).strftime("%Y-%m-%d")


def main() -> None:
    manifesto = list(csv.DictReader((GEO / "MANIFESTO.csv").open(encoding="utf-8")))
    linhas = []
    for m in manifesto:
        d = json.loads((GEO / m["arquivo"]).read_text(encoding="utf-8"))
        props = [f["properties"] for f in d["features"]]
        n = len(props)
        campos = list(props[0].keys()) if props else []
        for campo in campos:
            vals = [p.get(campo) for p in props]
            nulos = sum(1 for v in vals if v is None or (isinstance(v, str) and not v.strip()))
            nums = [v for v in vals if isinstance(v, (int, float)) and not isinstance(v, bool)]
            zeros = sum(1 for v in nums if v == 0)
            placeholder = 0
            if campo in CAMPOS_DATA:
                placeholder = sum(1 for v in nums if v == DATA_EXCEL_ZERO)
                validos = [v for v in nums if v != DATA_EXCEL_ZERO]
                mn = fmt_data(min(validos)) if validos else ""
                mx = fmt_data(max(validos)) if validos else ""
                cont = collections.Counter(fmt_data(v)[:7] for v in validos)
            else:
                mn = min(nums) if nums else ""
                mx = max(nums) if nums else ""
                cont = collections.Counter(str(v).strip() for v in vals if v is not None and str(v).strip())
            sem_dado = nulos + zeros + placeholder
            distintos = "; ".join(f"{k}={c}" for k, c in sorted(cont.items(), key=lambda x: (-x[1], x[0]))[:12])
            linhas.append([
                m["ponto"], m["servico"], m["camada"], campo, n, nulos, zeros, placeholder,
                f"{100 * sem_dado / n:.0f}" if n else "", mn, mx, len(cont), distintos,
            ])
    with (BASE / "preenchimento_por_campo.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["ponto", "servico", "camada", "campo", "registros", "nulos", "zeros",
                    "data_placeholder_1899", "pct_sem_dado", "minimo", "maximo", "n_distintos", "distintos_top12"])
        w.writerows(linhas)
    print(f"{len(linhas)} linhas")
    resumo_por_ponto()


def resumo_por_ponto() -> None:
    """resumo_por_ponto.csv: contagens por tipo e faixa de distância, preenchimento hidráulico, elemento mais próximo."""
    import math
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from utm import para_utm25s

    pontos = {r["slug"]: r for r in csv.DictReader((RAIZ / "tools/terreno/pontos_criticos.csv").open(encoding="utf-8"))}
    saida = []
    for slug in ["AV_RECIFE_IBURA", "CIN_UFPE", "AGAMENON_DERBY", "MASCARENHAS_IMBIRIBEIRA"]:
        e0, n0 = para_utm25s(float(pontos[slug]["latitude"]), float(pontos[slug]["longitude"]))
        feats = json.loads((GEO / f"{slug}__MI_c0.geojson").read_text(encoding="utf-8"))["features"]
        els = []
        for f in feats:
            x, y = f["geometry"]["coordinates"][:2]
            els.append((math.dist((x, y), (e0, n0)), f["properties"]))
        els.sort(key=lambda t: t[0])
        linha = {"ponto": slug, "e_utm": round(e0, 2), "n_utm": round(n0, 2), "elementos_300m": len(els)}
        for raio in (50, 100, 300):
            sub = [p for d, p in els if d <= raio]
            for tipo in ("BL", "PV", "CG", "CAN", "FC"):
                linha[f"{tipo}_ate_{raio}m"] = sum(1 for p in sub if p["TIPO"] == tipo)
        for campo in ("VLTBJUSAN1", "VLTBMONT1", "VLHFUNDO"):
            linha[f"pct_{campo}_preenchido"] = round(100 * sum(1 for _, p in els if p.get(campo)) / len(els)) if els else ""
        linha["pct_cota_fundo_ou_z_menos_hfundo"] = round(
            100 * sum(1 for _, p in els if p.get("COTA_FUNDO") is not None or (p.get("Z") is not None and p.get("VLHFUNDO"))) / len(els)
        ) if els else ""
        tubos = sorted({p["VLTBJUSAN1"] for _, p in els if p.get("VLTBJUSAN1")})
        linha["tubos_jusante_distintos"] = " ".join(f"{t:g}" for t in tubos)
        d, p = els[0]
        linha["mais_proximo_dist_m"] = round(d, 1)
        linha["mais_proximo_tipo"] = p["TIPO"]
        linha["mais_proximo_objectid"] = p["OBJECTID"]
        linha["mais_proximo_z"] = p.get("Z")
        bl = [(d, p) for d, p in els if p["TIPO"] == "BL"]
        linha["bl_mais_proxima_dist_m"] = round(bl[0][0], 1) if bl else ""
        datas = [p["DATA_LEVANTAMENTO"] for _, p in els if p.get("DATA_LEVANTAMENTO") not in (None, DATA_EXCEL_ZERO)]
        linha["levantamento_min"] = fmt_data(min(datas)) if datas else ""
        linha["levantamento_max"] = fmt_data(max(datas)) if datas else ""
        saida.append(linha)
    with (BASE / "resumo_por_ponto.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(saida[0].keys()))
        w.writeheader()
        w.writerows(saida)
    for l in saida:
        print(l)


if __name__ == "__main__":
    main()
