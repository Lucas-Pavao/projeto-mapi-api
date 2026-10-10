package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.ingestion.occurrence.CkanColumnMapping;
import com.projeto.mapi.service.ingestion.occurrence.LabelFilterConfig;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassifier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-BACK-18. A amostra {@code ckan/2022-shifted-sample.json} são payloads reais preservados em
 * {@code raw_observation} do recurso de 2022 (colunas deslocadas; um deles do recurso com a coluna "MÃªs").
 */
class CkanColumnMappingTest {

    private static final LabelFilterConfig FILTER =
            new LabelFilterConfig(1L, "cobrade-alagamento", List.of(), List.of("alagamento"));

    private List<Map<String, Object>> sample2022() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ckan/2022-shifted-sample.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    @Test
    void detectaOEsquemaDeslocadoPeloPerfilDosValoresNaoPeloAno() throws Exception {
        assertThat(CkanColumnMapping.detect(sample2022())).isSameAs(CkanColumnMapping.SHIFTED_DATE_IN_MONTH);
    }

    @Test
    void esquemaPadraoContinuaPadrao() {
        // 2023/2024: Ano = "2024", Mês = nome do mês, Data = data ISO
        Map<String, Object> r = Map.of("_id", 1, "Regional", "Regional 3", "Ano", "2024", "Mês", "Maio",
                "Data", "2024-05-28T00:00:00", "Ocorrencia", "Alagamentos", "Endereco", "Rua X, 10", "Bairro", "Boa Viagem");
        assertThat(CkanColumnMapping.detect(List.of(r))).isSameAs(CkanColumnMapping.STANDARD);
        assertThat(CkanColumnMapping.detect(List.of())).isSameAs(CkanColumnMapping.STANDARD);
    }

    @Test
    void mapeiaOAlagamentoReal35006ParaDataTipoEEnderecoCorretos() throws Exception {
        Map<String, Object> real = sample2022().stream().filter(m -> "35006".equals(String.valueOf(m.get("_id"))))
                .findFirst().orElseThrow();
        Map<String, Object> canon = CkanColumnMapping.SHIFTED_DATE_IN_MONTH.canonical(real);
        assertThat(canon).containsEntry("Data", "2022-07-12").containsEntry("Ocorrencia", "Alagamentos")
                .containsEntry("Endereco", "Avenida Joaquim Ribeiro").containsEntry("Bairro", "Varzea");

        OccurrenceClassification cls = OccurrenceClassifier.classify(canon, FILTER, "America/Recife");
        assertThat(cls.status()).isEqualTo(OccurrenceClassification.Status.ACCEPTED);
        assertThat(cls.precision()).isEqualTo("DAY");
        assertThat(cls.address()).isEqualTo("Avenida Joaquim Ribeiro");
    }

    @Test
    void colunaComCodificacaoQuebradaEMapeadaIgual() throws Exception {
        // a mesma ocorrência no recurso cuja coluna se chama "MÃªs" (UTF-8 lido como Latin-1), data com hora zero
        Map<String, Object> real = sample2022().stream().filter(m -> m.containsKey("MÃªs")).findFirst().orElseThrow();
        Map<String, Object> canon = CkanColumnMapping.SHIFTED_DATE_IN_MONTH.canonical(real);
        assertThat(canon).containsEntry("Data", "2022-07-12T00:00:00").containsEntry("Ocorrencia", "Alagamentos");
    }

    @Test
    void soAlagamentoDoRecurso2022ViraRotuloEOsDemaisSaoFiltradosOuSemTipo() throws Exception {
        long aceitos = 0;
        long excluidos = 0;
        for (Map<String, Object> real : sample2022()) {
            OccurrenceClassification cls = OccurrenceClassifier.classify(
                    CkanColumnMapping.SHIFTED_DATE_IN_MONTH.canonical(real), FILTER, "America/Recife");
            if (cls.status() == OccurrenceClassification.Status.ACCEPTED) aceitos++;
            else if (cls.status() == OccurrenceClassification.Status.EXCLUDED_BY_FILTER) excluidos++;
        }
        // 4 "Alagamentos" (35006, 35015, 35016 e a repetição de 35006 no outro recurso); os 4 restantes
        // (Data nula x2, "Imóveis Alagados", "Deslizamentos") não são o rótulo, mas têm data válida
        assertThat(aceitos).isEqualTo(4);
        assertThat(excluidos).isEqualTo(4);
    }
}
