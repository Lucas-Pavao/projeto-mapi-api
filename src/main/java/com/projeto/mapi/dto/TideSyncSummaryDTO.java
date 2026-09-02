package com.projeto.mapi.dto;

import java.util.List;

public record TideSyncSummaryDTO(int harborsSynced, int monthsSynced, int errors, List<String> harborNames) {
}
