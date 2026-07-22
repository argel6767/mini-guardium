package com.guardium_clone.ingestion_processor.api;

import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import java.time.Instant;

public record IngestionStatusResponse(
        Long ingestionId,
        IngestionStatus status,
        Instant acceptedAt,
        Instant updatedAt
) {

    public static IngestionStatusResponse from(IngestionEvent event) {
        return new IngestionStatusResponse(
                event.getId(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
