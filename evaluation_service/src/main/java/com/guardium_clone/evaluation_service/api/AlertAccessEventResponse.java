package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.QueryType;
import java.time.Instant;

public record AlertAccessEventResponse(
        Long id,
        String username,
        String tableName,
        QueryType queryType,
        Instant occurredAt,
        long rowCount,
        String sourceIp
) {
}
