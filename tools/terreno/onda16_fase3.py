"""Onda 16, Fase 3 (diagnóstico, não muda o alerta): ponto baixo de Mascarenhas e posição nova de Jaboatão.

Uso (container GDAL do run.sh, /terreno = saída com mosaico.vrt que inclui as folhas de Jaboatão e whitebox/):
    tools/terreno/run.sh onda16_fase3

Mesma janela de 2 km e mesmo motor (WhiteboxTools v2.4.0) de `atributos` e `area_regras`.

Mascarenhas (MASCARENHAS_IMBIRIBEIRA, posição do alinhamento de 21/11):
  - profundidade da depressão (fill - MDT) no pixel do ponto e no exutório do snap de 10 m;
  - caminho D8 a jusante do exutório até a primeira célula com fill - MDT > 2 cm (onde a água para de verdade);
  - depressões (fill - MDT > 2 cm, 8-conexas) a até 200 m do ponto: fundo, profundidade, área, volume, transbordo e
    distância ao eixo da Av. Mascarenhas de Morais (OSM);
  - perfil do MDT ao longo do eixo da avenida (a cada 2 m, até 200 m do ponto).
Jaboatão (JABOATAO_CENTRO): só o LUGAR citado em fontes públicas (Av. Barão de Lucena, margem do Rio Duas Unas); nenhuma
data nem intensidade. Candidato = pixel da avenida (a <= 6 m do eixo do OSM) a até 200 m do cruzamento da avenida com o
rio, fora do vão da ponte (>= 20 m do eixo do rio), com a maior profundidade de depressão; sem depressão, o de menor cota.
Para o candidato: bacias com snap de 5, 10 e 20 m, regra (c) e a trava (a).
Saída em /terreno/onda16_fase3/: fase3.json, perfil_mascarenhas.csv, depressoes_mascarenhas.csv, PNG por caso.
"""
import csv
import json
import math
import os
import sys
from collections import deque

import numpy as np
from osgeo import osr

sys.path.insert(0, "/tools/terreno")
import terreno as T  # noqa: E402
import area_regras as AR  # noqa: E402

OUT = "/terreno/onda16_fase3"
LUGAR = "/tools/terreno/vias_osm/jaboatao_lugar/barao_lucena_duas_unas.json"
DD_MIN = 0.02


def janela(p):
    ds, gt = T.vrt_info(); b = ds.GetRasterBand(1)
    n, xo, yo, arr, gtw = T.le_janela(b, gt, p, ds.RasterXSize, ds.RasterYSize)
    W = n + 2
    X = gtw[0] + (np.arange(W) + 0.5) * gtw[1]; Y = gtw[3] + (np.arange(W) + 0.5) * gtw[5]
    XX, YY = np.meshgrid(X, Y)
    return arr, gtw, X, Y, XX, YY, gt[1] * abs(gt[5])


def componentes(mask):
    """Rótulos 8-conexos (BFS; o container não tem scipy)."""
    lab = np.zeros(mask.shape, dtype=np.int32); k = 0
    H, W = mask.shape
    for i, j in zip(*np.nonzero(mask)):
        if lab[i, j]:
            continue
        k += 1; lab[i, j] = k; q = deque([(i, j)])
        while q:
            a, b = q.popleft()
            for da in (-1, 0, 1):
                for db in (-1, 0, 1):
                    u, v = a + da, b + db
                    if 0 <= u < H and 0 <= v < W and mask[u, v] and not lab[u, v]:
                        lab[u, v] = k; q.append((u, v))
    return lab, k


def linhas_osm(path, filtro):
    t = osr.CoordinateTransformation(T.srs4326(), T.srs31985())
    out = []
    for e in json.load(open(path, encoding="utf-8"))["elements"]:
        if e.get("type") == "way" and filtro(e.get("tags", {})):
            out.append([t.TransformPoint(g["lon"], g["lat"])[:2] for g in e["geometry"]])
    return out


def amostra_linha(linhas, x0, y0, raio, passo=2.0):
    pts = []
    for ln in linhas:
        for (xa, ya), (xb, yb) in zip(ln[:-1], ln[1:]):
            L = math.hypot(xb - xa, yb - ya)
            for k in range(int(L // passo) + 1):
                x, y = xa + (xb - xa) * k * passo / L, ya + (yb - ya) * k * passo / L
                if math.hypot(x - x0, y - y0) <= raio:
                    pts.append((x, y))
    return pts


def idx(X, Y, x, y):
    return int(np.argmin(np.abs(Y - y))), int(np.argmin(np.abs(X - x)))


def bacia_info(arr, par, ordem, acc, res, dist, apx, rua, W):
    if res is None:
        return None
    a_, ei, ej = res
    bac = T.bacia_do_exutorio(par, ordem, arr.size, ei * W + ej).reshape(arr.shape)
    area = int(round(bac.sum() * apx))
    V, h, transb = AR.lamina_ilustrativa(arr, bac, dist, area, rua, W)
    return {"area_m2": area, "exutorio_dist_m": round(float(dist[ei, ej]), 1), "exutorio_cota_m": round(float(arr[ei, ej]), 3),
            "ilustr_lamina_mm": h, "ilustr_passa_topo_curva": bool(transb) if transb is not None else None}, bac, (ei, ej)


def mascarenhas(rel):
    p = next(q for q in T.le_pontos() if q["slug"] == "MASCARENHAS_IMBIRIBEIRA")
    arr, gtw, X, Y, XX, YY, apx = janela(p)
    W = arr.shape[1]; valido = arr != T.ND
    dist = np.hypot(XX - p["x"], YY - p["y"])
    fill, par, ordem, acc = T.hidrologia(arr, gtw, p["slug"])
    dd = np.where(valido, fill - arr, 0.0)
    pl, pc = np.unravel_index(int(np.argmin(dist)), dist.shape)
    res10 = T.maximo_no_raio(acc, valido, dist, 10, pl, pc)
    _, ei, ej = res10
    av = linhas_osm(f"{AR.OSM}/MASCARENHAS_IMBIRIBEIRA.json", lambda t: "Mascarenhas" in (t.get("name") or ""))
    de_av = AR.dist_ao_eixo(XX, YY, av) if av else np.full(arr.shape, np.inf)
    # caminho a jusante do exutório até a água parar (depressão)
    caminho, k, comp = [], ei * W + ej, 0.0
    for _ in range(3000):
        i, j = divmod(k, W)
        caminho.append((i, j))
        if dd[i, j] > DD_MIN or par[k] < 0:
            break
        nk = par[k]; ni, nj = divmod(nk, W)
        comp += math.hypot(ni - i, nj - j) * gtw[1]; k = nk
    fi, fj = caminho[-1]
    jus = {"comprimento_m": round(comp, 1), "celulas": len(caminho), "cota_inicio_m": round(float(arr[ei, ej]), 3),
           "cota_fim_m": round(float(arr[fi, fj]), 3), "termina_em": "depressao" if dd[fi, fj] > DD_MIN else "borda/saida",
           "fim_dist_ponto_m": round(float(dist[fi, fj]), 1), "fim_dist_eixo_avenida_m": round(float(de_av[fi, fj]), 1),
           "fim_profundidade_m": round(float(dd[fi, fj]), 3)}
    # depressões a até 200 m
    m = (dd > DD_MIN) & (dist <= 200) & valido
    lab, nlab = componentes(m)
    deps = []
    for c in range(1, nlab + 1):
        cm = lab == c
        cells = int(cm.sum())
        if cells < 2:
            continue
        ii, jj = np.unravel_index(int(np.argmax(np.where(cm, dd, -1))), dd.shape)
        deps.append({"id": c, "celulas": cells, "area_m2": round(cells * apx, 1), "profundidade_max_m": round(float(dd[ii, jj]), 3),
                     "volume_m3": round(float(dd[cm].sum() * apx), 2), "fundo_cota_m": round(float(arr[ii, jj]), 3),
                     "transbordo_cota_m": round(float(fill[ii, jj]), 3), "fundo_dist_ponto_m": round(float(dist[ii, jj]), 1),
                     "fundo_dist_eixo_avenida_m": round(float(de_av[ii, jj]), 1),
                     "toca_avenida_6m": bool((cm & (de_av <= 6)).any()),
                     "fundo_lat_lon": _latlon(X[jj], Y[ii])})
    deps.sort(key=lambda d: (not d["toca_avenida_6m"], d["fundo_dist_ponto_m"]))
    # perfil ao longo da avenida
    perfil = []
    for x, y in amostra_linha(av, p["x"], p["y"], 200):
        i, j = idx(X, Y, x, y)
        if valido[i, j]:
            perfil.append({"dist_ponto_m": round(math.hypot(x - p["x"], y - p["y"]), 1), "x": round(x, 1), "y": round(y, 1),
                           "cota_m": round(float(arr[i, j]), 3), "fill_menos_mdt_m": round(float(dd[i, j]), 3)})
    rua = float(np.median(arr[(dist <= 3) & valido]))
    b10 = bacia_info(arr, par, ordem, acc, res10, dist, apx, rua, W)
    rel["mascarenhas"] = {
        "ponto": {"lat": p["latitude"], "lon": p["longitude"], "cota_pixel_m": round(float(arr[pl, pc]), 3), "cota_rua_m": round(rua, 3),
                  "profundidade_depressao_m": round(float(dd[pl, pc]), 3),
                  "profundidade_max_raio5m": round(float(dd[(dist <= 5) & valido].max()), 3),
                  "dist_eixo_avenida_m": round(float(de_av[pl, pc]), 1)},
        "exutorio_snap10": {"dist_m": round(float(dist[ei, ej]), 1), "cota_m": round(float(arr[ei, ej]), 3),
                            "profundidade_depressao_m": round(float(dd[ei, ej]), 3), "area_m2": b10[0]["area_m2"],
                            "ilustr_lamina_mm": b10[0]["ilustr_lamina_mm"]},
        "a_jusante_do_exutorio": jus, "depressoes_200m": deps[:15], "n_depressoes_200m": len(deps),
        "perfil_avenida": {"n": len(perfil), "cota_min_m": min((q["cota_m"] for q in perfil), default=None),
                           "cota_max_m": max((q["cota_m"] for q in perfil), default=None)}}
    T.write_csv(f"{OUT}/perfil_mascarenhas.csv", sorted(perfil, key=lambda q: (q["y"], q["x"])),
                ["dist_ponto_m", "x", "y", "cota_m", "fill_menos_mdt_m"])
    T.write_csv(f"{OUT}/depressoes_mascarenhas.csv", deps, list(deps[0].keys()) if deps else ["id"])
    bacias = {"snap10": b10[1]}; ex = {"snap10": b10[2]}
    # bacia da primeira depressão da avenida (proposta de ponto baixo), se houver
    dav = [d for d in deps if d["toca_avenida_6m"]]
    if dav:
        d0 = dav[0]
        cm = lab == d0["id"]
        ii, jj = np.unravel_index(int(np.argmax(np.where(cm, dd, -1))), dd.shape)
        dist_d = np.hypot(XX - X[jj], YY - Y[ii])
        rua_d = float(np.median(arr[(dist_d <= 3) & valido]))
        resd = T.maximo_no_raio(acc, valido, dist_d, 10, ii, jj)
        bd = bacia_info(arr, par, ordem, acc, resd, dist_d, apx, rua_d, W)
        rel["mascarenhas"]["proposta_ponto_baixo"] = {"depressao_id": d0["id"], "lat_lon": d0["fundo_lat_lon"],
                                                      "dist_ponto_m": d0["fundo_dist_ponto_m"], "cota_rua_m": round(rua_d, 3),
                                                      "snap10": bd[0]}
        bacias["prop"] = bd[1]; ex["prop"] = bd[2]
    # projeção do ponto no eixo da avenida (o alinhamento usou o centro dos lotes 2557 e 2857 do Nominatim, fora da via)
    best = None
    for ln in av:
        for (xa, ya), (xb, yb) in zip(ln[:-1], ln[1:]):
            dx, dy = xb - xa, yb - ya; L2 = dx * dx + dy * dy
            if L2 == 0:
                continue
            u = max(0.0, min(1.0, ((p["x"] - xa) * dx + (p["y"] - ya) * dy) / L2))
            x, y = xa + u * dx, ya + u * dy
            d = math.hypot(x - p["x"], y - p["y"])
            if best is None or d < best[0]:
                best = (d, x, y)
    _, xq, yq = best
    qi, qj = idx(X, Y, xq, yq)
    dist_q = np.hypot(XX - xq, YY - yq)
    rua_q = float(np.median(arr[(dist_q <= 3) & valido]))
    proj = {"lat_lon": _latlon(xq, yq), "deslocamento_m": round(best[0], 1), "cota_m": round(float(arr[qi, qj]), 3),
            "cota_rua_m": round(rua_q, 3), "profundidade_depressao_m": round(float(dd[qi, qj]), 3),
            "dentro_da_depressao_id": int(lab[qi, qj]) if lab[qi, qj] else None}
    for rs in (5, 10, 20):
        bi = bacia_info(arr, par, ordem, acc, T.maximo_no_raio(acc, valido, dist_q, rs, qi, qj), dist_q, apx, rua_q, W)
        proj[f"snap{rs}"] = bi[0]
        if rs == 10:
            bacias["proj10"] = bi[1]; ex["proj10"] = bi[2]
    rc = bacia_info(arr, par, ordem, acc, AR.max_acc(acc, (dist_q <= 20) & (de_av <= 6) & valido, dist_q), dist_q, apx, rua_q, W)
    if rc:
        proj["regra_c"] = rc[0]
    proj["regra_a_razao_20_5"] = round(proj["snap20"]["area_m2"] / proj["snap5"]["area_m2"], 1) if proj["snap5"]["area_m2"] else None
    rel["mascarenhas"]["proposta_ponto_na_avenida"] = proj
    d1 = max(deps, key=lambda d: d["volume_m3"]) if deps else None
    rel["mascarenhas"]["maior_depressao_200m"] = d1
    AR.CORES.setdefault("prop", (0, 200, 220)); AR.CORES.setdefault("proj10", (255, 0, 200))
    AR.png("MASCARENHAS_IMBIRIBEIRA", arr, gtw, p, bacias, ex, av, [])


def _latlon(x, y):
    t = osr.CoordinateTransformation(T.srs31985(), T.srs4326())
    lon, lat, _ = t.TransformPoint(float(x), float(y))
    return [round(lat, 6), round(lon, 6)]


def jaboatao(rel):
    t = osr.CoordinateTransformation(T.srs4326(), T.srs31985())
    av = linhas_osm(LUGAR, lambda tg: tg.get("highway") and "Lucena" in (tg.get("name") or ""))
    rio = linhas_osm(LUGAR, lambda tg: tg.get("waterway") and "Duas Unas" in (tg.get("name") or ""))
    # cruzamento: par (vértice da avenida, vértice do rio) mais próximo
    best = min(((math.hypot(xa - xr, ya - yr), xa, ya) for la in av for xa, ya in la for lr in rio for xr, yr in lr))
    cx, cy = best[1], best[2]
    p = {"slug": "JABOATAO_CANDIDATO", "x": cx, "y": cy}
    arr, gtw, X, Y, XX, YY, apx = janela(p)
    W = arr.shape[1]; valido = arr != T.ND
    if not valido.any():
        rel["jaboatao"] = {"status": "sem MDT no cruzamento"}; return
    fill, par, ordem, acc = T.hidrologia(arr, gtw, "JABOATAO_CANDIDATO")
    dd = np.where(valido, fill - arr, 0.0)
    de_av = AR.dist_ao_eixo(XX, YY, av); de_rio = AR.dist_ao_eixo(XX, YY, rio)
    dist_c = np.hypot(XX - cx, YY - cy)
    rel["jaboatao"] = {"cruzamento_avenida_rio_lat_lon": _latlon(cx, cy)}
    regras = {"regra_200m_do_cruzamento": (de_av <= 6) & (dist_c <= 200) & (de_rio >= 20) & valido,
              # escrita depois de ver que a regra de 200 m caiu a 215 m do eixo do rio, longe da "margem" da fonte
              "regra_margem_50m_do_rio": (de_av <= 6) & (de_rio <= 50) & (de_rio >= 20) & valido}
    for nome, cand in regras.items():
        rel["jaboatao"][nome] = _candidato(nome, cand, arr, dd, valido, X, Y, XX, YY, de_av, de_rio, dist_c, par, ordem, acc, apx, gtw, av, rio, W, t)


def _candidato(nome, cand, arr, dd, valido, X, Y, XX, YY, de_av, de_rio, dist_c, par, ordem, acc, apx, gtw, av, rio, W, t):
    if not cand.any():
        return {"status": "nenhum pixel da avenida na regra"}
    if (dd[cand] > DD_MIN).any():
        crit = "maior profundidade de depressão na avenida"
        ii, jj = np.unravel_index(int(np.argmax(np.where(cand, dd, -1))), dd.shape)
    else:
        crit = "menor cota da avenida (nenhuma depressão > 2 cm)"
        ii, jj = np.unravel_index(int(np.argmin(np.where(cand, arr, np.inf))), arr.shape)
    q = {"slug": "JABOATAO_CENTRO", "x": float(X[jj]), "y": float(Y[ii])}
    dist = np.hypot(XX - q["x"], YY - q["y"])
    rua = float(np.median(arr[(dist <= 3) & valido]))
    out = {"criterio": crit, "lat_lon": _latlon(X[jj], Y[ii]), "cota_m": round(float(arr[ii, jj]), 3), "cota_rua_m": round(rua, 3),
           "profundidade_depressao_m": round(float(dd[ii, jj]), 3), "dist_cruzamento_m": round(float(dist_c[ii, jj]), 1),
           "dist_eixo_rio_m": round(float(de_rio[ii, jj]), 1), "posicao_atual": [-8.106520, -35.013210],
           "dist_posicao_atual_m": None, "cota_posicao_atual_m": 64.8}
    xa, ya, _ = t.TransformPoint(-35.013210, -8.106520)
    out["dist_posicao_atual_m"] = round(math.hypot(q["x"] - xa, q["y"] - ya), 1)
    bacias, ex = {}, {}
    for rs in (5, 10, 20):
        bi = bacia_info(arr, par, ordem, acc, T.maximo_no_raio(acc, valido, dist, rs, ii, jj), dist, apx, rua, W)
        out[f"snap{rs}"] = bi[0]; bacias[f"snap{rs}"] = bi[1]; ex[f"snap{rs}"] = bi[2]
    rc = bacia_info(arr, par, ordem, acc, AR.max_acc(acc, (dist <= 20) & (de_av <= 6) & valido, dist), dist, apx, rua, W)
    if rc:
        out["regra_c"] = rc[0]; bacias["regra_c"] = rc[1]; ex["regra_c"] = rc[2]
    a5, a20 = out["snap5"]["area_m2"], out["snap20"]["area_m2"]
    out["regra_a_razao_20_5"] = round(a20 / a5, 1) if a5 else None
    out["trava_a_passa"] = out["regra_a_razao_20_5"] is not None and out["regra_a_razao_20_5"] <= 10
    AR.png(f"JABOATAO_{nome}", arr, gtw, {"x": q["x"], "y": q["y"]}, bacias, ex, av + rio, [])
    return out


def projecao_via(rel, slug, nome_via):
    """Mesma regra da Mascarenhas para os outros pontos de avenida: projeção perpendicular no eixo da via citada (OSM)."""
    p = next(q for q in T.le_pontos() if q["slug"] == slug)
    arr, gtw, X, Y, XX, YY, apx = janela(p)
    W = arr.shape[1]; valido = arr != T.ND
    fill, par, ordem, acc = T.hidrologia(arr, gtw, slug)
    dd = np.where(valido, fill - arr, 0.0)
    av = linhas_osm(f"{AR.OSM}/{slug}.json", lambda t: nome_via in (t.get("name") or "") and t.get("highway"))
    de_av = AR.dist_ao_eixo(XX, YY, av)
    best = None
    for ln in av:
        for (xa, ya), (xb, yb) in zip(ln[:-1], ln[1:]):
            dx, dy = xb - xa, yb - ya; L2 = dx * dx + dy * dy
            if L2 == 0:
                continue
            u = max(0.0, min(1.0, ((p["x"] - xa) * dx + (p["y"] - ya) * dy) / L2))
            x, y = xa + u * dx, ya + u * dy
            d = math.hypot(x - p["x"], y - p["y"])
            if best is None or d < best[0]:
                best = (d, x, y)
    _, xq, yq = best
    qi, qj = idx(X, Y, xq, yq)
    dist_q = np.hypot(XX - xq, YY - yq)
    rua_q = float(np.median(arr[(dist_q <= 3) & valido]))
    out = {"via": nome_via, "lat_lon": _latlon(xq, yq), "deslocamento_m": round(best[0], 1), "cota_m": round(float(arr[qi, qj]), 3),
           "cota_rua_m": round(rua_q, 3), "profundidade_depressao_m": round(float(dd[qi, qj]), 3)}
    bacias, ex = {}, {}
    for rs in (5, 10, 20):
        bi = bacia_info(arr, par, ordem, acc, T.maximo_no_raio(acc, valido, dist_q, rs, qi, qj), dist_q, apx, rua_q, W)
        out[f"snap{rs}"] = bi[0]; bacias[f"snap{rs}"] = bi[1]; ex[f"snap{rs}"] = bi[2]
    rc = bacia_info(arr, par, ordem, acc, AR.max_acc(acc, (dist_q <= 20) & (de_av <= 6) & valido, dist_q), dist_q, apx, rua_q, W)
    if rc:
        out["regra_c"] = rc[0]; bacias["regra_c"] = rc[1]; ex["regra_c"] = rc[2]
    out["regra_a_razao_20_5"] = round(out["snap20"]["area_m2"] / out["snap5"]["area_m2"], 1) if out["snap5"]["area_m2"] else None
    rel.setdefault("projecao_na_via", {})[slug] = out
    AR.png(f"{slug}_projecao", arr, gtw, {"x": xq, "y": yq}, bacias, ex, av, AR.bls(slug))


def main():
    os.makedirs(OUT, exist_ok=True)
    AR.OUT = OUT
    rel = {"parametros": {"dd_min_m": DD_MIN, "janela_m": T.PARAMS["janela_hidrologia_m"], "motor": os.environ.get("MOTOR", "whitebox")}}
    mascarenhas(rel)
    jaboatao(rel)
    for slug, via in (("AV_RECIFE_IBURA", "Avenida Recife"), ("AGAMENON_DERBY", "Agamenon")):
        projecao_via(rel, slug, via)
    json.dump(rel, open(f"{OUT}/fase3.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps(rel, ensure_ascii=False, indent=1)[:6000])


if __name__ == "__main__":
    main()
