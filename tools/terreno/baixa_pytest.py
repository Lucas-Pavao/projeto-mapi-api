#!/usr/bin/env python3
"""Baixa pytest e dependências (wheels puras, versões fixadas) do PyPI para o diretório dado, conferindo o SHA-256
publicado pelo PyPI. Roda no container GDAL (que não tem pip): python3 -I baixa_pytest.py <destino>"""
import hashlib, io, json, sys, urllib.request, zipfile

PACOTES = {"pytest": "9.1.1", "pluggy": "1.6.0", "iniconfig": "2.3.1", "packaging": "26.3", "pygments": "2.21.0"}

for nome, ver in PACOTES.items():
    meta = json.load(urllib.request.urlopen(f"https://pypi.org/pypi/{nome}/{ver}/json"))
    arq = next(u for u in meta["urls"] if u["filename"].endswith("py3-none-any.whl"))
    dados = urllib.request.urlopen(arq["url"]).read()
    assert hashlib.sha256(dados).hexdigest() == arq["digests"]["sha256"], nome
    zipfile.ZipFile(io.BytesIO(dados)).extractall(sys.argv[1])
    print("ok", nome, ver, arq["digests"]["sha256"])
