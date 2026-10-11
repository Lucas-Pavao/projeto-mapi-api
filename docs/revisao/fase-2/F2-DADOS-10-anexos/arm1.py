import json, os, datetime as dt
from collections import defaultdict
D = os.path.dirname(os.path.abspath(__file__))
first = {}
seen = defaultdict(dict)   # station -> label -> t_first_seen
base = defaultdict(set)
firstpoll = {}
bad_date = 0
for line in open(f"{D}/poll.jsonl"):
    e = json.loads(line)
    if e["event"] != "obs":
        continue
    s = e["station"]
    t = dt.datetime.fromisoformat(e["t_obs"])
    if e.get("http_date"):
        hd = dt.datetime.strptime(e["http_date"], "%a, %d %b %Y %H:%M:%S GMT").replace(tzinfo=dt.timezone.utc)
        if abs((hd - t).total_seconds()) > 60:
            bad_date += 1
            continue
    if s not in firstpoll:
        firstpoll[s] = e["poll"]
        for l in e["labels"]:
            seen[s][l] = t
            base[s].add(l)
        continue
    for l in e["labels"]:
        if l not in seen[s]:
            seen[s][l] = t


def lag(s, l, h):
    lab = dt.datetime.strptime(l, "%Y-%m-%d %H:%M:%S").replace(tzinfo=dt.timezone.utc)
    return (seen[s][l] - (lab - dt.timedelta(hours=h))).total_seconds() / 60


def q(v, p):
    v = sorted(v)
    if not v:
        return float("nan")
    k = (len(v) - 1) * p
    f = int(k)
    c = min(f + 1, len(v) - 1)
    return v[f] + (v[c] - v[f]) * (k - f)


new = {s: [l for l in seen[s] if l not in base[s]] for s in seen}
nnew = sum(len(v) for v in new.values())
nst = sum(1 for v in new.values() if v)
print("estacoes", len(seen), "leituras novas", nnew, "estacoes com novas", nst, "http_date fora de 60s", bad_date)
res = {}
for h in range(-6, 7):
    allv = [lag(s, l, h) for s in seen for l in seen[s]]
    newv = [lag(s, l, h) for s in seen for l in new[s]]
    badst = 0
    for s in seen:
        v = [lag(s, l, h) for l in seen[s]]
        if v and sum(1 for x in v if x < -10) / len(v) > 0.05:
            badst += 1
    v1 = q(allv, .01) >= -10 and badst == 0
    v2 = q(newv, .05) <= 30
    res[h] = {"p01_all": q(allv, .01), "p05_new": q(newv, .05), "med_new": q(newv, .5), "n_all": len(allv), "n_new": len(newv), "st_gt5pct_future": badst, "V1": v1, "V2": v2, "viable": v1 and v2}
    print(h, {k: (round(v, 1) if isinstance(v, float) else v) for k, v in res[h].items()})
viable = [h for h in res if res[h]["viable"]]
power = nnew >= 30 and nst >= 3
print("poder", power, "viaveis", viable)
json.dump({"n_estacoes": len(seen), "n_novas": nnew, "estacoes_com_novas": nst, "res": res, "viaveis": viable}, open(f"{D}/arm1_result.json", "w"), indent=1)
