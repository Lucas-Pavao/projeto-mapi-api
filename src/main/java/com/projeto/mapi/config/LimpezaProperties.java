package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Limpeza recente da drenagem pela Central 156 (prefixo {@code mapi.limpeza}). Regras e números:
 * docs/PRE-REGISTRO-ENTUPIMENTO-156.md e docs/extratos/emlurb-156/entupimento/RESULTADOS.md (posições alinhadas, 2026-11-21).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.limpeza")
public class LimpezaProperties {

    /** Liga a ingestão semanal e o uso da limpeza no alerta. */
    private boolean enabled = true;
    /** Semanal (o CKAN diz atualização semanal do CSV do ano). */
    private String cron = "0 30 4 * * MON";
    private String ckanBaseUrl = "https://dados.recife.pe.gov.br";
    private String dataset = "central-de-atendimento-de-servicos-da-emlurb-156";
    /** Casamento por coordenada (pré-registro, seção 2). */
    private double raioM = 150.0;
    /** Serviços que contam como entupimento/limpeza, "grupo/serviço" (aprovados pelo Lucas em 2026-11-21). */
    private List<String> servicos = List.of("2/4", "11/4", "27/8", "2/81", "11/81", "27/157");
    /** PROVAVEL quando a probabilidade da mistura livre/entupida chega a este valor. */
    private double probOn = 0.5;
    /** Abaixo disso a taxa do ponto não é usada (pré-registro, desvio 4). */
    private int minLimpezas = 10;
    /** Capacidade da galeria entupida, fração da de projeto ([sem fonte]; 0 = fechada). */
    private double fEntupida = 0.0;

    private Map<String, Ponto> pontos = defaults();

    @Data
    public static class Ponto {
        /** Logradouro como a 156 grava, normalizado (sem acento, sem prefixo). Nulo = só coordenada. */
        private String logradouro;
        private String bairro;
        /** Taxa de reentupimento por dia (limpeza ATENDIDA -> próximo chamado a 150 m). Nulo = sem dado. */
        private Double lambdaRePorDia;
        private int limpezas;

        static Ponto of(String logradouro, String bairro, Double lambda, int limpezas) {
            Ponto p = new Ponto();
            p.logradouro = logradouro; p.bairro = bairro; p.lambdaRePorDia = lambda; p.limpezas = limpezas;
            return p;
        }
    }

    private static Map<String, Ponto> defaults() {
        Map<String, Ponto> m = new LinkedHashMap<>();
        m.put("AV_RECIFE_IBURA", Ponto.of("RECIFE", "IPSEP", 0.02337, 53));
        m.put("AGAMENON_DERBY", Ponto.of("GOVERNADOR AGAMENON MAGALHAES", "BOA VISTA", 0.00981, 35));
        m.put("MASCARENHAS_IMBIRIBEIRA", Ponto.of("MARECHAL MASCARENHAS DE MORAES", "IMBIRIBEIRA", 0.03331, 130));
        m.put("CIN_UFPE", Ponto.of(null, "AREIAS", null, 0));
        return m;
    }
}
