package com.projeto.mapi.util;

/**
 * Latitude/longitude (SIRGAS 2000 ≈ WGS 84) para UTM, fórmulas de Snyder (1987, USGS PP 1395, eq. 8-9 a 8-11), elipsoide
 * GRS 80. Usado para achar a célula da grade do MDT do PE3D (EPSG:31985, UTM 25S). Erro da série &lt; 1 cm na faixa do Recife.
 */
public final class UtmConverter {

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257222101;
    private static final double K0 = 0.9996;
    private static final double E2 = F * (2 - F);
    private static final double EP2 = E2 / (1 - E2);

    private UtmConverter() {}

    /** @return {leste, norte} em metros na zona UTM 25 Sul (meridiano central −33°). */
    public static double[] toUtm25S(double latitude, double longitude) {
        return toUtm(latitude, longitude, -33.0, 500_000.0, 10_000_000.0);
    }

    static double[] toUtm(double latitude, double longitude, double centralMeridian, double falseEasting, double falseNorthing) {
        double phi = Math.toRadians(latitude);
        double sin = Math.sin(phi);
        double cos = Math.cos(phi);
        double tan = Math.tan(phi);
        double n = A / Math.sqrt(1 - E2 * sin * sin);
        double t = tan * tan;
        double c = EP2 * cos * cos;
        double a = cos * Math.toRadians(longitude - centralMeridian);
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        double m = A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
                - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi)
                - (35 * e6 / 3072) * Math.sin(6 * phi));
        double x = falseEasting + K0 * n * (a + (1 - t + c) * Math.pow(a, 3) / 6
                + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120);
        double y = falseNorthing + K0 * (m + n * tan * (a * a / 2
                + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720));
        return new double[] {x, y};
    }
}
