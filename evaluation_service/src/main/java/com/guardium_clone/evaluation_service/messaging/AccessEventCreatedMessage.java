package com.guardium_clone.evaluation_service.messaging;

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
}
