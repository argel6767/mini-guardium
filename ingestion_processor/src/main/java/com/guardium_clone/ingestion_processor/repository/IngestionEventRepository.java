package com.guardium_clone.ingestion_processor.repository;

import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionEventRepository extends JpaRepository<IngestionEvent, Long> {

    List<IngestionEvent> findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus status);

    List<IngestionEvent> findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            IngestionStatus status,
            int retryCount,
            Instant nextAttemptAt
    );
}
