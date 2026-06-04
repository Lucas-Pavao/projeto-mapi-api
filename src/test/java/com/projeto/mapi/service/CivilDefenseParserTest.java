package com.projeto.mapi.service;

import com.projeto.mapi.dto.CkanRecordDTO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CivilDefenseParserTest {

    private CkanRecordDTO mapToDto(CSVRecord row, Map<String, Integer> colMap) {
        CkanRecordDTO dto = new CkanRecordDTO();
        if (!colMap.isEmpty()) {
            if (colMap.containsKey("data")) dto.setData(row.get(colMap.get("data")));
            if (colMap.containsKey("ocorrencia")) dto.setOcorrencia(row.get(colMap.get("ocorrencia")));
            if (colMap.containsKey("solicitacao")) dto.setSolicitacao(row.get(colMap.get("solicitacao")));
            if (colMap.containsKey("endereco")) dto.setEndereco(row.get(colMap.get("endereco")));
            if (colMap.containsKey("bairro")) dto.setBairro(row.get(colMap.get("bairro")));
        } else {
            dto.setData(safeGet(row, 3, 2));
            dto.setOcorrencia(safeGet(row, 4));
            dto.setSolicitacao(safeGet(row, 5));
            dto.setEndereco(safeGet(row, 6));
            dto.setBairro(safeGet(row, 7));
        }
        return dto;
    }
    
    private String safeGet(CSVRecord row, int... indices) {
        for (int idx : indices) {
            if (idx < row.size()) {
                String v = row.get(idx);
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    @Test
    public void testParserComCabecalho2020() throws Exception {
        String csv = "\"2020\";\"Fevereiro\";\"2020-02-07\";\"\";\"Monitoramento Alagado\";\"Rua Prof. Antonio Luiz Lins de Barros\";\" ^[{\";\"Caxanga\";\"Nao Cadastrada\";\"\";\"2020-02-07\";\"Mapeamento de Area de Risco\";\"\";\"\";\"\";\"0\"";
        
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setQuote('\"').setTrim(true).setAllowMissingColumnNames(true).build();
        try (CSVParser parser = new CSVParser(new StringReader(csv), format)) {
            CSVRecord row = parser.getRecords().get(0);
            
            System.out.println("Col 2 (Data expected): " + safeGet(row, 2));
            System.out.println("Col 4 (Ocorrencia expected): " + safeGet(row, 4));
            System.out.println("Col 5 (Endereco expected): " + safeGet(row, 5));
            System.out.println("Col 6 (Lixo esperado): " + safeGet(row, 6));
            System.out.println("Col 7 (Bairro expected): " + safeGet(row, 7));
        }
    }
}
