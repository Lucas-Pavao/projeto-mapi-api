package com.projeto.mapi.service.analise;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RainEventSummarizerTest {

    @Test
    void quantilLinear() {
        double[] v = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThat(RainEventSummarizer.quantile(v, 0.5)).isEqualTo(5.5);
        assertThat(RainEventSummarizer.quantile(v, 0.9)).isEqualTo(9.1);
        assertThat(RainEventSummarizer.quantile(new double[]{}, 0.9)).isNull();
        assertThat(RainEventSummarizer.quantile(new double[]{4}, 0.9)).isEqualTo(4.0);
    }

    @Test
    void classificacao() {
        var idf = RainEventClassifier.Classification.IDF;
        assertThat(RainEventClassifier.classify(idf, 5.0, 50.3)).isEqualTo(RainEventClassifier.Type.FRACA);
        assertThat(RainEventClassifier.classify(idf, 5.03, 50.3)).isEqualTo(RainEventClassifier.Type.MEDIA);
        assertThat(RainEventClassifier.classify(idf, 25.15, 50.3)).isEqualTo(RainEventClassifier.Type.FORTE);
        var met = RainEventClassifier.Classification.METEOROLOGICA;
        assertThat(RainEventClassifier.classify(met, 4.99, 50.3)).isEqualTo(RainEventClassifier.Type.FRACA);
        assertThat(RainEventClassifier.classify(met, 24.9, 50.3)).isEqualTo(RainEventClassifier.Type.MEDIA);
        assertThat(RainEventClassifier.classify(met, 25, 50.3)).isEqualTo(RainEventClassifier.Type.FORTE);
    }

    @Test
    void csvDeterministico() {
        record R(String a, Double b, java.util.List<String> c) {}
        String csv = RainEventCsv.write(R.class, java.util.List.of(new R("x,y", 1.50, java.util.List.of("u", "v")),
                new R(null, 1e-7, java.util.List.of())));
        assertThat(csv).isEqualTo("a,b,c\n\"x,y\",1.5,u;v\n,0.0000001,\n");
    }
}
