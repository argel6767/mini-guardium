package com.guardium_clone.ingestion_processor.api;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.QueryType;
import java.time.Instant;

public record IngestEventResponse(
        Long eventId,
        String username,
        String tableName,
        QueryType queryType,
        Instant occurredAt,
        long rowCount,
        String sourceIp
) {

    public static IngestEventResponse from(AccessEvent event) {
        return new IngestEventResponse(
                event.getId(),
                event.getUser().getUsername(),
                event.getTable().getName(),
                event.getQueryType(),
                event.getOccurredAt(),
                event.getRowCount(),
                event.getSourceIp()
        );
    }
}
