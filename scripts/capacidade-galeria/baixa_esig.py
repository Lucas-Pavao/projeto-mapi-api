"""Baixa do ESIG (EMLURB) os elementos de drenagem a até 300 m de cada ponto crítico do Recife.

Uso (rede só aqui; o cálculo da capacidade lê os arquivos salvos e não acessa a rede):

    python3 -I scripts/capacidade-galeria/baixa_esig.py

Saída em docs/extratos/esig-drenagem/:
  - geojson/<PONTO>__<SERVICO>_c<camada>.geojson  (resposta bruta da consulta, páginas juntadas)
  - geojson/MANIFESTO.csv  (arquivo, ponto, serviço, camada, registros, páginas, SHA-256, URL, data/hora UTC)

Consulta: geometria = ponto (WGS84, inSR=4326), distance=300, units=esriSRUnit_Meter,
outSR=31985, outFields=*, f=geojson, paginada por resultOffset até não haver
exceededTransferLimit. Só biblioteca padrão.
"""
from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
PONTOS_CSV = RAIZ / "tools" / "terreno" / "pontos_criticos.csv"
SAIDA = RAIZ / "docs" / "extratos" / "esig-drenagem" / "geojson"
BASE = "https://esigportal2.recife.pe.gov.br/arcgis/rest/services/Emlurb"
SERVICOS = {
    "MI": ("BASES_DRENAGEM_RECIFE_MI", [0, 1, 2], 8000),
    "MA": ("BASES_DRENAGEM_RECIFE_MA", [0, 1, 2, 4], 2000),
}
PONTOS_RECIFE = ["AV_RECIFE_IBURA", "CIN_UFPE", "AGAMENON_DERBY", "MASCARENHAS_IMBIRIBEIRA"]
RAIO_M = 300


def get_json(url: str) -> dict:
    for tentativa in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "mapi-pesquisa/1.0"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # rede instável: espera e tenta de novo
            if tentativa == 3:
                raise
            print(f"  falhou ({e}); nova tentativa", file=sys.stderr)
            time.sleep(5 * (tentativa + 1))
    raise RuntimeError("inalcançável")


def consulta(servico: str, camada: int, lat: float, lon: float, page: int) -> tuple[dict, int, list[str]]:
    feats: list[dict] = []
    urls: list[str] = []
    offset = 0
    paginas = 0
    primeira: dict | None = None
    while True:
        params = {
            "geometry": f"{lon},{lat}",
            "geometryType": "esriGeometryPoint",
            "inSR": "4326",
            "spatialRel": "esriSpatialRelIntersects",
            "distance": str(RAIO_M),
            "units": "esriSRUnit_Meter",
            "outFields": "*",
            "returnGeometry": "true",
            "outSR": "31985",
            "orderByFields": "OBJECTID",
            "resultOffset": str(offset),
            "resultRecordCount": str(page),
            "f": "geojson",
        }
        url = f"{BASE}/{servico}/MapServer/{camada}/query?" + urllib.parse.urlencode(params)
        urls.append(url)
        d = get_json(url)
        if "error" in d:
            raise RuntimeError(f"{servico}/{camada}: {d['error']}")
        paginas += 1
        if primeira is None:
            primeira = d
        lote = d.get("features", [])
        feats.extend(lote)
        excedeu = d.get("exceededTransferLimit") or d.get("properties", {}).get("exceededTransferLimit")
        if not excedeu or not lote:
            break
        offset += len(lote)
    assert primeira is not None
    primeira = dict(primeira)
    primeira["features"] = feats
    primeira.pop("exceededTransferLimit", None)
    return primeira, paginas, urls


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    with PONTOS_CSV.open(newline="", encoding="utf-8") as f:
        pontos = {r["slug"]: r for r in csv.DictReader(f)}
    linhas = []
    for slug in PONTOS_RECIFE:
        p = pontos[slug]
        lat, lon = float(p["latitude"]), float(p["longitude"])
        for sigla, (servico, camadas, page) in SERVICOS.items():
            for c in camadas:
                agora = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                d, paginas, urls = consulta(servico, c, lat, lon, page)
                nome = f"{slug}__{sigla}_c{c}.geojson"
                dados = json.dumps(d, ensure_ascii=False, sort_keys=True, indent=1).encode("utf-8")
                (SAIDA / nome).write_bytes(dados)
                sha = hashlib.sha256(dados).hexdigest()
                n = len(d["features"])
                print(f"{nome}: {n} registros, {paginas} página(s)")
                linhas.append([nome, slug, servico, c, n, paginas, sha, urls[0], agora])
    with (SAIDA / "MANIFESTO.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["arquivo", "ponto", "servico", "camada", "registros", "paginas", "sha256", "url_primeira_pagina", "baixado_utc"])
        w.writerows(linhas)


if __name__ == "__main__":
    main()
