"""Trava da área de contribuição (ADR 0027, emenda A, aprovada em 2026-11-21).

Regra: se A(snap 20 m) / A(snap 5 m) > 10, o ponto fica SEM_DADO (TERRENO_EM_REVISAO). Por que 10:
docs/PROPOSTA-REGRA-AREA-CONTRIBUICAO.md, seção 3(a). Igual para todos os pontos; não olha rótulo.

    python3 -I tools/terreno/trava_area.py <ponto_terreno.csv>     -> imprime os slugs travados
A lista `mapi.alerta.flood.terrain-review` da API e o status do terreno.json da IA seguem esta saída
(mais os pontos com posição em revisão, como JABOATAO_CENTRO).
"""
import csv
import sys

N_MAX = 10.0


def instavel(a5, a20, n_max=N_MAX):
    """True se a área muda mais de n_max vezes entre os raios de 5 e 20 m (ou se A5 não existe)."""
    if a5 is None or a20 is None:
        return False
    if a5 <= 0:
        return True
    return a20 / a5 > n_max


def travados(caminho):
    out = []
    for r in csv.DictReader(open(caminho, encoding="utf-8")):
        a5, a20 = r.get("area_contribuinte_m2_snap5m"), r.get("area_contribuinte_m2_snap20m")
        if a5 and a20 and instavel(float(a5), float(a20)):
            out.append((r["slug"], round(float(a20) / float(a5), 1) if float(a5) > 0 else None))
    return out


if __name__ == "__main__":
    for slug, razao in travados(sys.argv[1]):
        print(slug, razao)
