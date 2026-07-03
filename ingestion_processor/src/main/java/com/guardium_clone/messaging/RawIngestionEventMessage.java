package com.guardium_clone.messaging;

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
}