package com.projeto.mapi.service.alerta;

/** Motivo de SEM_DADO, sempre visível. Falta de dado nunca vira NENHUM, NAO ou risco baixo. */
public enum SemDadoMotivo {
    SEM_ESTACAO_VALIDA("nenhuma estação válida a até 5 km"),
    LEITURA_VELHA("leitura mais nova acima do limite de frescor"),
    FONTE_PARADA("fonte de chuva parada (alarme de fonte velha)"),
    IA_INDISPONIVEL("serviço de previsão indisponível"),
    PREVISAO_RECUSADA("previsão recusada por falta de entrada (HTTP 422)"),
    SEM_PREVISAO("sem previsão de chuva para projetar a lâmina"),
    SEM_TERRENO("sem terreno do ponto"),
    TERRENO_EM_REVISAO("área de contribuição em revisão"),
    SERIE_INCOMPLETA("série de chuva de 10 min com lacuna recente"),
    BALDE_INDISPONIVEL("modelo físico (balde) indisponível");

    private final String texto;

    SemDadoMotivo(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }
}
