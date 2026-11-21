"""Testes contra o caso à mão do pré-registro (seção 1).   python3 -I -m unittest discover -s scripts/capacidade-galeria"""
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from capacidade import capacidade_ponto, fundo, manning_plena, para_mm_h, vertedor_guia  # noqa: E402


class TesteCapacidade(unittest.TestCase):
    def test_manning_caso_a_mao(self):
        # D = 0,6 m; S = 0,005; n = 0,015 -> (0,312/0,015) * 0,6^(8/3) * 0,005^0,5
        esperado = 20.8 * 0.6 ** (8 / 3) * math.sqrt(0.005)
        self.assertAlmostEqual(manning_plena(0.6, 0.005, 0.015), esperado, places=10)
        self.assertAlmostEqual(manning_plena(0.6, 0.005, 0.015), 0.3767, places=4)

    def test_manning_escala_com_diametro(self):
        # dobrar o diâmetro multiplica a vazão por 2^(8/3) = 6,35 (HEC-22, p. 7-3)
        self.assertAlmostEqual(manning_plena(1.2, 0.005, 0.015) / manning_plena(0.6, 0.005, 0.015), 6.35, places=2)

    def test_vertedor_guia(self):
        self.assertAlmostEqual(vertedor_guia(1.0, 0.15), 1.60 * 0.15 ** 1.5, places=12)
        self.assertAlmostEqual(vertedor_guia(1.0, 0.15), 0.0930, places=4)

    def test_conversao_mm_h(self):
        # Q = 0,3 m³/s, C = 0,86, A = 10.000 m² -> 125,6 mm/h
        self.assertAlmostEqual(para_mm_h(0.3, 10000.0, 0.86), 0.3 / 8600 * 3.6e6, places=9)
        self.assertAlmostEqual(para_mm_h(0.3, 10000.0, 0.86), 125.58, places=2)

    def test_minimo_e_sem_dado(self):
        self.assertEqual(capacidade_ponto(0.2, 0.5), 0.2)
        self.assertEqual(capacidade_ponto(0.9, 0.5), 0.5)
        self.assertIsNone(capacidade_ponto(None, 0.5))
        self.assertIsNone(capacidade_ponto(0.2, None))

    def test_fundo(self):
        self.assertEqual(fundo({"Z": 1.2, "VLHFUNDO": 1.0, "COTA_FUNDO": 0.2})[1], "COTA_FUNDO")
        f, o = fundo({"Z": 1.399, "VLHFUNDO": 0.95, "COTA_FUNDO": -93.601})  # OBJECTID 3352
        self.assertAlmostEqual(f, 0.449, places=3)
        self.assertIn("implausível", o)
        self.assertEqual(fundo({"Z": 1.0, "VLHFUNDO": None, "COTA_FUNDO": None}), (None, "sem cota de fundo"))


if __name__ == "__main__":
    unittest.main()
