package com.projeto.mapi.service.ingestion.adapter.tide;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifica cada extremo como PREAMAR ou BAIXA_MAR comparando-o ao PRÓXIMO extremo (se o próximo é mais baixo,
 * este é uma preamar); o último compara com o anterior. Depende só do vizinho seguinte, então a classificação não
 * muda com a janela pedida, desde que o adaptador traga um extremo além do fim. Altura igual à do vizinho é
 * ambígua e fica sem classe (null).
 */
public final class TideExtremeClassifier {

    private TideExtremeClassifier() {}

    /** {@code sorted} em ordem cronológica. Devolve uma classe por extremo, na mesma ordem. */
    public static List<String> classify(List<TideExtreme> sorted) {
        List<String> out = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            double h = sorted.get(i).level();
            if (sorted.size() == 1) {
                out.add(null);
                continue;
            }
            double other = i + 1 < sorted.size() ? sorted.get(i + 1).level() : sorted.get(i - 1).level();
            if (h == other) out.add(null);
            else out.add(h > other ? TideConstants.PREAMAR : TideConstants.BAIXA_MAR);
        }
        return out;
    }
}
