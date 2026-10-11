package com.projeto.mapi.service.ingestion.occurrence;

/** Limpeza do endereço do CKAN antes do geocoding (extraída de CivilDefenseServiceImpl, sem mudança de regra). */
public final class CkanAddress {

    private CkanAddress() {}

    public static String sanitize(String s) {
        if (s == null) return "";
        // Remove lixo comum de símbolos que a API do Recife retorna às vezes
        String clean = s.toLowerCase()
                .replace("nrte", "norte").replace("av.", "avenida").replace("r.", "rua")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("(\\D+)(\\d+)$", "$1, $2")
                .replaceAll("[#\\^\\>\\$\\!\\=\\+\\?\\\"\\*\\_\\|\\/\\@\\&\\<\\%\\~\\{\\}\\[\\]\\`\\']+", " ")
                .trim().replaceAll("^[,\\.\\-\\s]+|[,\\.\\-\\s]+$", "");

        clean = clean.replaceAll("\\s{2,}", " ");

        // Se após a limpeza sobrou apenas um ponto, vírgula ou string vazia, descarta
        if (clean.length() < 3 || clean.matches("^[\\W\\d]+$")) {
            return "";
        }
        return clean;
    }
}
