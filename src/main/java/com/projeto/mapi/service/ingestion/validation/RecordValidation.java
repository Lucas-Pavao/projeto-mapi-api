package com.projeto.mapi.service.ingestion.validation;

import java.util.List;

public record RecordValidation(List<ValidObservation> valid, List<Rejection> rejections) {}
