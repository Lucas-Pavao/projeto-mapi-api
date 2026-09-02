package com.projeto.mapi.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Extrai o valor numérico "principal" de uma leitura bruta de sensor (ANA ou APAC), na mesma
 * ordem de prioridade usada pelo antigo coletor Python (VirtualSensor._extrair_valor_relevante).
 * Alimenta SensorData.fogValueReference, que SensorServiceImpl usa como valor de fallback para
 * tipos de medição (ex.: Turbidez_Adotada, Ph_Adotado, Temperatura_Adotada da ANA) que não têm
 * coluna dedicada em SensorData.
 */
public final class SensorValueExtractor {

    private static final List<String> CHAVES_PRIORITARIAS = List.of(
            "chuva_acumulada", "precipitacao_acumulada", "Chuva_Adotada", "chuva",
            "Nivel_Adotado", "nivel",
            "Vazao_Adotada", "vazao",
            "Temperatura_Adotada", "temperatura_ar",
            "Turbidez_Adotada", "turbidez",
            "Ph_Adotado", "ph",
            "Umidade_Adotada", "umidade_relativa"
    );

    private SensorValueExtractor() {
    }

    public static Double extract(JsonNode leitura) {
        if (leitura == null) return null;
        for (String chave : CHAVES_PRIORITARIAS) {
            if (leitura.has(chave) && !leitura.get(chave).isNull()) {
                try {
                    return leitura.get(chave).asDouble();
                } catch (Exception ignored) {
                    // valor não numérico nesta chave, tenta a próxima
                }
            }
        }
        return null;
    }
}
