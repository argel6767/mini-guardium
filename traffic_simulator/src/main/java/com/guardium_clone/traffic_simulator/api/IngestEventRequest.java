package com.guardium_clone.traffic_simulator.api;

import com.guardium_clone.traffic_simulator.model.QueryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record IngestEventRequest(
        @NotBlank
        String username,
        @NotBlank
        String tableName,
        @NotNull
        QueryType queryType,
        Instant occurredAt,
        @PositiveOrZero
        long rowCount,
        @NotBlank
        String sourceIp,
        String queryText
) {
}
