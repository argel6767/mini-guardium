package com.guardium_clone.ingestion_processor.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IngestionStatusResponseTests {

    @Test
    void mapsVisibleStatusFieldsFromEntity() {
        IngestionEvent event = new IngestionEvent();
        Instant acceptedAt = Instant.parse("2026-07-21T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-07-21T12:05:00Z");
        ReflectionTestUtils.setField(event, "id", 17L);
        ReflectionTestUtils.setField(event, "status", IngestionStatus.PROCESSED);
        ReflectionTestUtils.setField(event, "createdAt", acceptedAt);
        ReflectionTestUtils.setField(event, "updatedAt", updatedAt);

        IngestionStatusResponse response = IngestionStatusResponse.from(event);

        assertThat(response).isEqualTo(new IngestionStatusResponse(
                17L, IngestionStatus.PROCESSED, acceptedAt, updatedAt
        ));
    }
}
