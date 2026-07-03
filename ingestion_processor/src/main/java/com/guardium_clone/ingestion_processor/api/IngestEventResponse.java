package com.guardium_clone.ingestion_processor.api;

import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import java.time.Instant;

public record IngestEventResponse(
        Long ingestionId,
        IngestionStatus status,
        Instant acceptedAt
) {

    public static IngestEventResponse from(IngestionEvent event) {
        return new IngestEventResponse(
                event.getId(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}
