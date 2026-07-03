package com.guardium_clone.ingestion_processor.messaging;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import java.time.Instant;

public record AccessEventCreatedMessage(
        Long accessEventId,
        String username,
        String tableName,
        String queryType,
        Instant occurredAt,
        long rowCount,
        String sourceIp,
        String queryText
) {

    public static AccessEventCreatedMessage from(AccessEvent accessEvent) {
        return new AccessEventCreatedMessage(
                accessEvent.getId(),
                accessEvent.getUser().getUsername(),
                accessEvent.getTable().getName(),
                accessEvent.getQueryType().name(),
                accessEvent.getOccurredAt(),
                accessEvent.getRowCount(),
                accessEvent.getSourceIp(),
                accessEvent.getQueryText()
        );
    }
}