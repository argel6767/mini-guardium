package com.guardium_clone.evaluation_service.api;

import java.time.Instant;
import java.util.List;

public record AlertBatchResponse(
        List<AlertResponse> alerts,
        int batchSize,
        Instant sentAt
) {
}
