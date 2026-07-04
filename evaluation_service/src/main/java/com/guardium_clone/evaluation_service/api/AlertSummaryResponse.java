package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;
import java.util.Map;

public record AlertSummaryResponse(
        long totalAlerts,
        Instant latestAlertCreatedAt,
        Map<AlertSeverity, Long> countsBySeverity
) {
}
