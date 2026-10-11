import re, json, time, urllib.request, datetime as dt
BASE = "http://telemetriaws1.ana.gov.br/ServiceANA.asmx"


def get(url):
    r = urllib.request.urlopen(url, timeout=40)
    return r.read().decode('utf-8', 'replace'), r.headers.get('Date')


def utcnow():
    return dt.datetime.now(dt.timezone.utc)


def fmt(d):
    return d.strftime("%d/%m/%Y")


def series(code):
    n = utcnow()
    b, d = get(f"{BASE}/DadosHidrometeorologicos?CodEstacao={code}&DataInicio={fmt(n - dt.timedelta(days=1))}&DataFim={fmt(n + dt.timedelta(days=1))}")
    return re.findall(r"<DataHora>([^<]*)</DataHora>", b), d


inv, _ = get(f"{BASE}/ListaEstacoesTelemetricas?statusEstacoes=0&origem=0")
stations = []
for t in re.findall(r"<Table .*?</Table>", inv, re.S):
    uf = re.search(r"<Municipio-UF>([^<]*)", t)
    st = re.search(r"<StatusEstacao>([^<]*)", t)
    c = re.search(r"<CodEstacao>([^<]*)", t)
    if uf and uf.group(1).strip().endswith("-PE") and st and st.group(1).strip() == "Ativo":
        stations.append(c.group(1).strip())
for s in ["39187800", "39370100", "39167000", "39084000"]:
    if s not in stations:
        stations.append(s)
active = []
for s in stations:
    try:
        ls, _ = series(s)
    except Exception:
        continue
    ok = [l for l in ls if abs((dt.datetime.strptime(l.strip(), "%Y-%m-%d %H:%M:%S").replace(tzinfo=dt.timezone.utc) - utcnow()).total_seconds()) <= 30 * 3600]
    if ok:
        active.append(s)
out = open("poll.jsonl", "a")
out.write(json.dumps({"event": "discovery", "t": utcnow().isoformat(), "n_inventory_pe": len(stations), "active": active}) + "\n")
out.flush()
for i in range(36):
    for s in active:
        try:
            ls, d = series(s)
            t = utcnow()
            out.write(json.dumps({"event": "obs", "poll": i, "t_obs": t.isoformat(), "http_date": d, "station": s, "labels": [l.strip() for l in ls]}) + "\n")
        except Exception as e:
            out.write(json.dumps({"event": "err", "poll": i, "station": s, "e": str(e)}) + "\n")
        out.flush()
    time.sleep(300)
out.write(json.dumps({"event": "end", "t": utcnow().isoformat()}) + "\n")
