package com.guardium_clone.messaging;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import java.time.Instant;
import java.util.UUID;

public record RawIngestionEventMessage(
        UUID simulatedEventId,
        String username,
        String tableName,
        String queryType,
        Instant occurredAt,
        long rowCount,
        String sourceIp,
        String queryText
) {

    public static RawIngestionEventMessage from(IngestEventRequest request) {
        return new RawIngestionEventMessage(
                UUID.randomUUID(),
                request.username(),
                request.tableName(),
                request.queryType().name(),
                request.occurredAt(),
                request.rowCount(),
                request.sourceIp(),
                request.queryText()
        );
    }
}