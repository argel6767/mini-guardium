package com.guardium_clone.traffic_simulator.api;

import java.time.Instant;

public record IngestEventResponse(
        Long ingestionId,
        String status,
        Instant acceptedAt
) {
}
