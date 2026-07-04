package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;

public record AlertSeverityEventResponse(
        Long alertId,
        AlertSeverity severity,
        String ruleName,
        Instant createdAt
) {
}
