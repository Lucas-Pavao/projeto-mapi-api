"""Diagnóstico e regras candidatas da área de contribuição (prompt 2, Parte B). PROPOSTA: nada aqui muda o alerta.

Uso (dentro do container GDAL do run.sh, com /terreno = saída que já tem mosaico.vrt, cobertura.csv e whitebox/):
    tools/terreno/run.sh area_regras        (precisa de ESIG=<pasta com os GeoJSON do prompt 1>)

Para cada um dos 5 pontos, na mesma janela de 2 km e com o mesmo motor (WhiteboxTools) de `atributos`:
  - exutório e bacia D8 com snap de 0, 5, 10 e 20 m (regra atual = 10 m);
  - regra (b): exutório = pixel de maior acumulação a <= 3 m da boca de lobo (ESIG, TIPO = BL) mais próxima do ponto
    baixo; ponto baixo = pixel válido de menor cota a <= 20 m do ponto; BL procurada a <= 50 m do ponto baixo;
  - regra (c): exutório = pixel de maior acumulação a <= 20 m do ponto, só entre pixels a <= 6 m do eixo de uma via
    do OSM (highway de veículo); a água da rua corre pela via até o ponto;
  - regra (a): razão A(20 m) / A(5 m);
  - regra (d): faixa [A(5 m), A(20 m)].
Ilustração física (não é rodada sobre série nenhuma): lâmina na rua de um único passo de 10 min com 20,9 mm (IDF de
Silva e Araújo, TR 5, Te 10 min) e galeria totalmente obstruída (f = 0), V = C·P·A, com a curva cota-volume da
própria bacia. Mostra quanto a área sozinha muda a lâmina.

Saídas em /terreno/area_regras/: area_regras.csv, <PONTO>.png (vista de 400 m x 400 m).
Cores do PNG: relevo sombreado em cinza; vias do OSM em branco; bacias (contorno): snap 0 m vermelho, 5 m laranja,
10 m azul, 20 m verde, regra (b) magenta, regra (c) ciano; exutórios em quadrado da mesma cor; ponto crítico em
amarelo (cruz); bocas de lobo do ESIG em preto com borda branca.
"""
import csv
import json
import math
import os
import sys

import numpy as np
from osgeo import gdal, osr

sys.path.insert(0, "/tools/terreno")
import terreno as T  # noqa: E402

gdal.UseExceptions()
OUT = "/terreno/area_regras"
ESIG = "/esig"
OSM = "/tools/terreno/vias_osm"
C = 0.86
P_TR5_10MIN_MM = 20.9
VISTA_M = 400
CORES = {"snap0": (230, 30, 30), "snap5": (255, 150, 0), "snap10": (40, 90, 255), "snap20": (0, 170, 60),
         "regra_b": (220, 0, 220), "regra_c": (0, 210, 210)}
VIAS_VEICULO = {"motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service",
                "living_street", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link", "road"}


def bls(slug):
    f = f"{ESIG}/{slug}__MI_c0.geojson"
    if not os.path.exists(f):
        return []
    d = json.load(open(f, encoding="utf-8"))
    return [(ft["geometry"]["coordinates"][0], ft["geometry"]["coordinates"][1], ft["properties"]["OBJECTID"])
            for ft in d["features"] if ft["properties"].get("TIPO") == "BL"]


def vias(slug):
    f = f"{OSM}/{slug}.json"
    if not os.path.exists(f):
        return []
    t = osr.CoordinateTransformation(T.srs4326(), T.srs31985())
    out = []
    for e in json.load(open(f, encoding="utf-8"))["elements"]:
        if e.get("type") != "way" or e.get("tags", {}).get("highway") not in VIAS_VEICULO:
            continue
        out.append([t.TransformPoint(g["lon"], g["lat"])[:2] for g in e["geometry"]])
    return out


def dist_ao_eixo(XX, YY, linhas):
    d = np.full(XX.shape, np.inf)
    for ln in linhas:
        for (x0, y0), (x1, y1) in zip(ln[:-1], ln[1:]):
            dx, dy = x1 - x0, y1 - y0
            L2 = dx * dx + dy * dy
            if L2 == 0:
                continue
            u = np.clip(((XX - x0) * dx + (YY - y0) * dy) / L2, 0, 1)
            d = np.minimum(d, np.hypot(XX - (x0 + u * dx), YY - (y0 + u * dy)))
    return d


def max_acc(acc, mascara, dist):
    a = np.where(mascara, acc, -1)
    mx = a.max()
    if mx < 0:
        return None
    cand = np.argwhere(a == mx)
    k = min(range(len(cand)), key=lambda q: (dist[cand[q][0], cand[q][1]], cand[q][0], cand[q][1]))
    return int(mx), int(cand[k][0]), int(cand[k][1])


def lamina_ilustrativa(arr, bacia, dist, area_m2, rua_cota, W):
    """Lâmina (mm) acima da cota da rua para V = C·P·A num passo, galeria obstruída; curva da própria bacia."""
    arrb = np.where(bacia & (dist <= T.PARAMS["raio_fundo_curva_m"]), arr, np.inf)
    if not np.isfinite(arrb).any():
        return None, None, None
    fi = int(np.argmin(arrb))
    z0, curva, _ = T.curva_cota_volume(arr, bacia, fi, W, T.PARAMS["curva_passo_m"], T.PARAMS["curva_altura_max_m"], T.ND)
    V = C * P_TR5_10MIN_MM * area_m2 / 1000.0
    zs = np.array([c[0] - z0 for c in curva]); vs = np.array([c[2] for c in curva])
    z = float(zs[-1]) if V > vs[-1] else float(np.interp(V, vs, zs))
    h = max(0.0, z - (rua_cota - z0)) * 1000.0
    return round(V, 2), round(h, 0), V > vs[-1]


def contorno(m):
    e = m.copy()
    e[1:-1, 1:-1] = m[1:-1, 1:-1] & m[:-2, 1:-1] & m[2:, 1:-1] & m[1:-1, :-2] & m[1:-1, 2:]
    return m & ~e


def png(slug, arr, gtw, p, bacias, exut, linhas, bl_pts):
    n = arr.shape[0]
    cx = int((p["x"] - gtw[0]) / gtw[1]); cy = int((p["y"] - gtw[3]) / gtw[5]); h = VISTA_M // 2
    y0, y1, x0, x1 = cy - h, cy + h, cx - h, cx + h
    a = arr[y0:y1, x0:x1].copy(); v = a != T.ND
    a[~v] = np.nan
    gy, gx = np.gradient(np.nan_to_num(a, nan=float(np.nanmean(a))))
    sl = np.arctan(np.hypot(gx, gy)); asp = np.arctan2(-gx, gy)
    az, alt = math.radians(315), math.radians(45)
    hs = np.clip(np.sin(alt) * np.cos(sl) + np.cos(alt) * np.sin(sl) * np.cos(az - asp), 0, 1)
    rel = (a - np.nanmin(a)) / max(np.nanmax(a) - np.nanmin(a), 1e-6)
    base = np.nan_to_num(0.45 * hs + 0.35 * rel + 0.15, nan=0.0)
    img = np.stack([base * 255] * 3, axis=-1).astype(np.uint8)
    img[~v] = (60, 90, 140)
    XX = gtw[0] + (np.arange(x0, x1) + 0.5) * gtw[1]; YY = gtw[3] + (np.arange(y0, y1) + 0.5) * gtw[5]
    XX, YY = np.meshgrid(XX, YY)
    if linhas:
        img[dist_ao_eixo(XX, YY, linhas) <= 0.8] = (255, 255, 255)
    for k in ["snap20", "snap10", "regra_c", "regra_b", "snap5", "snap0"]:
        if k in bacias:
            b = contorno(bacias[k][y0:y1, x0:x1])
            img[b] = CORES[k]
            if k in ("snap0", "snap5"):  # bacias minúsculas: pinta por dentro também
                img[bacias[k][y0:y1, x0:x1]] = CORES[k]
    for k, (ei, ej) in exut.items():
        r, c = ei - y0, ej - x0
        img[max(r - 3, 0):r + 4, max(c - 3, 0):c + 4] = CORES[k]
    for (bx, by, _) in bl_pts:
        r = int((by - gtw[3]) / gtw[5]) - y0; c = int((bx - gtw[0]) / gtw[1]) - x0
        if 0 <= r < 2 * h and 0 <= c < 2 * h:
            img[max(r - 3, 0):r + 4, max(c - 3, 0):c + 4] = (255, 255, 255)
            img[max(r - 2, 0):r + 3, max(c - 2, 0):c + 3] = (0, 0, 0)
    img[h - 6:h + 7, h - 1:h + 2] = (255, 230, 0); img[h - 1:h + 2, h - 6:h + 7] = (255, 230, 0)
    img = img.repeat(2, axis=0).repeat(2, axis=1)
    mem = gdal.GetDriverByName("MEM").Create("", img.shape[1], img.shape[0], 3, gdal.GDT_Byte)
    for i in range(3):
        mem.GetRasterBand(i + 1).WriteArray(img[:, :, i])
    gdal.Translate(f"{OUT}/{slug}.png", mem, format="PNG")
    for f in (f"{OUT}/{slug}.png.aux.xml",):
        if os.path.exists(f):
            os.remove(f)


def main():
    os.makedirs(OUT, exist_ok=True)
    ds, gt = T.vrt_info(); b = ds.GetRasterBand(1)
    Wm, Hm = ds.RasterXSize, ds.RasterYSize
    cob = {r["slug"]: r for r in csv.DictReader(open("/terreno/cobertura.csv", encoding="utf-8"))}
    apx = gt[1] * abs(gt[5])
    rows = []
    for p in T.le_pontos():
        c = cob[p["slug"]]
        if c["pixel_valido"] != "True":
            rows.append(dict(ponto=p["slug"], observacao="sem terreno")); continue
        n, xo, yo, arr, gtw = T.le_janela(b, gt, p, Wm, Hm)
        W = n + 2
        X = gtw[0] + (np.arange(W) + 0.5) * gtw[1]; Y = gtw[3] + (np.arange(W) + 0.5) * gtw[5]
        XX, YY = np.meshgrid(X, Y)
        dist = np.hypot(XX - p["x"], YY - p["y"])
        valido = arr != T.ND
        print(p["slug"], "hidrologia...", flush=True)
        fill, par, ordem, acc = T.hidrologia(arr, gtw, p["slug"])
        pl = int(c["lin"]) - yo + 1; pc = int(c["col"]) - xo + 1
        rua = float(np.median(arr[(dist <= 3) & valido]))
        r = dict(ponto=p["slug"], cota_rua_m=round(rua, 3))
        bacias, exut = {}, {}

        def registra(chave, res, extra=None):
            if res is None:
                r[f"{chave}_area_m2"] = ""; return
            a_, ei, ej = res
            bac = T.bacia_do_exutorio(par, ordem, arr.size, ei * W + ej).reshape(arr.shape)
            bacias[chave] = bac; exut[chave] = (ei, ej)
            area = int(round(bac.sum() * apx))
            r[f"{chave}_area_m2"] = area
            r[f"{chave}_exutorio_dist_m"] = round(float(dist[ei, ej]), 1)
            r[f"{chave}_exutorio_cota_m"] = round(float(arr[ei, ej]), 3)
            V, h, transborda = lamina_ilustrativa(arr, bac, dist, area, rua, W)
            r[f"{chave}_ilustr_V_m3"] = V; r[f"{chave}_ilustr_lamina_mm"] = h; r[f"{chave}_ilustr_passa_topo_curva"] = transborda

        for rs in T.PARAMS["raios_snap_sensibilidade_m"]:
            registra(f"snap{rs}", T.maximo_no_raio(acc, valido, dist, rs, pl, pc))
        # regra (b): boca de lobo do ESIG mais próxima do ponto baixo
        pts_bl = bls(p["slug"])
        m20 = (dist <= 20) & valido
        bi, bj = np.unravel_index(int(np.argmin(np.where(m20, arr, np.inf))), arr.shape)
        r["ponto_baixo_dist_m"] = round(float(dist[bi, bj]), 1); r["ponto_baixo_cota_m"] = round(float(arr[bi, bj]), 3)
        xb, yb = X[bj], Y[bi]
        cand = sorted((math.hypot(x - xb, y - yb), oid, x, y) for x, y, oid in pts_bl)
        if not pts_bl:
            r["regra_b_area_m2"] = ""; r["regra_b_motivo"] = "sem cadastro ESIG (fora do Recife)" if p["slug"].startswith("JABOATAO") else "nenhuma BL no ESIG a 300 m"
        elif cand[0][0] > 50:
            r["regra_b_area_m2"] = ""; r["regra_b_motivo"] = f"BL mais próxima a {cand[0][0]:.0f} m do ponto baixo (> 50 m)"
        else:
            d_bl, oid, x, y = cand[0]
            dbl = np.hypot(XX - x, YY - y)
            r["regra_b_bl_objectid"] = oid; r["regra_b_bl_dist_ponto_baixo_m"] = round(d_bl, 1)
            registra("regra_b", max_acc(acc, (dbl <= 3) & valido, dbl))
        # regra (c): via do OSM
        linhas = vias(p["slug"])
        if linhas:
            de = dist_ao_eixo(XX, YY, linhas)
            res = max_acc(acc, (dist <= 20) & (de <= 6) & valido, dist)
            registra("regra_c", res)
            if res is None:
                r["regra_c_motivo"] = "nenhum pixel de via a 20 m"
        else:
            r["regra_c_area_m2"] = ""; r["regra_c_motivo"] = "sem vias do OSM"
        a5, a20 = r.get("snap5_area_m2") or 0, r.get("snap20_area_m2") or 0
        r["regra_a_razao_20_5"] = round(a20 / a5, 1) if a5 else ""
        rows.append(r)
        png(p["slug"], arr, gtw, p, bacias, exut, linhas, pts_bl)
        print({k: v for k, v in r.items() if k.endswith("area_m2") or k.startswith("regra_a")}, flush=True)
    cols = []
    for r in rows:
        for k in r:
            if k not in cols:
                cols.append(k)
    T.write_csv(f"{OUT}/area_regras.csv", rows, cols)


if __name__ == "__main__":
    main()
