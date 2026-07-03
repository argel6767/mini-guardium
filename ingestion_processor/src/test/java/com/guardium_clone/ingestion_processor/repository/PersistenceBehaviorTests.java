package com.guardium_clone.ingestion_processor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.Alert;
import com.guardium_clone.ingestion_processor.model.AlertSeverity;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.model.QueryType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceBehaviorTests {

    @Autowired
    private AccessEventRepository accessEventRepository;

    @Autowired
    private DatabaseTableRepository databaseTableRepository;

    @Autowired
    private DatabaseUserRepository databaseUserRepository;

    @Autowired
    private IngestionEventRepository ingestionEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findTop50PendingEventsOrdersByCreatedAtAscending() throws InterruptedException {
        IngestionEvent first = ingestionEvent("first", IngestionStatus.PENDING);
        ingestionEventRepository.saveAndFlush(first);
        Thread.sleep(5);
        IngestionEvent second = ingestionEvent("second", IngestionStatus.PENDING);
        ingestionEventRepository.saveAndFlush(second);
        IngestionEvent failed = ingestionEvent("failed", IngestionStatus.FAILED);
        ingestionEventRepository.saveAndFlush(failed);

        List<IngestionEvent> pendingEvents = ingestionEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus.PENDING);

        assertThat(pendingEvents)
                .extracting(IngestionEvent::getUsername)
                .containsExactly("first", "second");
    }

    @Test
    void retryScanReturnsOnlyDueFailuresUnderMaxRetryCount() {
        Instant now = Instant.now();
        IngestionEvent dueFailure = ingestionEvent("due", IngestionStatus.FAILED);
        dueFailure.setRetryCount(1);
        dueFailure.setNextAttemptAt(now.minusSeconds(1));
        IngestionEvent futureFailure = ingestionEvent("future", IngestionStatus.FAILED);
        futureFailure.setRetryCount(1);
        futureFailure.setNextAttemptAt(now.plusSeconds(60));
        IngestionEvent maxedFailure = ingestionEvent("maxed", IngestionStatus.FAILED);
        maxedFailure.setRetryCount(5);
        maxedFailure.setNextAttemptAt(now.minusSeconds(1));
        IngestionEvent pending = ingestionEvent("pending", IngestionStatus.PENDING);
        pending.setNextAttemptAt(now.minusSeconds(1));
        ingestionEventRepository.saveAllAndFlush(List.of(dueFailure, futureFailure, maxedFailure, pending));

        List<IngestionEvent> retryableEvents = ingestionEventRepository
                .findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        IngestionStatus.FAILED,
                        5,
                        now
                );

        assertThat(retryableEvents)
                .singleElement()
                .extracting(IngestionEvent::getUsername)
                .isEqualTo("due");
    }

    @Test
    void ingestionEventLifecycleDefaultsStatusAndMaintainsTimestamps() throws InterruptedException {
        IngestionEvent event = ingestionEvent("alice", IngestionStatus.PENDING);
        event.setStatus(null);

        IngestionEvent saved = ingestionEventRepository.saveAndFlush(event);

        assertThat(saved.getStatus()).isEqualTo(IngestionStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(5);
        saved.setRowCount(99);
        IngestionEvent updated = ingestionEventRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void alertLifecycleSetsCreatedAtWhenMissing() {
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(accessEvent());
        Alert alert = new Alert(accessEvent, "large export", AlertSeverity.HIGH, "large read detected");

        entityManager.persist(alert);
        entityManager.flush();

        assertThat(alert.getCreatedAt()).isNotNull();
    }

    @Test
    void alertLifecycleKeepsExistingCreatedAt() {
        Instant createdAt = Instant.parse("2026-07-02T22:30:00Z");
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(accessEvent());
        Alert alert = new Alert(accessEvent, "large export", AlertSeverity.HIGH, "large read detected");
        alert.setCreatedAt(createdAt);

        entityManager.persist(alert);
        entityManager.flush();

        assertThat(alert.getCreatedAt()).isEqualTo(createdAt);
    }

    private IngestionEvent ingestionEvent(String username, IngestionStatus status) {
        IngestionEvent event = new IngestionEvent(
                username,
                "customer_accounts_" + username,
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
        event.setStatus(status);
        return event;
    }

    private AccessEvent accessEvent() {
        DatabaseUser user = databaseUserRepository.saveAndFlush(new DatabaseUser("alice-" + System.nanoTime()));
        DatabaseTable table = databaseTableRepository.saveAndFlush(new DatabaseTable("accounts-" + System.nanoTime(), false));
        return new AccessEvent(
                user,
                table,
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
    }
}
