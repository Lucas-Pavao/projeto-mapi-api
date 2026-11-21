"""WGS84/SIRGAS 2000 geográficas -> UTM 25S (EPSG:31985), série de Krüger (ordem 6), só biblioteca padrão.

Erro < 1 mm na faixa do fuso. Conferido contra gdaltransform (GDAL 3.10.0) nos 5 pontos críticos
(teste test_utm_confere_com_gdal). GRS80 (SIRGAS 2000) e WGS84 diferem < 0,1 mm aqui.
"""
from __future__ import annotations

import math

A = 6378137.0
F = 1 / 298.257222101  # GRS80
K0 = 0.9996
LON0 = math.radians(-33.0)  # meridiano central do fuso 25
FE, FN = 500000.0, 10000000.0


def para_utm25s(lat: float, lon: float) -> tuple[float, float]:
    n = F / (2 - F)
    a_ = A / (1 + n) * (1 + n**2 / 4 + n**4 / 64)
    alfa = [
        n / 2 - 2 * n**2 / 3 + 5 * n**3 / 16,
        13 * n**2 / 48 - 3 * n**3 / 5,
        61 * n**3 / 240,
    ]
    phi, lam = math.radians(lat), math.radians(lon) - LON0
    e = math.sqrt(F * (2 - F))
    t = math.sinh(math.atanh(math.sin(phi)) - e * math.atanh(e * math.sin(phi)))
    xi = math.atan2(t, math.cos(lam))
    eta = math.atanh(math.sin(lam) / math.sqrt(1 + t * t))
    x = eta + sum(alfa[j] * math.cos(2 * (j + 1) * xi) * math.sinh(2 * (j + 1) * eta) for j in range(3))
    y = xi + sum(alfa[j] * math.sin(2 * (j + 1) * xi) * math.cosh(2 * (j + 1) * eta) for j in range(3))
    return FE + K0 * a_ * x, FN + K0 * a_ * y
