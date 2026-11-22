"""Relatório da Central 156 da EMLURB ANTES de qualquer conta (prompt 3, tarefa 1). Sem rede.

    python3 -I scripts/central-156/relatorio_156.py <pasta_dados_brutos>

Lê <pasta>/<ano>.csv.gz (baixa_156.py) e escreve em docs/extratos/emlurb-156/:
  - colunas_por_ano.csv: ano, codificação, separador, colunas, linhas;
  - servicos_por_ano.csv: grupo, serviço, ano, registros (CIDADE INTEIRA). Serviços de alagamento ficam fora (lacrados);
  - qualidade_por_ano.csv: por ano, % com coordenada, % com número, situações e quantas têm DATA_ULT_SITUACAO > DATA_DEMANDA,
    só para os grupos de drenagem;
  - lacrado/ALAGAMENTO_IDS.txt: um id por linha (SHA-256 da linha bruta normalizada, ver id_linha) dos chamados cujo serviço
    tem "ALAGA" ou "INUNDA" na descrição; mais lacrado/ALAGAMENTO_IDS.sha256 (hash da lista) e lacrado/LEIA-ME.md.
    Nenhuma contagem por lugar, bairro ou data desses chamados é calculada ou impressa; o relatório mostra só o total.
A base não tem identificador de chamado: o id é o hash da linha. Duas linhas idênticas viram o mesmo id (contadas à parte).
"""
from __future__ import annotations

import collections
import csv
import gzip
import hashlib
import io
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
EXTR = RAIZ / "docs" / "extratos" / "emlurb-156"
LACRE = re.compile(r"ALAGA|INUNDA", re.I)
GRUPOS_DRENAGEM = re.compile(r"DRENAGEM", re.I)  # DRENAGEM e MACRODRENAGEM
# em 2012 a drenagem estava no grupo CONSERVACAO: o serviço também conta pelo nome
SERV_DRENAGEM = re.compile(r"GAL|CANALET|CANAL\b|SARJET|VALETA|TAMPA", re.I)


def le(path: Path):
    bruto = gzip.open(path, "rb").read()
    for enc in ("utf-8-sig", "cp1252"):
        try:
            txt = bruto.decode(enc); break
        except UnicodeDecodeError:
            continue
    primeira = txt.split("\n", 1)[0]
    sep = ";" if primeira.count(";") >= primeira.count(",") else ","
    rd = csv.reader(io.StringIO(txt, newline=""), delimiter=sep)
    cab = [c.strip() for c in next(rd)]
    linhas = [[c.strip() for c in r] for r in rd if r]
    return enc, sep, cab, linhas


def id_linha(ano: str, r: list[str]) -> str:
    return hashlib.sha256((ano + "|" + "|".join(r)).encode("utf-8")).hexdigest()


def main() -> None:
    brutos = Path(sys.argv[1])
    (EXTR / "lacrado").mkdir(parents=True, exist_ok=True)
    col_rows, serv = [], collections.Counter()
    sc, nomes = collections.Counter(), {}
    qual_rows = []
    lacrados: list[str] = []
    n_lacrado_por_servico = collections.Counter()
    for gz in sorted(brutos.glob("*.csv.gz")):
        ano = gz.name[:4]
        enc, sep, cab, linhas = le(gz)
        ix = {c.upper(): i for i, c in enumerate(cab)}
        col_rows.append([ano, enc, sep, len(linhas), ";".join(cab)])
        g, s = ix["GRUPOSERVICO_DESCRICAO"], ix["SERVICO_DESCRICAO"]
        sit, d0, d1 = ix["SITUACAO"], ix["DATA_DEMANDA"], ix["DATA_ULT_SITUACAO"]
        lat = ix.get("LATITUDE"); num = ix.get("NUMERO")
        dren = collections.Counter(); sits = collections.Counter(); com_coord = com_num = depois = 0
        for r in linhas:
            if len(r) != len(cab):
                serv[("(linha malformada)", "", ano)] += 1
                continue
            if LACRE.search(r[s]) or LACRE.search(r[g]):
                lacrados.append(id_linha(ano, r))
                n_lacrado_por_servico[(r[g], r[s])] += 1
                continue
            serv[(r[g], r[s], ano)] += 1
            sc[(r[ix["GRUPOSERVICO_CODIGO"]], r[ix["SERVICO_CODIGO"]], ano)] += 1
            nomes.setdefault((r[ix["GRUPOSERVICO_CODIGO"]], r[ix["SERVICO_CODIGO"]]), collections.Counter())[(r[g], r[s])] += 1
            if GRUPOS_DRENAGEM.search(r[g]) or SERV_DRENAGEM.search(r[s]):
                dren[r[s]] += 1
                sits[r[sit]] += 1
                if lat is not None and r[lat]:
                    com_coord += 1
                if num is not None and r[num]:
                    com_num += 1
                if r[d1] and r[d0] and r[d1] > r[d0]:
                    depois += 1
        nd = sum(dren.values())
        qual_rows.append([ano, nd, f"{100 * com_coord / nd:.0f}" if nd else "", f"{100 * com_num / nd:.0f}" if nd else "",
                          f"{100 * depois / nd:.0f}" if nd else "",
                          "; ".join(f"{k}={v}" for k, v in sits.most_common())])
        print(ano, enc, repr(sep), len(linhas), "drenagem:", nd, flush=True)
    with (EXTR / "colunas_por_ano.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["ano", "codificacao", "separador", "linhas", "colunas"]); w.writerows(col_rows)
    anos = sorted({k[2] for k in serv})
    chaves = sorted({(k[0], k[1]) for k in serv})
    with (EXTR / "servicos_por_ano.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["grupo", "servico"] + anos + ["total"])
        for gk, sk in chaves:
            v = [serv.get((gk, sk, a), 0) for a in anos]
            w.writerow([gk, sk] + v + [sum(v)])
    # por código (grupo, serviço): junta as grafias diferentes do mesmo serviço; só os de drenagem
    anos_c = sorted({k[2] for k in sc})
    with (EXTR / "servicos_drenagem_por_codigo.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["grupo_codigo", "servico_codigo", "grupo_mais_frequente", "servico_mais_frequente", "grafias"] + anos_c + ["total"])
        for gc, scod in sorted({(k[0], k[1]) for k in sc}, key=lambda t: (int(t[0]) if t[0].isdigit() else 999, int(t[1]) if t[1].isdigit() else 999)):
            (gn, sn), _ = nomes[(gc, scod)].most_common(1)[0]
            if not (GRUPOS_DRENAGEM.search(gn) or SERV_DRENAGEM.search(sn)):
                continue
            v = [sc.get((gc, scod, a), 0) for a in anos_c]
            w.writerow([gc, scod, gn, sn, len(nomes[(gc, scod)])] + v + [sum(v)])
    with (EXTR / "qualidade_drenagem_por_ano.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["ano", "registros_grupos_drenagem", "pct_com_coordenada", "pct_com_numero", "pct_data_ult_situacao_depois_da_abertura", "situacoes"])
        w.writerows(qual_rows)
    lista = "\n".join(sorted(lacrados)) + "\n"
    (EXTR / "lacrado" / "ALAGAMENTO_IDS.txt").write_text(lista, encoding="utf-8")
    sha = hashlib.sha256(lista.encode("utf-8")).hexdigest()
    (EXTR / "lacrado" / "ALAGAMENTO_IDS.sha256").write_text(f"{sha}  ALAGAMENTO_IDS.txt\n", encoding="utf-8")
    (EXTR / "lacrado" / "LEIA-ME.md").write_text(
        "# Chamados de alagamento da Central 156 (lacrados)\n\n"
        "Gerado por `scripts/central-156/relatorio_156.py`. Critério: descrição do grupo ou do serviço com `ALAGA` ou `INUNDA`.\n"
        f"Total de linhas lacradas (2012–2026, cidade inteira): **{len(lacrados)}**; ids distintos: {len(set(lacrados))}.\n"
        f"SHA-256 da lista: `{sha}`.\n\n"
        "Serviços lacrados (nome e total na cidade, sem ano, lugar ou data):\n\n"
        + "".join(f"- {gk} / {sk}: {n}\n" for (gk, sk), n in sorted(n_lacrado_por_servico.items()))
        + "\nRegra (prompt 3): estes chamados **não entram** na estimativa de λ, μ nem da perda de capacidade, e ninguém olha\n"
        "contagem por ponto, bairro ou dia antes de a validação ser pré-registrada. Podem virar o rótulo com hora e endereço da\n"
        "validação. O id é `sha256(ano + '|' + campos da linha separados por '|')`, com os campos sem espaços nas pontas.\n",
        encoding="utf-8")
    print("lacrados:", len(lacrados), "sha", sha[:12])


if __name__ == "__main__":
    main()
