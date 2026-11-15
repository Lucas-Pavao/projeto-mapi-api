#!/usr/bin/env python3
"""
Tabela de eventos de chuva de 5 anos, três fontes, para a tela /chuva do front (JSON estático).

Por quê: calcular os eventos de 5 anos de 5 pontos a cada abertura da tela leva mais de 1,5 min na API
(medido em 2026-11-16, 4 consultas simultâneas). Os eventos de um período fechado não mudam; o front lê este JSON.

Entrada: o export de GET /api/analise/eventos-chuva e /resumo (CSV + manifest), por exemplo
MAPI/exports/eventos-chuva-2026-11-09-cemaden-ped/. O SHA-256 de cada CSV vai no JSON e é conferido contra
SHA256SUMS.txt do export.
Uso: python3 scripts/eventos-chuva-json.py <dir-do-export> <saida.json>
"""
import csv
import hashlib
import json
import sys
from pathlib import Path

SOURCES = [("CEMADEN_PED", "CEMADEN_PED"), ("ANA", "ANA_25KM"), ("OPEN_METEO_ARCHIVE", "REANALISE")]


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def num(v):
    return None if v in ("", None) else float(v)


def main(src: Path, out: Path) -> None:
    sums = {}
    sums_file = src / "SHA256SUMS.txt"
    if sums_file.exists():
        for line in sums_file.read_text().splitlines():
            h, name = line.split()[:2]
            sums[name.lstrip("*")] = h
    result = {"generatedFrom": src.name, "attribution": "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC", "sources": {}}
    for source, tag in SOURCES:
        ev_path, rs_path, mf_path = src / f"eventos_{tag}.csv", src / f"resumo_{tag}.csv", src / f"eventos_{tag}.manifest.json"
        for p in (ev_path, rs_path):
            if p.name in sums and sums[p.name] != sha(p):
                raise SystemExit(f"SHA-256 não confere com SHA256SUMS.txt: {p.name}")
        mf = json.loads(mf_path.read_text())
        events = []
        with ev_path.open() as f:
            for r in csv.DictReader(f):
                events.append({"slug": r["floodPointSlug"], "startUtc": r["startUtc"], "durationMin": num(r["durationMin"]),
                               "totalMm": num(r["totalMm"]), "maxHourlyMmH": num(r["maxHourlyMmH"]), "type": r["type"],
                               "stationId": r["stationId"] or None, "stationDistanceM": num(r["stationDistanceM"]),
                               "excessOverCapacityMm": num(r["excessOverCapacityMm"]),
                               "hasGap": r["hasGap"] == "true", "truncated": r["truncated"] == "true"})
        summary = []
        with rs_path.open() as f:
            for r in csv.DictReader(f):
                summary.append({"slug": r["floodPointSlug"], "type": r["type"], "count": int(r["count"]),
                                "countPerYear": num(r["countPerYear"]), "meanDurationMin": num(r["meanDurationMin"]),
                                "fractionOfTime": num(r["fractionOfTime"]), "eventsAboveCapacity": num(r["eventsAboveCapacity"]),
                                "coverageFraction": num(r["coverageFraction"])})
        result["sources"][source] = {
            "periodFrom": mf["periodFrom"], "periodTo": mf["periodTo"], "params": mf["params"],
            "methodVersion": mf.get("methodVersion"), "datasetId": mf.get("datasetId"),
            "sha256": {ev_path.name: sha(ev_path), rs_path.name: sha(rs_path)},
            "summary": summary, "events": events,
        }
    out.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    print(f"{out}: " + ", ".join(f"{s} {len(v['events'])} eventos" for s, v in result["sources"].items()))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
