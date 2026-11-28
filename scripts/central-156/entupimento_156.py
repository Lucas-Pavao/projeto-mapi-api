"""Entupimento e limpeza pela Central 156 (docs/PRE-REGISTRO-ENTUPIMENTO-156.md). Sem rede.

    python3 -I scripts/central-156/entupimento_156.py <pasta_156_csv_gz> <pasta_esig_geojson> <pasta_series_chuva> \
        [<bairros_recife.geojson>]

Emenda 1 (2026-11-22, aprovada pelo Lucas): E1 coordenadas-padrão fora do casamento por coordenada; E2 bairro do ponto pelo
polígono oficial da Prefeitura (4º argumento); E3 intervalo p95 entre limpezas e fim da cobertura; E4 regime permanente
(média restrita a 365 dias do Kaplan–Meier do atendimento).

Escreve em docs/extratos/emlurb-156/entupimento/:
  casamento.csv, lambda_por_ponto.csv, atendimento_km.csv, reentupimento.csv, sazonalidade.csv, dias_secos.csv, parametros.json
"""
from __future__ import annotations

import collections
import csv
import datetime as dt
import gzip
import io
import json
import math
import sys
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "docs" / "extratos" / "emlurb-156" / "entupimento"
PONTOS_CSV = RAIZ / "tools" / "terreno" / "pontos_criticos.csv"
PONTOS = ["AV_RECIFE_IBURA", "CIN_UFPE", "AGAMENON_DERBY", "MASCARENHAS_IMBIRIBEIRA"]
# grafia da própria 156 (correção registrada no pré-registro, seção 2)
LOGRADOURO = {"AV_RECIFE_IBURA": "RECIFE", "AGAMENON_DERBY": "GOVERNADOR AGAMENON MAGALHAES",
              "MASCARENHAS_IMBIRIBEIRA": "MARECHAL MASCARENHAS DE MORAES", "CIN_UFPE": None}
ENTUPIMENTO = {("2", "4"), ("11", "4"), ("27", "8"), ("2", "81"), ("11", "81"), ("27", "157")}
ESTRUTURAL = {("2", "72"), ("11", "72"), ("27", "141"), ("2", "97"), ("11", "97"), ("27", "189")}
RAIO, RAIOS_SENS = 150.0, (100.0, 300.0)
MARCOS_DIAS = (30, 60, 90, 180, 365)
PREFIXOS = {"AV", "AVENIDA", "R", "RUA", "TR", "TV", "TRAVESSA", "EST", "ESTRADA", "PC", "PRACA", "1TR", "2TR", "3TR", "4TR",
            "5TR", "1TV", "2TV", "PR", "1PR", "2PR", "VL", "VILA", "LG", "LARGO", "CAIS", "BC", "BECO", "1BC", "2BC", "1SB", "SB"}


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().upper()
    s = "".join(ch if ch.isalnum() or ch == " " else " " for ch in s)
    return " ".join(s.split())


def norm_logradouro(s: str) -> str:
    p = norm(s).split()
    while p and p[0] in PREFIXOS:
        p = p[1:]
    return " ".join(p)


def dist_m(lat1, lon1, lat2, lon2) -> float:
    r = 6371008.8
    f1, f2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((f2 - f1) / 2) ** 2 + math.cos(f1) * math.cos(f2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def data(s: str) -> dt.date | None:
    try:
        return dt.date.fromisoformat(s.strip()[:10])
    except ValueError:
        return None


def le_156(pasta: Path) -> list[dict]:
    out = []
    for gz in sorted(pasta.glob("*.csv.gz")):
        txt = gzip.open(gz, "rb").read().decode("utf-8-sig")
        rd = csv.reader(io.StringIO(txt, newline=""), delimiter=";")
        cab = [c.strip().upper() for c in next(rd)]
        for r in rd:
            if len(r) != len(cab):
                continue
            d = {k: v.strip() for k, v in zip(cab, r)}
            chave = (d["GRUPOSERVICO_CODIGO"], d["SERVICO_CODIGO"])
            lat = lon = None
            try:
                lat, lon = float(d.get("LATITUDE") or "x"), float(d.get("LONGITUDE") or "x")
                if not (-8.2 < lat < -7.9 and -35.1 < lon < -34.8):
                    lat = lon = None
            except ValueError:
                pass
            out.append(dict(ano=gz.name[:4], chave=chave, entupimento=chave in ENTUPIMENTO, estrutural=chave in ESTRUTURAL,
                            atendida=norm(d["SITUACAO"]) == "ATENDIDA", d0=data(d["DATA_DEMANDA"]), d1=data(d["DATA_ULT_SITUACAO"]),
                            logr=norm_logradouro(d["LOGRADOURO"]), bairro=norm(d["BAIRRO"]), lat=lat, lon=lon))
    return out


def kaplan_meier(tempos: list[tuple[float, bool]]) -> list[tuple[float, float]]:
    """(tempo, evento) -> [(t, S(t))] nos tempos de evento."""
    tempos = sorted(tempos)
    n = len(tempos); s = 1.0; curva = []; i = 0
    while i < len(tempos):
        t = tempos[i][0]; e = c = 0
        while i < len(tempos) and tempos[i][0] == t:
            e += tempos[i][1]; c += 1; i += 1
        if e:
            s *= 1 - e / n; curva.append((t, s))
        n -= c
    return curva


def s_em(curva, t) -> float:
    s = 1.0
    for ti, si in curva:
        if ti > t:
            break
        s = si
    return s


def quantil_km(curva, q) -> float | None:
    for t, s in curva:
        if s <= 1 - q:
            return t
    return None


def media_restrita_km(curva, ate: float = 365.0) -> float | None:
    """Área sob S(t) de 0 a `ate` dias (média restrita do Kaplan–Meier). Curva vazia = None."""
    if not curva:
        return None
    area, t_ant, s_ant = 0.0, 0.0, 1.0
    for t, s in curva:
        if t >= ate:
            break
        area += s_ant * (t - t_ant)
        t_ant, s_ant = t, s
    return area + s_ant * (ate - t_ant)


def p95(vals: list[int]) -> int | None:
    """Percentil 95 por posto mais próximo (nearest-rank)."""
    if not vals:
        return None
    v = sorted(vals)
    return v[max(0, math.ceil(0.95 * len(v)) - 1)]


COORD_PADRAO_MIN, COORD_PADRAO_FRAC = 20, 0.5          # E1 (emenda 1)


def coordenadas_padrao(todos: list[dict]) -> dict[tuple, dict]:
    """E1: coordenada (5 casas) com >= 20 chamados e bairro mais frequente < 50% deles."""
    n = collections.Counter(); b = collections.defaultdict(collections.Counter)
    for r in todos:
        if r["lat"] is None:
            continue
        k = (round(r["lat"], 5), round(r["lon"], 5)); n[k] += 1; b[k][r["bairro"]] += 1
    out = {}
    for k, c in n.items():
        if c >= COORD_PADRAO_MIN:
            top, ct = b[k].most_common(1)[0]
            if ct / c < COORD_PADRAO_FRAC:
                out[k] = dict(chamados=c, bairros=len(b[k]), bairro_mais_frequente=top, fracao=round(ct / c, 3))
    return out


def _dentro(lon, lat, anel) -> bool:
    dentro = False
    for (x1, y1), (x2, y2) in zip(anel, anel[1:] + anel[:1]):
        if (y1 > lat) != (y2 > lat) and lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
            dentro = not dentro
    return dentro


def bairro_oficial(geojson: Path | None, lat: float, lon: float) -> str | None:
    """E2: bairro do polígono oficial que contém o ponto (EBAIRRNOME normalizado); fora de todos = None."""
    if geojson is None or not geojson.exists():
        return None
    for f in json.loads(geojson.read_text(encoding="utf-8"))["features"]:
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        for poly in polys:
            ext = [tuple(p[:2]) for p in poly[0]]
            if _dentro(lon, lat, ext) and not any(_dentro(lon, lat, [tuple(p[:2]) for p in buraco]) for buraco in poly[1:]):
                return norm(f["properties"].get("EBAIRRNOME") or "")
    return None


def main() -> None:
    pasta156, pasta_esig, pasta_chuva = (Path(a) for a in sys.argv[1:4])
    bairros_geojson = Path(sys.argv[4]) if len(sys.argv) > 4 else None
    SAIDA.mkdir(parents=True, exist_ok=True)
    pts = {r["slug"]: r for r in csv.DictReader(PONTOS_CSV.open(encoding="utf-8"))}
    todos = le_156(pasta156)
    fim = max(r["d0"] for r in todos if r["d0"])
    # cobertura = do 1º dia do ano do arquivo mais antigo (2012) ao fim; o arquivo de 2012 traz algumas demandas antigas (2008)
    ini = dt.date(int(min(r["ano"] for r in todos)), 1, 1)
    anos_cob = (fim - ini).days / 365.25
    padrao = coordenadas_padrao(todos)
    params: dict = {"gerado_por": "scripts/central-156/entupimento_156.py", "pre_registro": "docs/PRE-REGISTRO-ENTUPIMENTO-156.md",
                    "emenda": "1 (2026-11-22): E1 coordenadas-padrão, E2 bairro oficial, E3 limpeza conhecida, E4 regime permanente",
                    "inicio_dados": str(ini), "fim_dados": str(fim), "fim_cobertura": str(fim), "raio_m": RAIO,
                    "coordenadas_padrao": {"regra": f">= {COORD_PADRAO_MIN} chamados e bairro mais frequente < {COORD_PADRAO_FRAC:.0%}",
                                           "n": len(padrao), "chamados": sum(v["chamados"] for v in padrao.values())},
                    "pontos": {}}
    (SAIDA / "coordenadas_padrao.json").write_text(json.dumps(
        [dict(lat=k[0], lon=k[1], **v) for k, v in sorted(padrao.items(), key=lambda x: -x[1]["chamados"])],
        ensure_ascii=False, indent=1), encoding="utf-8")
    lin_cas, lin_lam, lin_km, lin_re, lin_saz, lin_seco = [], [], [], [], [], []

    for slug in PONTOS:
        p = pts[slug]; la, lo = float(p["latitude"]), float(p["longitude"])
        # bairro do ponto (E2): polígono oficial da Prefeitura; a regra antiga (mais frequente a 150 m) fica só no relatório
        bc = collections.Counter(r["bairro"] for r in todos if r["lat"] is not None and dist_m(la, lo, r["lat"], r["lon"]) <= RAIO)
        bairro_antigo = bc.most_common(1)[0][0] if bc else None
        bairro = bairro_oficial(bairros_geojson, la, lo) if bairros_geojson else bairro_antigo
        logr = LOGRADOURO[slug]

        def casa_coord(r, raio=RAIO):
            # E1: chamado numa coordenada-padrão não diz onde a boca de lobo está
            return (r["lat"] is not None and (round(r["lat"], 5), round(r["lon"], 5)) not in padrao
                    and dist_m(la, lo, r["lat"], r["lon"]) <= raio)

        def casa_logr(r):
            return logr is not None and r["logr"] == logr and r["bairro"] == bairro

        ent = [r for r in todos if r["entupimento"] and r["d0"]]
        no_bairro = [r for r in ent if r["bairro"] == bairro]
        por_coord = [r for r in ent if casa_coord(r)]
        por_logr = [r for r in ent if casa_logr(r)]
        uniao = [r for r in ent if casa_coord(r) or casa_logr(r)]
        # SENSIBILIDADE, fora do pré-registro (declarada no relatório): a rua inteira, em qualquer bairro
        rua = [r for r in ent if logr is not None and r["logr"] == logr]
        perto_padrao = sum(1 for r in todos if r["lat"] is not None and (round(r["lat"], 5), round(r["lon"], 5)) in padrao
                           and dist_m(la, lo, r["lat"], r["lon"]) <= RAIO)
        lin_cas.append(dict(ponto=slug, bairro=bairro, bairro_regra_antiga=bairro_antigo, chamados_coord_padrao_150m=perto_padrao,
                            logradouro=logr, chamados_entupimento_bairro=len(no_bairro),
                            casados_rua_qualquer_bairro_sensibilidade=len(rua),
                            casados_coordenada_150m=len(por_coord), casados_logradouro_bairro=len(por_logr), casados_uniao=len(uniao),
                            pct_bairro_casado_uniao=round(100 * len(uniao) / len(no_bairro), 1) if no_bairro else "",
                            **{f"casados_coordenada_{int(s)}m": sum(1 for r in ent if casa_coord(r, s)) for s in RAIOS_SENS}))
        # BL do ESIG a <= 150 m
        nbl = 0
        g = pasta_esig / f"{slug}__MI_c0.geojson"
        if g.exists():
            from_utm = None  # coordenadas do ESIG estão em UTM 25S; converte o ponto
            sys.path.insert(0, str(RAIZ / "scripts" / "capacidade-galeria"))
            try:
                from utm import para_utm25s
                e0, n0 = para_utm25s(la, lo)
                nbl = sum(1 for f in json.loads(g.read_text(encoding="utf-8"))["features"]
                          if f["properties"]["TIPO"] == "BL" and math.dist(f["geometry"]["coordinates"][:2], (e0, n0)) <= RAIO)
            except ImportError:
                nbl = None
        # λ por ano (coordenada: só anos com coordenada, 2015-2023 e 2025-2026; usa a cobertura real)
        anos_coord = sorted({r["ano"] for r in todos if r["lat"] is not None})
        for escopo, lista, anos in (("ponto_150m", por_coord, len(anos_coord)), ("ponto_uniao", uniao, anos_cob),
                                    ("rua_sensibilidade", rua, anos_cob),
                                    ("bairro", no_bairro, anos_cob)):
            lam = len(lista) / anos if anos else None
            lin_lam.append(dict(ponto=slug, escopo=escopo, chamados=len(lista), anos=round(anos, 2),
                                chamados_por_ano=round(lam, 2) if lam else "", bl_esig_150m=nbl if escopo.startswith("ponto") else "",
                                chamados_por_bl_por_ano=round(lam / nbl, 3) if (lam and nbl and escopo.startswith("ponto")) else ""))
        # tempo de atendimento (KM com censura), no bairro e no ponto
        atend: dict = {}
        for escopo, lista in (("ponto_uniao", uniao), ("bairro", no_bairro)):
            tt = [(max(0, (r["d1"] - r["d0"]).days), r["atendida"]) for r in lista if r["d1"]]
            km = kaplan_meier(tt)
            rm = media_restrita_km(km)
            lin_km.append(dict(ponto=slug, escopo=escopo, n=len(tt), atendidas=sum(e for _, e in tt),
                               mediana_dias=quantil_km(km, 0.5), q25_dias=quantil_km(km, 0.25), q75_dias=quantil_km(km, 0.75),
                               media_restrita_365_dias=round(rm, 1) if rm is not None else ""))
            atend[escopo] = (sum(e for _, e in tt), rm)
        # reentupimento depois de limpeza
        for escopo, lista in (("ponto_uniao", uniao), ("rua_sensibilidade", rua), ("bairro", no_bairro)):
            limp = sorted(r["d1"] for r in lista if r["atendida"] and r["d1"])
            chamados = sorted(r["d0"] for r in lista)
            tt = []
            for d in limp:
                prox = next((c for c in chamados if c > d), None)
                tt.append(((prox - d).days, True) if prox else ((fim - d).days, False))
            ev = sum(e for _, e in tt); expo = sum(t for t, _ in tt)
            lam_re = ev / expo if expo else None
            km = kaplan_meier(tt)
            intervalos = [(b - a).days for a, b in zip(limp, limp[1:]) if (b - a).days > 0]
            linha = dict(ponto=slug, escopo=escopo, limpezas=len(limp), reentupimentos=ev, dias_expostos=expo,
                         lambda_re_por_dia=round(lam_re, 5) if lam_re else "",
                         media_dias_ate_reentupir=round(1 / lam_re, 1) if lam_re else "",
                         mediana_km_dias=quantil_km(km, 0.5),
                         mediana_intervalo_entre_limpezas_dias=sorted(intervalos)[len(intervalos) // 2] if intervalos else "",
                         **{f"p_reentupido_{d}d_km": round(1 - s_em(km, d), 3) if tt else "" for d in MARCOS_DIAS},
                         **{f"p_reentupido_{d}d_exp": round(1 - math.exp(-lam_re * d), 3) if lam_re else "" for d in MARCOS_DIAS})
            lin_re.append(linha)
            if escopo == "rua_sensibilidade":
                params["pontos"][slug].update(lambda_re_por_dia_rua_sensibilidade=linha["lambda_re_por_dia"], limpezas_rua=len(limp))
            elif escopo == "ponto_uniao":
                params["pontos"][slug] = dict(bairro=bairro, bairro_regra_antiga=bairro_antigo,
                                              lambda_re_por_dia_ponto=linha["lambda_re_por_dia"],
                                              limpezas_ponto=len(limp), reentupimentos_ponto=ev,
                                              intervalo_p95_entre_limpezas_dias=p95(intervalos) if intervalos else "")
            else:
                params["pontos"][slug].update(lambda_re_por_dia_bairro=linha["lambda_re_por_dia"], limpezas_bairro=len(limp),
                                              reentupimentos_bairro=ev,
                                              ultima_limpeza_conhecida_ponto=str(max((r["d1"] for r in uniao if r["atendida"] and r["d1"]), default="")))
        # E3/E4: limpeza conhecida e regime permanente
        pp = params["pontos"][slug]
        n_at, rm = atend.get("ponto_uniao", (0, None))
        fonte_de = "ponto"
        if n_at < 10:
            n_at, rm = atend.get("bairro", (0, None)); fonte_de = "bairro"
        pp["dias_entupida_media"] = round(rm, 1) if rm is not None else ""
        pp["dias_entupida_fonte"] = fonte_de
        lam = pp.get("lambda_re_por_dia_ponto")
        pp["pi_entupida_permanente"] = round(rm / (1 / lam + rm), 4) if (lam and rm is not None) else ""
        ult = pp.get("ultima_limpeza_conhecida_ponto")
        n95 = pp.get("intervalo_p95_entre_limpezas_dias")
        if ult and n95 != "":
            vale = (fim - dt.date.fromisoformat(ult)).days <= n95
            pp["limpeza_conhecida_no_fim_da_cobertura"] = vale
        else:
            pp["limpeza_conhecida_no_fim_da_cobertura"] = False
        # sazonalidade
        for escopo, lista in (("ponto_uniao", uniao), ("bairro", no_bairro)):
            m = collections.Counter(r["d0"].month for r in lista)
            lin_saz.append(dict(ponto=slug, escopo=escopo, **{f"m{k:02d}": m.get(k, 0) for k in range(1, 13)}))
        # dias secos antes do chamado (só chuva)
        sc = pasta_chuva / f"serie10min_{slug}.csv.gz"
        if sc.exists():
            dia = collections.defaultdict(float); visto = set()
            for r in csv.DictReader(io.StringIO(gzip.open(sc, "rt", encoding="utf-8").read())):
                if r["mm"] == "":
                    continue
                t = dt.datetime.fromisoformat(r["slot_end_utc"].replace("Z", "+00:00")) - dt.timedelta(hours=3)
                dia[t.date()] += float(r["mm"]); visto.add(t.date())
            d0s, d1s = min(visto), max(visto)

            def secos(d):
                k = 0; x = d - dt.timedelta(days=1)
                while x >= d0s:
                    if x not in visto:
                        return None
                    if dia[x] >= 1.0:
                        return k
                    k += 1; x -= dt.timedelta(days=1)
                return None

            def resumo(vals):
                v = sorted(x for x in vals if x is not None)
                return (len(v), v[len(v) // 2] if v else "", round(sum(1 for x in v if x >= 7) / len(v), 3) if v else "")

            ch = resumo(secos(r["d0"]) for r in no_bairro if d0s < r["d0"] <= d1s)
            base = resumo(secos(d0s + dt.timedelta(days=i)) for i in range(1, (d1s - d0s).days + 1))
            lin_seco.append(dict(ponto=slug, escopo="bairro", periodo=f"{d0s}..{d1s}", chamados=ch[0], mediana_dias_secos_chamados=ch[1],
                                 frac_chamados_apos_7_dias_secos=ch[2], dias_base=base[0], mediana_dias_secos_base=base[1],
                                 frac_dias_base_apos_7_dias_secos=base[2]))

    for nome, linhas in (("casamento", lin_cas), ("lambda_por_ponto", lin_lam), ("atendimento_km", lin_km),
                         ("reentupimento", lin_re), ("sazonalidade", lin_saz), ("dias_secos", lin_seco)):
        cols: list[str] = []
        for l in linhas:
            cols += [k for k in l if k not in cols]
        with (SAIDA / f"{nome}.csv").open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n"); w.writeheader()
            for l in linhas:
                w.writerow({k: ("" if l.get(k) is None else l.get(k)) for k in cols})
    (SAIDA / "parametros.json").write_text(json.dumps(params, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(params, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
