package com.projeto.mapi.dto;

public record CollectionSummaryDTO(int processed, int errors) {
    public static CollectionSummaryDTO empty() {
        return new CollectionSummaryDTO(0, 0);
    }
}
