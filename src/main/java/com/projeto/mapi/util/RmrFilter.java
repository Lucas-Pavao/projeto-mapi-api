package com.projeto.mapi.util;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filtro de estações da Região Metropolitana do Recife (RMR), portado do coletor de
 * referência em Python (src/utils/text_utils.py do projeto-mapi). Usa limite de palavra
 * (\b) para evitar falso positivo, ex.: "Araripina" não deve casar com o termo "Pina".
 */
public final class RmrFilter {

    private static final List<String> RMR_MUNICIPIOS = List.of(
            "RECIFE", "JABOATAO DOS GUARARAPES", "OLINDA", "PAULISTA", "IGARASSU",
            "ABREU E LIMA", "CAMARAGIBE", "CABO DE SANTO AGOSTINHO", "IPOJUCA", "MORENO",
            "SAO LOURENCO DA MATA", "ARACOIABA", "ILHA DE ITAMARACA", "ITAPISSUMA", "GOIANA"
    );

    private static final List<String> RMR_TERMOS_ESPECIAIS = List.of(
            "CASTELO BRANCO", "ENGENHO VELHO", "CURADO", "VILA NATAL", "ALTO DA BONDADE",
            "PINA", "IMBIRIBEIRA", "NOVA DESCOBERTA", "ALDEIA", "COMPESA", "SEDE"
    );

    private RmrFilter() {
    }

    public static String normalize(String txt) {
        if (txt == null) return "";
        String semAcentos = Normalizer.normalize(txt, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return semAcentos.toUpperCase().trim();
    }

    public static boolean isRmr(String municipio, String estacaoNome) {
        String muni = normalize(municipio);
        String est = normalize(estacaoNome);
        return matchesAny(RMR_MUNICIPIOS, muni, est) || matchesAny(RMR_TERMOS_ESPECIAIS, muni, est);
    }

    private static boolean matchesAny(List<String> termos, String... alvos) {
        for (String termo : termos) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(normalize(termo)) + "\\b");
            for (String alvo : alvos) {
                if (alvo != null && !alvo.isBlank() && pattern.matcher(alvo).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
