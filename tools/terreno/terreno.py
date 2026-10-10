#!/usr/bin/env python3
"""MAPi — terreno PE3D (F3-GEO-01/04/05). Roda dentro do container GDAL (ver run.sh).

Subcomandos (uso: python3 -I terreno.py <subcomando>):
  inventario  -> inventario.csv, articulacao_folhas.geojson (lê só os zips por /vsizip)
  cobertura   -> cobertura.csv (ponto dentro do mosaico e em pixel válido; janelas de recorte)
  atributos   -> ponto_terreno.csv, ponto_terreno.meta.json, curva_cota_volume.csv
  comparar    -> comparacao_altitude.csv, comparacao_altitude.json
  estimativas -> estimativas_cog.json (tamanho de COG estimado a partir de medições)

Somente GDAL (osgeo) + numpy + stdlib. Sem aleatoriedade: resultado determinístico.
Entradas fixas por montagem de volume: /dados (zips, ro), /terreno (saídas), /tools (este dir, ro).
"""
import csv, glob, hashlib, heapq, json, math, os, sys, zipfile
import numpy as np
from osgeo import gdal, osr

gdal.UseExceptions()
DADOS = "/dados"
OUT = "/terreno"
TOOLS = "/tools/terreno"
VRT = f"{OUT}/mosaico.vrt"
ND = -9999.0
EPSG = 31985
# parâmetros (registrados no meta.json)
PARAMS = dict(
    raio_cota_m=[3, 5],
    janelas_declividade_m=[50, 100],
    janela_hidrologia_m=2000,      # lado total, centrado no ponto
    raio_snap_acumulacao_m=10,
    recorte_cog_m=1000,
    curva_passo_m=0.05,
    curva_altura_max_m=2.0,
    raio_fundo_curva_m=50,         # fundo = menor cota da bacia a <= 50 m do ponto
    raios_snap_sensibilidade_m=[0, 5, 10, 20],
    vizinhanca="D8 (8 vizinhos)",
    motor_hidrologia="WhiteboxTools v2.4.0: FillDepressions (exato) para a profundidade; FillDepressions --fix_flats -> D8Pointer -> D8FlowAccumulation (cells) para a área. MOTOR=proprio usa Priority-Flood + D8 próprios (sem download)",
    regra_snap="pixel de maior acumulação a <= 10 m do ponto (centros de pixel, só válidos); empate: menor distância, depois menor (linha, coluna)",
)


def srs31985():
    s = osr.SpatialReference(); s.ImportFromEPSG(EPSG)
    s.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER); return s


def srs4326():
    s = osr.SpatialReference(); s.ImportFromEPSG(4326)
    s.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER); return s


def le_pontos():
    with open(f"{TOOLS}/pontos_criticos.csv", encoding="utf-8") as f:
        pts = list(csv.DictReader(f))
    t = osr.CoordinateTransformation(srs4326(), srs31985())
    for p in pts:
        p["latitude"] = float(p["latitude"]); p["longitude"] = float(p["longitude"])
        x, y, _ = t.TransformPoint(p["longitude"], p["latitude"])
        p["x"], p["y"] = x, y
    return pts


def zips():
    return sorted(glob.glob(f"{DADOS}/MDT-*.zip"))


def inner_tif(z):
    with zipfile.ZipFile(z) as zf:
        n = [i for i in zf.namelist() if i.lower().endswith(".tif")]
    assert len(n) == 1, z
    return n[0]


def write_csv(path, rows, cols):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n"); w.writeheader()
        for r in rows: w.writerow({c: r.get(c, "") for c in cols})


# ---------------------------------------------------------------- inventário
def cmd_inventario():
    rows, feats = [], []
    t = osr.CoordinateTransformation(srs31985(), srs4326())
    total_valid = 0
    for z in zips():
        tif = inner_tif(z)
        base = os.path.basename(z)[:-4]
        with zipfile.ZipFile(z) as zf:
            tfw_name = [i for i in zf.namelist() if i.lower().endswith(".tfw")][0]
            tfw = [float(v) for v in zf.read(tfw_name).decode().split()]
        ds = gdal.Open(f"/vsizip/{z}/{tif}")
        gt = ds.GetGeoTransform(); w, h = ds.RasterXSize, ds.RasterYSize
        b = ds.GetRasterBand(1)
        crs_no_arquivo = bool(ds.GetProjection())
        _sr = osr.SpatialReference(wkt=ds.GetProjection()) if crs_no_arquivo else None
        crs_igual = bool(_sr.IsSame(srs31985())) if _sr else False
        files = [os.path.basename(f) for f in ds.GetFileList()]
        # tfw: centro do pixel superior esquerdo -> canto = centro - 0.5 * tamanho
        tfw_x0 = tfw[4] - tfw[0] / 2; tfw_y0 = tfw[5] - tfw[3] / 2
        tfw_confere = abs(tfw_x0 - gt[0]) < 1e-6 and abs(tfw_y0 - gt[3]) < 1e-6 and abs(tfw[0] - gt[1]) < 1e-9
        # pixels válidos (lê por faixas de linhas)
        nvalid = 0; vmin = np.inf; vmax = -np.inf
        for y0 in range(0, h, 512):
            a = b.ReadAsArray(0, y0, w, min(512, h - y0))
            m = a != ND
            nvalid += int(m.sum())
            if m.any():
                vmin = min(vmin, float(a[m].min())); vmax = max(vmax, float(a[m].max()))
        total_valid += nvalid
        x0, y0_, x1, y1 = gt[0], gt[3], gt[0] + w * gt[1], gt[3] + h * gt[5]
        rows.append(dict(
            folha=base, arquivo_tif=tif, zip_bytes=os.path.getsize(z), largura_px=w, altura_px=h,
            pixel_m=gt[1], dtype=gdal.GetDataTypeName(b.DataType), nodata=b.GetNoDataValue(),
            crs_no_tif=crs_no_arquivo, crs_igual_epsg31985=crs_igual, arquivos_lidos=";".join(files), tfw_confere_com_geotransform=tfw_confere,
            xmin=x0, xmax=x1, ymin=y1, ymax=y0_, pixels_validos=nvalid, pct_validos=round(100 * nvalid / (w * h), 2),
            z_min=round(vmin, 2), z_max=round(vmax, 2)))
        ring = []
        for (x, y) in [(x0, y0_), (x1, y0_), (x1, y1), (x0, y1), (x0, y0_)]:
            lon, lat, _ = t.TransformPoint(x, y); ring.append([round(lon, 6), round(lat, 6)])
        feats.append(dict(type="Feature", properties=dict(folha=base, largura_px=w, altura_px=h,
                          pixels_validos=nvalid), geometry=dict(type="Polygon", coordinates=[ring])))
        print(base, w, h, nvalid, flush=True)
    cols = list(rows[0].keys())
    write_csv(f"{OUT}/inventario.csv", rows, cols)
    with open(f"{OUT}/articulacao_folhas.geojson", "w") as f:
        json.dump(dict(type="FeatureCollection", name="articulacao_folhas_PE3D_MDT_2016",
                       crs=dict(type="name", properties=dict(name="urn:ogc:def:crs:OGC:1.3:CRS84")),
                       features=feats), f)
    print("folhas", len(rows), "pixels_validos_total", total_valid)


# ---------------------------------------------------------------- cobertura
def vrt_info():
    ds = gdal.Open(VRT); return ds, ds.GetGeoTransform()


def janela_alinhada(gt, x, y, lado, W, H):
    """Janela de `lado` m centrada em (x,y), alinhada à grade do mosaico. Retorna (xoff, yoff, n)."""
    n = int(round(lado / gt[1]))
    col = int(math.floor((x - gt[0]) / gt[1])); lin = int(math.floor((y - gt[3]) / gt[5]))
    return col - n // 2, lin - n // 2, n


def cmd_cobertura():
    ds, gt = vrt_info(); b = ds.GetRasterBand(1)
    W, H = ds.RasterXSize, ds.RasterYSize
    rows = []
    for p in le_pontos():
        col = int(math.floor((p["x"] - gt[0]) / gt[1])); lin = int(math.floor((p["y"] - gt[3]) / gt[5]))
        dentro = 0 <= col < W and 0 <= lin < H
        val = None; valido = False
        if dentro:
            val = float(b.ReadAsArray(col, lin, 1, 1)[0, 0]); valido = val != ND
        xo, yo, n = janela_alinhada(gt, p["x"], p["y"], PARAMS["recorte_cog_m"], W, H)
        # fração válida da janela de 1 km (lida só se o ponto estiver dentro)
        fv = ""
        if dentro:
            cx0, cy0 = max(xo, 0), max(yo, 0); cx1, cy1 = min(xo + n, W), min(yo + n, H)
            a = b.ReadAsArray(cx0, cy0, cx1 - cx0, cy1 - cy0)
            fv = round(float((a != ND).sum()) / (n * n), 4)
        # folha de origem: aquela cujo retângulo contém o ponto
        folha = ""
        with open(f"{OUT}/inventario.csv", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                if float(r["xmin"]) <= p["x"] < float(r["xmax"]) and float(r["ymin"]) < p["y"] <= float(r["ymax"]):
                    folha = (folha + ";" if folha else "") + r["folha"]
        dmin = ""
        if dentro and not valido:   # distância ao pixel válido mais próximo (janela de ±3 km)
            R = 3000
            x0_, y0_ = max(col - R, 0), max(lin - R, 0); x1_, y1_ = min(col + R, W), min(lin + R, H)
            a = b.ReadAsArray(x0_, y0_, x1_ - x0_, y1_ - y0_); yy, xx = np.nonzero(a != ND)
            dmin = round(float(np.hypot(xx + x0_ - col, yy + y0_ - lin).min()), 0) if len(xx) else ">3000"
        rows.append(dict(slug=p["slug"], dist_pixel_valido_mais_proximo_m=dmin, x_utm=round(p["x"], 3), y_utm=round(p["y"], 3), dentro_do_mosaico=dentro,
                         col=col, lin=lin, cota_pixel_m=val, pixel_valido=valido, folha_origem=folha,
                         janela_xoff=xo, janela_yoff=yo, janela_n=n, fracao_valida_janela_1km=fv,
                         projwin_ulx=gt[0] + xo * gt[1], projwin_uly=gt[3] + yo * gt[5],
                         projwin_lrx=gt[0] + (xo + n) * gt[1], projwin_lry=gt[3] + (yo + n) * gt[5]))
    write_csv(f"{OUT}/cobertura.csv", rows, list(rows[0].keys()))
    for r in rows: print(r["slug"], r["dentro_do_mosaico"], r["pixel_valido"], r["cota_pixel_m"], r["folha_origem"], r["fracao_valida_janela_1km"])


# ---------------------------------------------------------------- hidrologia
def priority_flood(dem, nd):
    """Priority-Flood com árvore de descoberta. dem: 2D float64 já com anel de nodata ao redor.
    Retorna (fill, parent, ordem): parent[i] = índice do vizinho para onde i escoa (-1 = saída),
    ordem = células na ordem de extração (pais antes dos filhos)."""
    H, W = dem.shape
    flat = dem.ravel()
    N = flat.size
    valid = flat != nd
    fill = flat.copy()
    parent = np.full(N, -2, dtype=np.int64)  # -2 = não visitado/nodata
    offs = [-W - 1, -W, -W + 1, -1, 1, W - 1, W, W + 1]
    fl = flat.tolist(); vl = valid.tolist()
    filll = fl[:]
    par = [-2] * N
    visited = [False] * N
    heap = []
    # sementes: válidas com algum vizinho nodata (inclui borda da janela, pelo anel)
    idx_valid = np.flatnonzero(valid)
    for i in idx_valid.tolist():
        for o in offs:
            if not vl[i + o]:
                par[i] = -1; visited[i] = True; heap.append((fl[i], i)); break
    heapq.heapify(heap)
    ordem = []
    pop, push = heapq.heappop, heapq.heappush
    while heap:
        e, i = pop(heap)
        ordem.append(i)
        for o in offs:
            j = i + o
            if vl[j] and not visited[j]:
                visited[j] = True
                par[j] = i
                v = fl[j]
                if v < e: v = e
                filll[j] = v
                push(heap, (v, j))
    return np.array(filll).reshape(H, W), par, ordem


DIRS = [(0, 1, 1.0), (0, -1, 1.0), (1, 0, 1.0), (-1, 0, 1.0),
        (1, 1, math.sqrt(2)), (1, -1, math.sqrt(2)), (-1, 1, math.sqrt(2)), (-1, -1, math.sqrt(2))]


def direcao_d8(fill, par_arvore, nd):
    """D8 de máxima declividade sobre o DEM preenchido (declividade = queda / distância, diagonal = raiz de 2).
    Empate de declividade: vence a primeira direção de DIRS (L, O, S, N, depois diagonais).
    Células sem vizinho estritamente mais baixo (planos) seguem a árvore do Priority-Flood; células vizinhas de nodata
    (inclui a borda da janela) são saídas (-1). Retorna par (int64; -1 saída, -2 nodata); sem ciclos: cada passo
    ou reduz a cota do fill ou, num plano, sobe na árvore de descoberta."""
    H, W = fill.shape
    valid = fill != nd
    best = np.full(fill.shape, 0.0); alvo = np.full(fill.shape, -1, dtype=np.int64)
    idx = np.arange(fill.size, dtype=np.int64).reshape(fill.shape)
    todos_validos = valid.copy()
    for dy, dx, dist in DIRS:
        nb = np.roll(fill, (-dy, -dx), (0, 1)); vn = np.roll(valid, (-dy, -dx), (0, 1))
        todos_validos &= vn
        sl = np.where(valid & vn, (fill - nb) / dist, 0.0)
        melhor = sl > best
        best = np.where(melhor, sl, best)
        alvo = np.where(melhor, idx + dy * W + dx, alvo)
    pa = np.asarray(par_arvore, dtype=np.int64).reshape(fill.shape)
    par = np.where(alvo >= 0, alvo, pa)
    par = np.where(valid & ~todos_validos, -1, par)       # vizinha de nodata/borda = saída
    return np.where(valid, par, -2).ravel()


def ordem_topologica(par):
    """Índices em ordem pais-antes-dos-filhos (largura a partir das saídas). par: -1 saída, -2 nodata."""
    par = np.asarray(par)
    filhos = np.flatnonzero(par >= 0)
    srt = filhos[np.argsort(par[filhos], kind="stable")]
    chave = par[srt]
    ini = np.searchsorted(chave, np.arange(par.size + 1))
    srt = srt.tolist(); ini = ini.tolist()
    ordem = np.flatnonzero(par == -1).tolist()
    k = 0
    while k < len(ordem):
        i = ordem[k]; k += 1
        ordem.extend(srt[ini[i]:ini[i + 1]])
    return ordem


def hidrologia_d8(arr, nd=ND):
    """Pipeline próprio: Priority-Flood (fill) -> D8 de máxima declividade -> acumulação. Retorna (fill, par, ordem, acc 1D)."""
    fill, par_arvore, _ = priority_flood(arr, nd)
    par = direcao_d8(fill, par_arvore, nd)
    ordem = ordem_topologica(par)
    return fill, par.tolist(), ordem, acumulacao(par.tolist(), ordem, arr.size)


# ---------------------------------------------------------------- motor de referência: WhiteboxTools
WBT = f"{OUT}/whitebox/WBT/whitebox_tools"
# código do ponteiro D8 do WhiteboxTools -> (dy, dx): 1=NE 2=E 4=SE 8=S 16=SW 32=W 64=NW 128=N
WBT_DIR = {1: (-1, 1), 2: (0, 1), 4: (1, 1), 8: (1, 0), 16: (1, -1), 32: (0, -1), 64: (-1, -1), 128: (-1, 0)}


def executa_whitebox(slug, arr, gtw, breach=False):
    """Roda o WhiteboxTools na janela (arr: com anel nodata; o Whitebox recebe a janela n x n, sem o anel, e portanto a
    borda da grade é a borda da janela). Passos: FillDepressions (exato) e FillDepressions --fix_flats (só para o
    D8Pointer, que precisa de gradiente nos planos), D8Pointer, D8FlowAccumulation --out_type=cells. Com breach=True
    usa BreachDepressionsLeastCost (--dist=500 --fill) no lugar do fill. Retorna dict de arrays com o anel
    restaurado: fill (exato; None em breach), ptr, acc. Arquivos temporários em OUT/validacao são apagados."""
    import subprocess
    if not os.path.exists(WBT):
        raise SystemExit(f"WhiteboxTools não encontrado em {WBT}; rode: tools/terreno/run.sh whitebox")
    wd = f"{OUT}/validacao"; os.makedirs(wd, exist_ok=True)
    n = arr.shape[0] - 2
    gtn = (gtw[0] + gtw[1], gtw[1], 0, gtw[3] + gtw[5], 0, gtw[5])
    f_in = f"{wd}/janela_{slug}.tif"
    # DEFLATE sem predictor: o leitor de GeoTIFF do Whitebox não aceita PREDICTOR=3
    o = gdal.GetDriverByName("GTiff").Create(f_in, n, n, 1, gdal.GDT_Float32, ["COMPRESS=DEFLATE"])
    o.SetGeoTransform(gtn); o.SetProjection(srs31985().ExportToWkt())
    bb = o.GetRasterBand(1); bb.SetNoDataValue(ND); bb.WriteArray(arr[1:-1, 1:-1].astype(np.float32)); o.FlushCache(); o = None

    def wb(*args):
        subprocess.run([WBT, f"--wd={wd}", *args], check=True, stdout=subprocess.DEVNULL)

    nome = lambda t: f"wbt_{t}_{slug}.tif"
    if breach:
        wb("-r=BreachDepressionsLeastCost", f"--dem={os.path.basename(f_in)}", f"-o={nome('fillfix')}", "--dist=500", "--fill")
    else:
        wb("-r=FillDepressions", f"--dem={os.path.basename(f_in)}", f"-o={nome('fill')}")
        wb("-r=FillDepressions", f"--dem={os.path.basename(f_in)}", f"-o={nome('fillfix')}", "--fix_flats")
    wb("-r=D8Pointer", f"--dem={nome('fillfix')}", f"-o={nome('ptr')}")
    wb("-r=D8FlowAccumulation", f"-i={nome('ptr')}", f"-o={nome('acc')}", "--pntr", "--out_type=cells")
    res = {}
    for t in (["ptr", "acc"] if breach else ["fill", "ptr", "acc"]):
        d = gdal.Open(f"{wd}/{nome(t)}"); res[t] = d.GetRasterBand(1).ReadAsArray().astype(np.float64); d = None
    res.setdefault("fill", None)
    for t in ("fill", "fillfix", "ptr", "acc"):
        for f in (f"{wd}/{nome(t)}",):
            if os.path.exists(f): os.remove(f)
    os.remove(f_in)
    valido = arr != ND
    for t in ("fill", "ptr", "acc"):
        if res[t] is not None:
            res[t] = np.pad(res[t], 1, constant_values=ND if t == "fill" else 0)
    res["fill"] = None if res["fill"] is None else np.where(valido, res["fill"], ND)
    res["acc"] = np.where(valido, res["acc"], 0)
    res["ptr"] = np.where(valido, res["ptr"], 0)
    return res


def par_do_whitebox(ptr, valido):
    """Ponteiro D8 do Whitebox (array com anel) -> par (índice flat da célula a jusante; -1 = saída/pit/escoa para
    fora ou para nodata; -2 = nodata)."""
    H, W = ptr.shape
    par = np.full(ptr.shape, -1, dtype=np.int64)
    idx = np.arange(ptr.size, dtype=np.int64).reshape(ptr.shape)
    for cod, (dy, dx) in WBT_DIR.items():
        par = np.where(ptr == cod, idx + dy * W + dx, par)
    par = par.ravel(); v = valido.ravel()
    par = np.where(v & (par >= 0) & ~v[np.clip(par, 0, par.size - 1)], -1, par)   # aponta para nodata = saída
    return np.where(v, par, -2)


def hidrologia(arr, gtw, slug):
    """Hidrologia da janela conforme o motor escolhido (env MOTOR: 'whitebox' = padrão, 'proprio' = Priority-Flood + D8
    próprios, sem download). Retorna (fill 2D, par list, ordem list, acc 2D inteiro)."""
    motor = os.environ.get("MOTOR", "whitebox")
    if motor == "proprio":
        fill, par, ordem, acc = hidrologia_d8(arr)
        return fill, par, ordem, np.array(acc).reshape(arr.shape)
    w = executa_whitebox(slug, arr, gtw)
    par = par_do_whitebox(w["ptr"], arr != ND)
    return w["fill"], par.tolist(), ordem_topologica(par), np.rint(w["acc"]).astype(np.int64)


def acumulacao(par, ordem, N):
    acc = [1] * N
    for i in reversed(ordem):
        p = par[i]
        if p >= 0: acc[p] += acc[i]
    return acc


def bacia_do_exutorio(par, ordem, N, exutorio):
    em = bytearray(N)
    em[exutorio] = 1
    for i in ordem:  # pais antes dos filhos
        p = par[i]
        if p >= 0 and em[p]: em[i] = 1
    return np.frombuffer(bytes(em), dtype=np.uint8).astype(bool)


def curva_cota_volume(dem, bacia, fundo_idx, W, passo, altura, nd):
    """Volume armazenável vs. cota na bacia local: células da bacia ligadas (8-conexo) à célula do fundo
    por caminho com cota < z. Nível de ligação L(c) = menor z para o qual c é alcançável (minimax)."""
    flat = dem.ravel()
    z0 = float(flat[fundo_idx]); zmax = z0 + altura
    offs = [-W - 1, -W, -W + 1, -1, 1, W - 1, W, W + 1]
    fl = flat.tolist(); bl = bacia.ravel().tolist()
    L = {fundo_idx: z0}
    heap = [(z0, fundo_idx)]
    done = set()
    Ls, Zs = [], []
    while heap:
        l, i = heapq.heappop(heap)
        if i in done: continue
        done.add(i)
        Ls.append(l); Zs.append(fl[i])
        for o in offs:
            j = i + o
            if bl[j] and j not in done:
                nl = max(l, fl[j])
                if nl <= zmax and nl < L.get(j, 1e18):
                    L[j] = nl; heapq.heappush(heap, (nl, j))
    Ls = np.array(Ls); Zs = np.array(Zs)
    niveis = z0 + passo * np.arange(int(round(altura / passo)) + 1)
    out = []
    for z in niveis:
        m = Ls < z  # estritamente abaixo do nível
        n = int(m.sum()); vol = float((z - Zs[m]).sum()) if n else 0.0
        out.append((float(z), n, vol))
    toca_borda = None
    return z0, out, len(done)


def declividade_pct(win_arr, gt_win, srs_wkt):
    """gdaldem slope -p (Horn) sobre a janela, via /vsimem. Retorna array 2D (nodata=-9999)."""
    h, w = win_arr.shape
    drv = gdal.GetDriverByName("GTiff")
    src = "/vsimem/win.tif"; dst = "/vsimem/slope.tif"
    ds = drv.Create(src, w, h, 1, gdal.GDT_Float32)
    ds.SetGeoTransform(gt_win); ds.SetProjection(srs_wkt)
    bb = ds.GetRasterBand(1); bb.SetNoDataValue(ND); bb.WriteArray(win_arr.astype(np.float32)); ds.FlushCache(); ds = None
    gdal.DEMProcessing(dst, src, "slope", slopeFormat="percent", computeEdges=False, format="GTiff")
    o = gdal.Open(dst); a = o.GetRasterBand(1).ReadAsArray(); nd_out = o.GetRasterBand(1).GetNoDataValue(); o = None
    gdal.Unlink(src); gdal.Unlink(dst)
    return np.where(a == nd_out, np.nan, a)


def le_janela(b, gt, p, Wm, Hm):
    """Janela de hidrologia (2 km) centrada no ponto, com anel extra de 1 px de nodata (fora do mosaico = nodata).
    Retorna (n, xo, yo, arr (n+2 x n+2, float64), gtw geotransform da janela com anel)."""
    n = int(PARAMS["janela_hidrologia_m"] / gt[1])
    xo, yo, _ = janela_alinhada(gt, p["x"], p["y"], PARAMS["janela_hidrologia_m"], Wm, Hm)
    arr = np.full((n + 2, n + 2), ND, dtype=np.float64)
    cx0, cy0 = max(xo, 0), max(yo, 0); cx1, cy1 = min(xo + n, Wm), min(yo + n, Hm)
    arr[1 + cy0 - yo:1 + cy1 - yo, 1 + cx0 - xo:1 + cx1 - xo] = b.ReadAsArray(cx0, cy0, cx1 - cx0, cy1 - cy0)
    gtw = (gt[0] + (xo - 1) * gt[1], gt[1], 0, gt[3] + (yo - 1) * gt[5], 0, gt[5])  # inclui anel
    return n, xo, yo, arr, gtw


def cmd_atributos():
    ds, gt = vrt_info(); b = ds.GetRasterBand(1)
    Wm, Hm = ds.RasterXSize, ds.RasterYSize
    wkt = srs31985().ExportToWkt()
    cob = {r["slug"]: r for r in csv.DictReader(open(f"{OUT}/cobertura.csv", encoding="utf-8"))}
    rows, curvas, meta_pts = [], [], {}
    for p in le_pontos():
        c = cob[p["slug"]]
        if c["pixel_valido"] != "True":
            rows.append(dict(slug=p["slug"], observacao="fora do mosaico ou pixel nodata")); continue
        n, xo, yo, arr, gtw = le_janela(b, gt, p, Wm, Hm)
        X = gtw[0] + (np.arange(n + 2) + 0.5) * gtw[1]; Y = gtw[3] + (np.arange(n + 2) + 0.5) * gtw[5]
        XX, YY = np.meshgrid(X, Y)
        dist = np.hypot(XX - p["x"], YY - p["y"])
        valido = arr != ND
        r = dict(slug=p["slug"], folha_origem=c["folha_origem"], janela_hidrologia_m=PARAMS["janela_hidrologia_m"])
        # cota da rua
        for rad in PARAMS["raio_cota_m"]:
            m = (dist <= rad) & valido
            r[f"cota_rua_m_raio{rad}m"] = round(float(np.median(arr[m])), 3)
            r[f"n_pixels_raio{rad}m"] = int(m.sum())
            r[f"cota_min_raio{rad}m"] = round(float(arr[m].min()), 3); r[f"cota_max_raio{rad}m"] = round(float(arr[m].max()), 3)
        r["cota_pixel_ponto_m"] = round(float(c["cota_pixel_m"]), 3)
        # declividade
        sl = declividade_pct(arr, gtw, wkt)
        for w_ in PARAMS["janelas_declividade_m"]:
            m = (np.abs(XX - p["x"]) <= w_ / 2) & (np.abs(YY - p["y"]) <= w_ / 2) & ~np.isnan(sl)
            r[f"declividade_media_pct_{w_}m"] = round(float(np.nanmean(sl[m])), 3)
            r[f"declividade_max_pct_{w_}m"] = round(float(np.nanmax(sl[m])), 3)
            r[f"n_pixels_declividade_{w_}m"] = int(m.sum())
        # hidrologia
        print(p["slug"], "hidrologia (motor:", os.environ.get("MOTOR", "whitebox") + ")...", flush=True)
        fill, par, ordem, acc = hidrologia(arr, gtw, p["slug"])
        N = arr.size
        # profundidade da depressão
        pl = int(c["lin"]) - yo + 1; pc = int(c["col"]) - xo + 1
        prof = fill[pl, pc] - arr[pl, pc]
        m5 = (dist <= 5) & valido
        r["profundidade_depressao_m"] = round(float(prof), 3)
        r["profundidade_depressao_max_raio5m"] = round(float((fill - arr)[m5].max()), 3)
        r["fill_no_ponto_m"] = round(float(fill[pl, pc]), 3)
        # área contribuinte: regra de snap = pixel de maior acumulação a <= raio_snap_acumulacao_m (ver maximo_no_raio)
        apx = gt[1] * abs(gt[5])
        amax, ei, ej = maximo_no_raio(acc, valido, dist, PARAMS["raio_snap_acumulacao_m"], pl, pc)
        r["area_contribuinte_m2"] = int(round(amax * apx))
        r["area_contribuinte_pixel_do_ponto_m2"] = int(round(acc[pl, pc] * apx))
        for rs in PARAMS["raios_snap_sensibilidade_m"]:
            r[f"area_contribuinte_m2_snap{rs}m"] = int(round(maximo_no_raio(acc, valido, dist, rs, pl, pc)[0] * apx))
        r["snap_dist_m"] = round(float(dist[ei, ej]), 1)
        r["snap_em_area_preenchida"] = bool(fill[ei, ej] - arr[ei, ej] > 0.001)   # exutório num plano criado pelo fill: roteamento incerto
        r["exutorio_x_utm"] = round(float(X[ej]), 1); r["exutorio_y_utm"] = round(float(Y[ei]), 1)
        r["exutorio_cota_m"] = round(float(arr[ei, ej]), 3)
        bacia = bacia_do_exutorio(par, ordem, N, ei * (n + 2) + ej).reshape(arr.shape)
        tb = bacia.copy(); tb[2:-2, 2:-2] = False
        r["bacia_area_m2"] = int(bacia.sum())
        r["bacia_toca_borda_janela"] = bool(tb.any())
        # vizinho nodata dentro da bacia (água/lacunas)
        nd_viz = False
        nodata_mask = ~valido; nodata_mask[0, :] = nodata_mask[-1, :] = nodata_mask[:, 0] = nodata_mask[:, -1] = False
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dy or dx:
                    nd_viz |= bool((np.roll(np.roll(nodata_mask, dy, 0), dx, 1) & bacia).any())
        r["bacia_adjacente_a_nodata"] = nd_viz
        r["bacia_cota_min_m"] = round(float(arr[bacia].min()), 3); r["bacia_cota_max_m"] = round(float(arr[bacia].max()), 3)
        # curva cota-volume: fundo = célula de menor cota da bacia a <= raio_fundo_curva_m do ponto
        arrb = np.where(bacia & (dist <= PARAMS["raio_fundo_curva_m"]), arr, np.inf)
        fi = int(np.argmin(arrb))
        z0, curva, ncon = curva_cota_volume(arr, bacia, fi, n + 2, PARAMS["curva_passo_m"], PARAMS["curva_altura_max_m"], ND)
        fy, fx = divmod(fi, n + 2)
        r["fundo_bacia_cota_m"] = round(z0, 3); r["fundo_bacia_x_utm"] = round(float(X[fx]), 1); r["fundo_bacia_y_utm"] = round(float(Y[fy]), 1)
        r["fundo_bacia_dist_ponto_m"] = round(float(dist[fy, fx]), 1)
        r["curva_celulas_conectadas"] = ncon
        r["curva_volume_2m_m3"] = round(curva[-1][2], 1)
        r["curva_area_2m_m2"] = curva[-1][1]
        prev = None
        for k, (z, na, vol) in enumerate(curva):
            m3cm = "" if k == 0 else round((vol - curva[k - 1][2]) / (PARAMS["curva_passo_m"] * 100), 3)
            curvas.append(dict(slug=p["slug"], cota_m=round(z, 3), altura_acima_fundo_m=round(z - z0, 3),
                               area_superficie_m2=na, volume_acumulado_m3=round(vol, 3), m3_por_cm=m3cm))
        rows.append(r)
        print({k: v for k, v in r.items() if k in ("slug", "cota_rua_m_raio3m", "declividade_media_pct_50m", "area_contribuinte_m2", "profundidade_depressao_m", "bacia_toca_borda_janela")}, flush=True)
    cols = []
    for r in rows:
        for k in r:
            if k not in cols: cols.append(k)
    write_csv(f"{OUT}/ponto_terreno.csv", rows, cols)
    write_csv(f"{OUT}/curva_cota_volume.csv", curvas, ["slug", "cota_m", "altura_acima_fundo_m", "area_superficie_m2", "volume_acumulado_m3", "m3_por_cm"])
    vrt_sha = hashlib.sha256(open(VRT, "rb").read()).hexdigest()
    script_sha = hashlib.sha256(open(os.path.abspath(__file__), "rb").read()).hexdigest()
    meta = dict(
        gerado_por="tools/terreno/terreno.py atributos", sha256_mosaico_vrt=vrt_sha, sha256_terreno_py=script_sha,
        voo="PE3D 2016 (MDT 1 m)", crs="EPSG:31985 (SIRGAS 2000 / UTM 25S)", cota="ortométrica (datum vertical do MDT); NÃO comparável à maré (NR/DHN) sem ADR de referência vertical",
        nodata=ND, parametros=PARAMS,
        software=dict(gdal=gdal.__version__, numpy=np.__version__, python=sys.version.split()[0],
                      imagem=os.environ.get("GDAL_IMAGE", "desconhecida"),
                      whitebox=(open(f"{OUT}/whitebox/versao.txt").read().strip() if os.path.exists(f"{OUT}/whitebox/versao.txt") else None),
                      whitebox_sha256_binario=(open(f"{OUT}/whitebox/binario.sha256").read().split()[0] if os.path.exists(f"{OUT}/whitebox/binario.sha256") else None),
                      motor_hidrologia=os.environ.get("MOTOR", "whitebox")),
        folhas=[r["folha"] for r in csv.DictReader(open(f"{OUT}/inventario.csv", encoding="utf-8"))],
        colunas=cols,
        definicoes=dict(
            cota_rua_m="mediana do MDT nos pixels (válidos) cujo centro está a <= raio do ponto; colunas _raio3m e _raio5m",
            declividade_media_pct="média de gdaldem slope -p (Horn, computeEdges=False) nos pixels dentro de janela quadrada de lado 50 m / 100 m centrada no ponto",
            area_contribuinte_m2="acumulação D8 do WhiteboxTools (nº de células x área do pixel) sobre o DEM preenchido da janela; valor do pixel de maior acumulação a <= 10 m do ponto (regra de snap em docs/TERRENO-PE3D.md, seção 5.1)",
            profundidade_depressao_m="fill - MDT no pixel que contém o ponto",
            bacia="células cujo caminho D8 passa pelo exutório; fundo = célula de menor cota da bacia a <= 50 m do ponto",
            curva_cota_volume="V(z) = soma (z - cota) das células da bacia conectadas (8-vizinhos, caminho com cota < z) ao fundo; z = fundo + k*5 cm, k=0..40; m3_por_cm = dV/(5 cm)",
            efeito_de_borda="fora da janela o terreno é tratado como nodata (saída); se a bacia toca a borda, a área é limite inferior (bacia_toca_borda_janela)"))
    json.dump(meta, open(f"{OUT}/ponto_terreno.meta.json", "w"), indent=2, ensure_ascii=False)


# ---------------------------------------------------------------- validação x WhiteboxTools
def hidro_arvore(arr):
    """Método legado: acumulação sobre a árvore de descoberta do Priority-Flood. Retorna (fill, acc 2D, par 2D)."""
    fill, par, ordem = priority_flood(arr, ND)
    return fill, np.array(acumulacao(par, ordem, arr.size)).reshape(arr.shape), np.array(par).reshape(arr.shape)


def hidro_d8(arr):
    """Método próprio atual: Priority-Flood + D8 de máxima declividade. Retorna (fill, acc 2D, par 2D)."""
    fill, par, ordem, acc = hidrologia_d8(arr)
    return fill, np.array(acc).reshape(arr.shape), np.array(par).reshape(arr.shape)


METODOS_PROPRIOS = {"arvore_pf": hidro_arvore, "d8_pf": hidro_d8}
BORDA_PX = 300   # fill do Whitebox sobe células de borda (não as trata como saída); estatísticas "internas" ignoram 300 px


def maximo_no_raio(acc, valido, dist, raio, pl, pc):
    """REGRA DE SNAP. Pixel de maior acumulação a <= raio (m) do ponto, centros de pixel, só pixels válidos.
    raio 0 = o pixel que contém o ponto. Desempate: menor distância ao ponto, depois menor (linha, coluna), para a saída
    ser determinística. Retorna (acc, linha, coluna)."""
    if raio <= 0:
        return int(acc[pl, pc]), pl, pc
    m = (dist <= raio) & valido
    a = np.where(m, acc, -1)
    mx = a.max()
    cand = np.argwhere(a == mx)
    k = min(range(len(cand)), key=lambda q: (dist[cand[q][0], cand[q][1]], cand[q][0], cand[q][1]))
    return int(mx), int(cand[k][0]), int(cand[k][1])


def cmd_validar():
    """Compara a hidrologia própria (Priority-Flood + árvore; Priority-Flood + D8) e o Whitebox com breaching com a do
    WhiteboxTools de referência (FillDepressions, D8Pointer, D8FlowAccumulation) nas janelas de 2 km.
    Saídas: validacao_whitebox.csv e validacao_whitebox.json."""
    ds, gt = vrt_info(); b = ds.GetRasterBand(1); Wm, Hm = ds.RasterXSize, ds.RasterYSize
    cob = {r["slug"]: r for r in csv.DictReader(open(f"{OUT}/cobertura.csv", encoding="utf-8"))}
    rows = []
    for p in le_pontos():
        c = cob[p["slug"]]
        if c["pixel_valido"] != "True": continue
        s = p["slug"]
        n, xo, yo, arr, gtw = le_janela(b, gt, p, Wm, Hm)
        valido = arr != ND
        pl = int(c["lin"]) - yo + 1; pc = int(c["col"]) - xo + 1
        X = gtw[0] + (np.arange(n + 2) + 0.5) * gtw[1]; Y = gtw[3] + (np.arange(n + 2) + 0.5) * gtw[5]
        XX, YY = np.meshgrid(X, Y); dist = np.hypot(XX - p["x"], YY - p["y"])
        interno = np.zeros(arr.shape, bool); interno[BORDA_PX + 1:-BORDA_PX - 1, BORDA_PX + 1:-BORDA_PX - 1] = True
        print(s, "whitebox...", flush=True)
        W_ = executa_whitebox(s, arr, gtw); B_ = executa_whitebox(s, arr, gtw, breach=True)
        wfill, wacc = W_["fill"], W_["acc"]
        pw = par_do_whitebox(W_["ptr"], valido)
        cands = {nome: fn(arr) for nome, fn in METODOS_PROPRIOS.items()}
        cands["whitebox_breach"] = (None, B_["acc"], par_do_whitebox(B_["ptr"], valido).reshape(arr.shape))
        for nome, (fill, acc, par) in cands.items():
            r = dict(slug=s, metodo=nome)
            r["prof_depressao_whitebox_m"] = round(float(wfill[pl, pc] - arr[pl, pc]), 4)
            r["celulas_preenchidas_whitebox"] = int(((wfill - arr > 0.001) & valido).sum())
            if fill is not None:
                r["prof_depressao_metodo_m"] = round(float(fill[pl, pc] - arr[pl, pc]), 4)
                dif = np.abs(np.where(valido, fill - wfill, 0))
                r["fill_dif_max_janela_m"] = round(float(dif.max()), 4)
                r["fill_dif_max_interno_m"] = round(float(dif[interno].max()), 4)
                r["fill_celulas_dif_gt_1cm_interno"] = int((dif[interno] > 0.01).sum())
                r["celulas_preenchidas_metodo"] = int(((fill - arr > 0.001) & valido).sum())
            sel = valido & interno
            r["log10_acc_correlacao_interna"] = round(float(np.corrcoef(np.log10(np.maximum(acc[sel], 1)), np.log10(np.maximum(wacc[sel], 1)))[0, 1]), 5)
            r["celulas_acc_ge_1000_metodo"] = int((acc[sel] >= 1000).sum())
            r["celulas_acc_ge_1000_whitebox"] = int((wacc[sel] >= 1000).sum())
            pp = par.ravel(); pwr = pw.ravel()
            if fill is not None:     # concordância de direção fora de planos (célula com vizinho estritamente mais baixo no fill do método)
                fl = fill.ravel()
                it = sel.ravel() & (pp >= 0) & (pwr >= 0)
                npl = it & (fl - fl[np.where(pp >= 0, pp, 0)] > 0)
                r["celulas_nao_plano"] = int(npl.sum())
                r["direcao_concordancia_nao_plano_pct"] = round(100 * float((pp[npl] == pwr[npl]).mean()), 3)
            for rs in PARAMS["raios_snap_sensibilidade_m"]:
                a1, l1, c1 = maximo_no_raio(acc, valido, dist, rs, pl, pc)
                a2, l2, c2 = maximo_no_raio(wacc, valido, dist, rs, pl, pc)
                r[f"acc_snap{rs}m_metodo"] = a1; r[f"acc_snap{rs}m_whitebox"] = a2
                r[f"acc_snap{rs}m_dif_abs"] = a1 - a2
                r[f"acc_snap{rs}m_dif_rel_pct"] = round(100 * (a1 - a2) / max(a2, 1), 2)
                r[f"acc_snap{rs}m_mesmo_pixel"] = (l1, c1) == (l2, c2)
                r[f"acc_snap{rs}m_dist_pixels_m"] = round(float(math.hypot(l1 - l2, c1 - c2)), 1)
            rows.append(r)
            print({k: r.get(k) for k in ("slug", "metodo", "prof_depressao_metodo_m", "prof_depressao_whitebox_m", "fill_dif_max_interno_m",
                                         "direcao_concordancia_nao_plano_pct", "log10_acc_correlacao_interna",
                                         "acc_snap10m_metodo", "acc_snap10m_whitebox")}, flush=True)
    cols = []
    for r in rows:
        for k in r:
            if k not in cols: cols.append(k)
    write_csv(f"{OUT}/validacao_whitebox.csv", rows, cols)
    wbt_v = open(f"{OUT}/whitebox/versao.txt").read().strip() if os.path.exists(f"{OUT}/whitebox/versao.txt") else ""
    wbt_h = open(f"{OUT}/whitebox/binario.sha256").read().split()[0] if os.path.exists(f"{OUT}/whitebox/binario.sha256") else ""
    json.dump(dict(whitebox=wbt_v, sha256_binario_whitebox=wbt_h,
                   ferramentas="FillDepressions; FillDepressions --fix_flats -> D8Pointer; D8FlowAccumulation --pntr --out_type=cells; "
                               "breach: BreachDepressionsLeastCost --dist=500 --fill",
                   raios_snap_m=PARAMS["raios_snap_sensibilidade_m"], borda_ignorada_px=BORDA_PX, linhas=rows),
              open(f"{OUT}/validacao_whitebox.json", "w"), indent=1, ensure_ascii=False)


# ---------------------------------------------------------------- comparação
def cmd_comparar():
    alt = {r["slug"]: r for r in csv.DictReader(open(f"{OUT}/altitude_open_meteo.csv", encoding="utf-8"))}
    pt = {r["slug"]: r for r in csv.DictReader(open(f"{OUT}/ponto_terreno.csv", encoding="utf-8"))}
    rows = []
    for slug, r in pt.items():
        if not r.get("cota_rua_m_raio3m"): continue
        a = float(alt[slug]["elevation_open_meteo_m"]); c3 = float(r["cota_rua_m_raio3m"]); c5 = float(r["cota_rua_m_raio5m"])
        rows.append(dict(slug=slug, altitude_atual_m=a, cota_rua_m_raio3m=c3, cota_rua_m_raio5m=c5,
                         diferenca_m=round(c3 - a, 3), diferenca_abs_m=round(abs(c3 - a), 3)))
    write_csv(f"{OUT}/comparacao_altitude.csv", rows, list(rows[0].keys()))
    d = np.array([r["diferenca_m"] for r in rows]); da = np.abs(d)
    res = dict(n=len(rows), media_diferenca_m=round(float(d.mean()), 3), media_abs_m=round(float(da.mean()), 3),
               max_abs_m=round(float(da.max()), 3), slug_max_abs=rows[int(da.argmax())]["slug"],
               definicao="diferenca = cota_rua_m (mediana raio 3 m, MDT PE3D 2016) - altitudeM (Open-Meteo, DEM ~90 m)")
    json.dump(res, open(f"{OUT}/comparacao_altitude.json", "w"), indent=2, ensure_ascii=False)
    print(json.dumps(res, ensure_ascii=False))


# ---------------------------------------------------------------- estimativas
def cmd_estimativas():
    """Tamanho de COG: bytes/pixel medido em COGs gravados em /vsimem de folhas inteiras (amostra),
    multiplicado pelo nº de pixels válidos do mosaico e do bbox dos pontos + 2 km. Nada é gravado em disco."""
    inv = list(csv.DictReader(open(f"{OUT}/inventario.csv", encoding="utf-8")))
    cob = list(csv.DictReader(open(f"{OUT}/cobertura.csv", encoding="utf-8")))
    folhas_amostra = sorted({c["folha_origem"].split(";")[0] for c in cob if c["folha_origem"]})
    amostra = []
    for f in folhas_amostra:
        z = f"{DADOS}/{f}.zip"
        dst = "/vsimem/amostra.tif"
        gdal.Translate(dst, f"/vsizip/{z}/{inner_tif(z)}", format="COG",
                       creationOptions=["COMPRESS=DEFLATE", "PREDICTOR=3"], outputSRS="EPSG:31985")
        sz = gdal.VSIStatL(dst).size; gdal.Unlink(dst)
        nv = int(next(r for r in inv if r["folha"] == f)["pixels_validos"])
        px = int(next(r for r in inv if r["folha"] == f)["largura_px"]) * int(next(r for r in inv if r["folha"] == f)["altura_px"])
        amostra.append(dict(folha=f, cog_bytes=sz, pixels_total=px, pixels_validos=nv, bytes_por_pixel_total=round(sz / px, 4),
                            bytes_por_pixel_valido=round(sz / nv, 4)))
        print(amostra[-1], flush=True)
    bpp = [a["bytes_por_pixel_total"] for a in amostra]
    tot_px = sum(int(r["largura_px"]) * int(r["altura_px"]) for r in inv)
    # extensão do mosaico (bbox) para COG único
    ds, gt = vrt_info(); mosaico_px = ds.RasterXSize * ds.RasterYSize
    xs = [c["x_utm"] for c in cob if c["pixel_valido"] == "True"]; ys = [c["y_utm"] for c in cob if c["pixel_valido"] == "True"]
    x0, x1 = min(map(float, xs)) - 2000, max(map(float, xs)) + 2000; y0, y1 = min(map(float, ys)) - 2000, max(map(float, ys)) + 2000
    bbox_px = int((x1 - x0) / gt[1]) * int((y1 - y0) / abs(gt[5]))
    # pixels válidos dentro do bbox (lê só o bbox)
    b = ds.GetRasterBand(1)
    xo = int((x0 - gt[0]) / gt[1]); yo = int((gt[3] - y1) / abs(gt[5]))
    w = int((x1 - x0) / gt[1]); h = int((y1 - y0) / abs(gt[5]))
    cx0, cy0 = max(xo, 0), max(yo, 0); cx1, cy1 = min(xo + w, ds.RasterXSize), min(yo + h, ds.RasterYSize)
    nvalid_bbox = 0
    for yy in range(cy0, cy1, 512):
        a = b.ReadAsArray(cx0, yy, cx1 - cx0, min(512, cy1 - yy)); nvalid_bbox += int((a != ND).sum())
    tot_valid = sum(int(r["pixels_validos"]) for r in inv)
    bpv = [a["bytes_por_pixel_valido"] for a in amostra]
    res = dict(amostra=amostra, pixels_validos_mosaico=tot_valid, pixels_folhas_soma=tot_px, pixels_extensao_vrt=mosaico_px,
               bbox_pontos_mais_2km=dict(xmin=x0, xmax=x1, ymin=y0, ymax=y1, pixels=bbox_px, pixels_validos=nvalid_bbox),
               estimativa_cog_mosaico_inteiro_bytes=dict(min=int(min(bpv) * tot_valid), max=int(max(bpv) * tot_valid), media=int(np.mean(bpv) * tot_valid)),
               estimativa_cog_bbox_bytes=dict(min=int(min(bpv) * nvalid_bbox), max=int(max(bpv) * nvalid_bbox), media=int(np.mean(bpv) * nvalid_bbox)),
               metodo="bytes por pixel válido medidos em COG (DEFLATE+PREDICTOR=3) de folhas inteiras gravado em /vsimem, x pixels válidos; inclui as overviews que o driver COG gera por padrão")
    json.dump(res, open(f"{OUT}/estimativas_cog.json", "w"), indent=2, ensure_ascii=False)
    print(json.dumps({k: v for k, v in res.items() if k != "amostra"}, ensure_ascii=False))


if __name__ == "__main__":
    {"inventario": cmd_inventario, "cobertura": cmd_cobertura, "atributos": cmd_atributos,
     "comparar": cmd_comparar, "validar": cmd_validar, "estimativas": cmd_estimativas}[sys.argv[1]]()
