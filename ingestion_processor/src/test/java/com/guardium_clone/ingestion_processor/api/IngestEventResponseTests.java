package com.guardium_clone.ingestion_processor.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.model.QueryType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IngestEventResponseTests {

    @Test
    void fromMapsOnlyClientFacingIngestionMetadata() {
        IngestionEvent event = new IngestionEvent(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
        ReflectionTestUtils.setField(event, "id", 42L);
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-07-02T22:31:00Z"));
        event.setStatus(IngestionStatus.PENDING);

        IngestEventResponse response = IngestEventResponse.from(event);

        assertThat(response.ingestionId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(IngestionStatus.PENDING);
        assertThat(response.acceptedAt()).isEqualTo(Instant.parse("2026-07-02T22:31:00Z"));
    }
}
