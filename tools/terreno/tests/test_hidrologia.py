"""Testes unitários da hidrologia de terreno.py em DEMs sintéticos com resposta conhecida (F3-GEO-07).

Rodar dentro do container GDAL: tools/terreno/run.sh testes
"""
import os, sys
import numpy as np
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import terreno as T  # noqa: E402

ND = T.ND


def pad(a):
    """Anel de nodata ao redor (como cmd_atributos faz): as bordas do DEM viram saídas."""
    a = np.asarray(a, dtype=np.float64)
    out = np.full((a.shape[0] + 2, a.shape[1] + 2), ND)
    out[1:-1, 1:-1] = a
    return out


def roda(a):
    d = pad(a)
    fill, par, ordem, acc = T.hidrologia_d8(d, ND)
    return d, fill, par, ordem, np.array(acc).reshape(d.shape)


def inner(x):
    return x[1:-1, 1:-1]


def alcanca_saida(par, i, limite):
    for _ in range(limite):
        if par[i] == -1:
            return True
        if par[i] < 0:
            return False
        i = par[i]
    return False


def test_cone_sem_depressao_e_acumulacao_conservada():
    n = 11; c = n // 2
    yy, xx = np.mgrid[0:n, 0:n]
    cone = 10.0 - np.hypot(yy - c, xx - c)           # pico no centro, desce em todas as direções
    d, fill, par, ordem, acc = roda(cone)
    assert np.array_equal(inner(fill), cone)          # nada a preencher
    assert acc[1 + c, 1 + c] == 1                     # o topo só contém a si mesmo
    saidas = [i for i in ordem if par[i] == -1]
    assert sum(acc.ravel()[i] for i in saidas) == n * n   # toda célula chega a uma saída
    # simetria: os 4 eixos (N,S,L,O) recebem o mesmo
    assert acc[1, 1 + c] == acc[n, 1 + c] == acc[1 + c, 1] == acc[1 + c, n]


def test_plano_inclinado_escoa_para_o_lado_baixo():
    h, w = 7, 9
    plano = np.tile(np.arange(w, dtype=float), (h, 1)) * 0.1   # sobe para leste; água vai para oeste
    d, fill, par, ordem, acc = roda(plano)
    assert np.array_equal(inner(fill), plano)
    # linhas de borda da janela são saídas por construção (vizinhas do anel nodata); só as internas valem
    oeste = acc[2:-2, 1]
    # D8 de máxima declividade: cada linha interna escoa pela própria linha (reta > diagonal); a célula leste da
    # linha é saída de borda por si só, então a saída oeste recebe as outras w-1
    assert list(oeste) == [w - 1] * (h - 2)
    assert (acc[1:-1, w] == 1).all()                           # coluna leste (alta) não recebe de ninguém


def test_poco_unico_profundidade_e_nivel_de_transbordo():
    dem = np.full((9, 9), 10.0)
    dem[3:6, 3:6] = 8.0           # anel de 8 m ao redor do poço
    dem[4, 4] = 5.0               # poço
    dem[4, 0:3] = 7.0             # canal de saída a oeste, cota 7: é o menor transbordo
    dem[4, 3] = 7.0
    d, fill, par, ordem, acc = roda(dem)
    assert fill[1 + 4, 1 + 4] == 7.0                          # enche até o menor transbordo
    assert fill[1 + 4, 1 + 4] - d[1 + 4, 1 + 4] == pytest.approx(2.0)
    assert (inner(fill) >= dem).all()                         # fill nunca abaixa a cota
    assert alcanca_saida(par, (1 + 4) * d.shape[1] + 1 + 4, d.size)


def test_poco_fechado_profundidade_ate_a_borda():
    dem = np.full((7, 7), 10.0); dem[3, 3] = 2.0
    d, fill, *_ = roda(dem)
    assert fill[4, 4] - d[4, 4] == pytest.approx(8.0)


def test_duas_bacias_separadas_por_divisor():
    h, w = 5, 11
    c = w // 2
    dem = np.tile(10.0 - np.abs(np.arange(w) - c), (h, 1))     # cume na coluna central
    d, fill, par, ordem, acc = roda(dem)
    assert np.array_equal(inner(fill), dem)
    ex = (1 + 2) * d.shape[1] + 1                              # exutório oeste, linha do meio
    bacia = T.bacia_do_exutorio(par, ordem, d.size, ex).reshape(d.shape)
    assert bacia[1 + 2, 1 + 1]                                 # vizinho a leste pertence
    assert not bacia[1 + 2, w]                                 # extremo leste, outra bacia
    assert not bacia[:, 1 + c + 1:].any()                      # nada além do cume
    # as duas bordas (oeste e leste) recebem tudo: cume (coluna c) drena para um dos lados
    for r in range(1, h - 1):                                  # linhas internas: 11 células, nenhuma cruza o cume
        assert acc[1 + r, 1] + acc[1 + r, w] == w
        assert acc[1 + r, 1] == c                              # colunas 0..4 escoam para oeste
        assert acc[1 + r, w] == c + 1                          # cume (empate) desempata para leste (primeira direção de DIRS)


def test_plato_plano_todas_celulas_drenam():
    dem = np.full((8, 8), 3.0)
    d, fill, par, ordem, acc = roda(dem)
    assert np.array_equal(inner(fill), dem)
    assert (inner(np.array(par).reshape(d.shape)) != -2).all()
    assert len(ordem) == 64
    saidas = [i for i in ordem if par[i] == -1]
    assert sum(acc.ravel()[i] for i in saidas) == 64
    assert all(alcanca_saida(par, i, 100) for i in ordem)       # sem ciclos


def test_plato_dentro_de_depressao_nao_cria_ciclo():
    dem = np.full((9, 9), 9.0); dem[3:6, 3:6] = 4.0             # platô baixo cercado
    d, fill, par, ordem, acc = roda(dem)
    assert (fill[1 + 3:1 + 6, 1 + 3:1 + 6] == 9.0).all()        # sobe até a cota de saída
    assert all(alcanca_saida(par, i, 200) for i in ordem)


def test_nodata_interno_e_saida():
    dem = np.full((5, 5), 5.0); dem[2, 2] = ND                  # lago/lacuna no meio
    dem[2, 1] = 4.0
    d, fill, par, ordem, acc = roda(dem)
    assert fill[1 + 2, 1 + 1] == 4.0
    assert par[(1 + 2) * d.shape[1] + 1 + 1] == -1              # vizinha do nodata é semente/saída
    assert fill[1 + 2, 1 + 2] == ND


def test_d8_ponderacao_diagonal_e_direcao_de_maxima_declividade():
    dem = np.full((5, 5), 20.0)
    dem[2, 2] = 10.0
    dem[2, 3] = 9.0     # leste: queda 1,0 / 1 = 1,0
    dem[3, 3] = 8.7     # sudeste: queda 1,3 / 1,414 = 0,92 -> perde para leste
    d = pad(dem)
    fill, par, ordem, acc = T.hidrologia_d8(d, ND)
    W = d.shape[1]
    assert par[(1 + 2) * W + 1 + 2] == (1 + 2) * W + 1 + 3
    dem[3, 3] = 8.0     # queda 2,0 / 1,414 = 1,41 -> sudeste vence
    d = pad(dem)
    fill, par, ordem, acc = T.hidrologia_d8(d, ND)
    assert par[(1 + 2) * W + 1 + 2] == (1 + 3) * W + 1 + 3
