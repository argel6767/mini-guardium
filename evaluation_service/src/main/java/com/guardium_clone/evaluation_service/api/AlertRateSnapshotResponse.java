package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;
import java.util.Map;

public record AlertRateSnapshotResponse(
        Instant timestamp,
        long windowSeconds,
        long intervalSeconds,
        double overallPerMinute,
        Map<AlertSeverity, Double> bySeverityPerMinute,
        Map<String, Double> byRulePerMinute
) {
}
