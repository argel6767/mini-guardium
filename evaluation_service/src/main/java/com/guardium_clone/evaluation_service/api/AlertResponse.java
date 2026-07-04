package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;

public record AlertResponse(
        Long id,
        String ruleName,
        AlertSeverity severity,
        String message,
        Instant createdAt,
        AlertAccessEventResponse accessEvent
) {
}
