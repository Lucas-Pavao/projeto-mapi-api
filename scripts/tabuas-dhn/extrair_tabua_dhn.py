#!/usr/bin/env python3
"""Extrai os extremos (preamar/baixa-mar) da tábua de maré anual da DHN/CHM (PDF) para CSV.

Uso: python3 -I extrair_tabua_dhn.py <ano> <arquivo.pdf> <saida.csv>

Requer `pdftotext` (poppler-utils). Lê as posições das palavras (-bbox): em cada meia-coluna a tábua traz o número do
dia à esquerda e, empilhados, até 4 pares HORA (HHMM, hora local da tábua, UTC-3) e ALT (m, NR da DHN). A extração é
conferida: todos os dias do ano devem existir, 2 a 4 extremos por dia, horários crescentes no dia. Qualquer falha
interrompe com erro (nada é completado ou inventado).
"""
import calendar
import csv
import hashlib
import re
import subprocess
import sys
from collections import defaultdict

MESES = ["Janeiro", "Fevereiro", "Março", "Marco", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro",
         "Outubro", "Novembro", "Dezembro"]
WORD = re.compile(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>')


def paginas(pdf):
    html = subprocess.run(["pdftotext", "-bbox", pdf, "-"], check=True, capture_output=True, text=True).stdout
    for pg in html.split("<page ")[1:]:
        yield [(float(a), float(b), float(c), float(d), t) for a, b, c, d, t in WORD.findall(pg)]


def extrair(ano, pdf):
    saida = []
    mes_base = 0
    for words in paginas(pdf):
        meses = [t for x, y, _, _, t in words if t in MESES]
        if not meses:
            continue
        hdr_y = min(y for x, y, _, _, t in words if t == "HORA")
        cols = sorted(x for x, y, _, _, t in words if t == "HORA" and abs(y - hdr_y) < 1)
        if len(cols) != 8 or len(meses) != 4:
            raise SystemExit(f"página com {len(cols)} colunas e {len(meses)} meses (esperado 8 e 4)")
        # cada mês ocupa 2 meia-colunas consecutivas
        fronteiras = cols + [10_000]
        for h, xc in enumerate(cols):
            mes = mes_base + h // 2 + 1
            x_lo, x_hi = xc - 20, fronteiras[h + 1] - 20
            # número do dia: à esquerda da coluna HORA (x < xc) e à direita da fronteira anterior
            dias = [(y, int(t)) for x, y, _, _, t in words
                    if y > hdr_y + 5 and x_lo <= x < xc and re.fullmatch(r"\d{2}", t) and int(t) <= 31]
            horas = [(x, y, t) for x, y, _, _, t in words
                     if y > hdr_y + 5 and xc <= x < x_hi and re.fullmatch(r"\d{4}", t)]
            alts = [(x, y, t) for x, y, _, _, t in words
                    if y > hdr_y + 5 and xc <= x < x_hi and re.fullmatch(r"-?\d\.\d{1,2}", t)]
            por_dia = defaultdict(list)
            for x, y, t in horas:
                a = [al for al in alts if abs(al[1] - y) < 1.5 and al[0] > x]
                if len(a) != 1:
                    raise SystemExit(f"hora {t} (mês {mes}) sem altura única")
                dia = max((d for d in dias if d[0] <= y + 3), key=lambda d: d[0])[1]  # bloco do dia: rótulo no topo
                por_dia[dia].append((t, float(a[0][2]), y))
            for dia, itens in por_dia.items():
                itens.sort(key=lambda i: i[2])
                for t, alt, _ in itens:
                    saida.append((ano, mes, dia, f"{t[:2]}:{t[2:]}", alt))
        mes_base += 4
    saida.sort()
    return saida


def conferir(ano, linhas):
    por_dia = defaultdict(list)
    for a, m, d, hm, alt in linhas:
        por_dia[(m, d)].append((hm, alt))
    esperado = {(m, d) for m in range(1, 13) for d in range(1, calendar.monthrange(ano, m)[1] + 1)}
    if set(por_dia) != esperado:
        raise SystemExit(f"dias faltando/sobrando: {sorted(esperado ^ set(por_dia))}")
    for k, v in por_dia.items():
        hs = [h for h, _ in v]
        if not 2 <= len(v) <= 4 or hs != sorted(hs) or len(set(hs)) != len(hs):
            raise SystemExit(f"dia {k} inconsistente: {v}")
    return len(linhas)


def main():
    ano, pdf, out = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    linhas = extrair(ano, pdf)
    n = conferir(ano, linhas)
    sha = hashlib.sha256(open(pdf, "rb").read()).hexdigest()
    with open(out, "w", newline="", encoding="utf-8") as f:
        f.write(f"# fonte: tabua de mare DHN/CHM, Porto do Recife (carta 902, pe02), {ano}; pdf sha256={sha}\n")
        f.write("# data_local,hora_local,altura_m (hora em UTC-3 como na tabua; altura acima do NR da DHN)\n")
        w = csv.writer(f, lineterminator="\n")
        for a, m, d, hm, alt in linhas:
            w.writerow([f"{a:04d}-{m:02d}-{d:02d}", hm, f"{alt:g}"])
    print(f"{ano}: {n} extremos, sha256={sha}")


if __name__ == "__main__":
    main()
