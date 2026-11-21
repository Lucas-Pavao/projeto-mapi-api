"""Trava da área (ADR 0027, emenda A). Roda com: tools/terreno/run.sh testes"""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from trava_area import instavel  # noqa: E402


def test_valores_dos_5_pontos():
    # áreas de docs/PROPOSTA-REGRA-AREA-CONTRIBUICAO.md, seção 1 (snap 5 e 20 m)
    assert instavel(28, 10880)          # Ibura, 388,6
    assert instavel(26, 14875)          # Derby, 572,1
    assert not instavel(82297, 82487)   # CIn, 1,0
    assert not instavel(145, 161)       # Jaboatão, 1,1
    assert not instavel(9616, 12364)    # Imbiribeira, 1,3


def test_limite_e_casos_de_borda():
    assert not instavel(100, 1000)      # razão 10 exata: não trava (> 10)
    assert instavel(100, 1001)
    assert instavel(0, 5)
    assert not instavel(None, 5)
