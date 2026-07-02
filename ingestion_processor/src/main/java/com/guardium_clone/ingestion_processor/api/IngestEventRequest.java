package com.guardium_clone.ingestion_processor.api;

import com.guardium_clone.ingestion_processor.model.QueryType;
import java.time.Instant;

public record IngestEventRequest(
        String username,
        String tableName,
        QueryType queryType,
        Instant occurredAt,
        long rowCount,
        String sourceIp,
        String queryText
) {
}