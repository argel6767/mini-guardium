package com.guardium_clone.ingestion_processor.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IngestionEventNotFoundException extends RuntimeException {

    public IngestionEventNotFoundException(Long ingestionId) {
        super("Ingestion event not found: " + ingestionId);
    }
}
