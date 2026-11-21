"""Baixa o conjunto "Central de Atendimento de Serviços da Emlurb – 156" do CKAN do Recife (ODbL).

    python3 -I scripts/central-156/baixa_156.py <pasta_dados_brutos>

- Lê o package_show do CKAN e baixa, por ano, o recurso CSV mais recente (last_modified); recursos repetidos do mesmo
  ano ficam listados no manifesto como "não baixado (versão mais antiga)".
- Grava cada CSV comprimido (<ano>.csv.gz) na pasta indicada (fora do git: os brutos somam ~290 MB) e calcula o
  SHA-256 do arquivo BRUTO (antes de comprimir) no caminho, sem gravar o bruto.
- Baixa os dicionários (PDF e JSON) para docs/extratos/emlurb-156/ (pequenos, versionados).
- Escreve docs/extratos/emlurb-156/MANIFESTO.csv (recurso, ano, url, bytes, sha256 bruto, last_modified, baixado_utc)
  e package_show.json (metadados do conjunto, com licença).
Só biblioteca padrão.
"""
from __future__ import annotations

import csv
import datetime as dt
import gzip
import hashlib
import json
import re
import sys
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
EXTR = RAIZ / "docs" / "extratos" / "emlurb-156"
API = "https://dados.recife.pe.gov.br/api/3/action/package_show?id=central-de-atendimento-de-servicos-da-emlurb-156"
UA = {"User-Agent": "mapi-pesquisa/1.0"}


def abre(url: str):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300)


def main() -> None:
    brutos = Path(sys.argv[1]).resolve()
    brutos.mkdir(parents=True, exist_ok=True)
    EXTR.mkdir(parents=True, exist_ok=True)
    pkg = json.loads(abre(API).read().decode("utf-8"))
    (EXTR / "package_show.json").write_text(json.dumps(pkg, ensure_ascii=False, indent=1), encoding="utf-8")
    res = pkg["result"]["resources"]
    por_ano: dict[str, list[dict]] = {}
    dic = []
    for r in res:
        if r.get("format", "").upper() == "CSV":
            ano = re.match(r"(\d{4})", r["name"]).group(1)
            por_ano.setdefault(ano, []).append(r)
        else:
            dic.append(r)
    linhas = []
    for ano in sorted(por_ano):
        rs = sorted(por_ano[ano], key=lambda r: r.get("last_modified") or "", reverse=True)
        for k, r in enumerate(rs):
            if k > 0:
                linhas.append([ano, r["id"], r["url"], r.get("size"), "", r.get("last_modified"), "", "não baixado (versão mais antiga do mesmo ano)"])
                continue
            h = hashlib.sha256(); n = 0
            destino = brutos / f"{ano}.csv.gz"
            with abre(r["url"]) as resp, gzip.open(destino, "wb", compresslevel=6) as gz:
                while True:
                    bloco = resp.read(1 << 20)
                    if not bloco:
                        break
                    h.update(bloco); n += len(bloco); gz.write(bloco)
            agora = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            linhas.append([ano, r["id"], r["url"], n, h.hexdigest(), r.get("last_modified"), agora, f"{destino.name}"])
            print(ano, n, h.hexdigest()[:12], flush=True)
    for r in dic:
        nome = Path(r["url"]).name
        dados = abre(r["url"]).read()
        if not (EXTR / nome).exists():
            (EXTR / nome).write_bytes(dados)
        linhas.append(["dicionario", r["id"], r["url"], len(dados), hashlib.sha256(dados).hexdigest(), r.get("last_modified"),
                       dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), nome])
    with (EXTR / "MANIFESTO.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["ano", "resource_id", "url", "bytes_bruto", "sha256_bruto", "last_modified_ckan", "baixado_utc", "arquivo"])
        w.writerows(linhas)


if __name__ == "__main__":
    main()
