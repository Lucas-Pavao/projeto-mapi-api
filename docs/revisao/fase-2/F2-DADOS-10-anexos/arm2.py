import csv, json, urllib.request, datetime as dt, os, sys
from collections import defaultdict
D = os.path.dirname(os.path.abspath(__file__))
ST = {"39084000": (-7.5428, -35.0686), "39167000": (-8.12, -35.3211), "39187800": (-7.9986, -35.0392), "39370100": (-8.3697, -35.1422)}


def om(code):
    p = f"{D}/om_{code}.json"
    if not os.path.exists(p):
        lat, lon = ST[code]
        u = (f"https://archive-api.open-meteo.com/v1/archive?latitude={lat}&longitude={lon}&start_date=2021-09-30&end_date=2026-10-10"
             f"&hourly=precipitation&timezone=GMT")
        open(p, "wb").write(urllib.request.urlopen(u, timeout=120).read())
    j = json.load(open(p))
    assert j.get("utc_offset_seconds", 0) == 0
    return {dt.datetime.fromisoformat(t): v for t, v in zip(j["hourly"]["time"], j["hourly"]["precipitation"])}


def ana():
    r = defaultdict(dict)
    for sid, ts, v in csv.reader(open(f"{D}/ana_rain.csv")):
        if v == "":
            continue
        r[sid][dt.datetime.fromisoformat(ts)] = float(v)
    return r


def hourly(readings, h):
    # rotulo = UTC + h ; UTC = rotulo - h ; hora UTC rotulada pelo fim (H-1h, H]; >=3 de 4 leituras
    acc = defaultdict(list)
    for lab, v in readings.items():
        u = lab - dt.timedelta(hours=h)
        end = (u - dt.timedelta(seconds=1)).replace(minute=0, second=0, microsecond=0) + dt.timedelta(hours=1)
        acc[end].append(v)
    return {k: sum(v) for k, v in acc.items() if len(v) >= 3}


def rank(x):
    idx = sorted(range(len(x)), key=lambda i: x[i])
    r = [0.0] * len(x)
    i = 0
    while i < len(idx):
        j = i
        while j + 1 < len(idx) and x[idx[j + 1]] == x[idx[i]]:
            j += 1
        for k in range(i, j + 1):
            r[idx[k]] = (i + j) / 2 + 1
        i = j + 1
    return r


def pearson(a, b):
    n = len(a)
    ma, mb = sum(a) / n, sum(b) / n
    sa = sum((x - ma) ** 2 for x in a) ** .5
    sb = sum((y - mb) ** 2 for y in b) ** .5
    if sa == 0 or sb == 0:
        return float("nan")
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb)


def spearman(a, b):
    return pearson(rank(a), rank(b))


def main():
    A = ana()
    out = {}
    for code in ST:
        ref = om(code)
        rd = A[code]
        byh = {h: hourly(rd, h) for h in range(-6, 7)}
        years = sorted({k.year for k in byh[-3]})
        out[code] = {}
        for y in years:
            row = {}
            for h in range(-6, 7):
                common = [k for k in byh[h] if k.year == y and k in ref and ref[k] is not None]
                a = [byh[h][k] for k in common]
                b = [ref[k] for k in common]
                wet = [k for k in common if ref[k] >= 0.1]
                days = {k.date() for k in wet}
                row[h] = {"rho": spearman(a, b) if len(a) > 30 else float("nan"), "n": len(a), "wet": len(wet), "wetdays": len(days)}
            out[code][y] = row
    json.dump({c: {str(y): {str(h): v for h, v in r.items()} for y, r in d.items()} for c, d in out.items()}, open(f"{D}/arm2_raw.json", "w"), indent=1)
    # decisao
    qual = conf = 0
    per = {}
    lines = []
    for code, d in out.items():
        q = c = 0
        for y, row in d.items():
            r0 = row[0]
            if not (r0["wet"] >= 50 and r0["wetdays"] >= 5):
                lines.append(f"{code} {y}: sem poder (wet={r0['wet']}, dias={r0['wetdays']})")
                continue
            rho = {h: row[h]["rho"] for h in row}
            best_h = max(rho, key=lambda h: rho[h])
            a = rho[-3] - rho[0] >= 0.03
            b = rho[-3] >= rho[best_h] - 0.03
            q += 1
            c += (a and b)
            lines.append(f"{code} {y}: wet={r0['wet']} dias={r0['wetdays']} rho(-3)={rho[-3]:.3f} rho(-4)={rho[-4]:.3f} rho(0)={rho[0]:.3f} best={best_h}({rho[best_h]:.3f}) a={a} b={b} {'CONFORME' if a and b else 'NAO'}")
        per[code] = (q, c)
        qual += q
        conf += c
    print("\n".join(lines))
    print(per, "total", qual, conf, (conf / qual if qual else None))
    ok_total = qual >= 8 and conf / qual >= 0.8
    ok_st = all(q >= 3 and c / q >= 0.6 for q, c in per.values())
    print("BRACO2", "APROVADO" if ok_total and ok_st else "REJECTED/INSUFFICIENT", ok_total, ok_st)


main()
