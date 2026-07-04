package com.guardium_clone.evaluation_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.evaluation_service.model.AccessEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.model.DatabaseTable;
import com.guardium_clone.evaluation_service.model.DatabaseUser;
import com.guardium_clone.evaluation_service.model.QueryType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertPersistenceTests {

    @Autowired
    private AccessEventRepository accessEventRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DatabaseTableRepository databaseTableRepository;

    @Autowired
    private DatabaseUserRepository databaseUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void alertLifecycleSetsCreatedAtWhenMissing() {
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(accessEvent());
        Alert alert = new Alert(accessEvent, "large export", AlertSeverity.HIGH, "large read detected");

        Alert saved = alertRepository.saveAndFlush(alert);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void alertLifecycleKeepsExistingCreatedAt() {
        Instant createdAt = Instant.parse("2026-07-02T22:30:00Z");
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(accessEvent());
        Alert alert = new Alert(accessEvent, "large export", AlertSeverity.HIGH, "large read detected");
        alert.setCreatedAt(createdAt);

        Alert saved = alertRepository.saveAndFlush(alert);

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void alertCanBeCreatedForExistingAccessEventReference() {
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(accessEvent());
        entityManager.clear();

        AccessEvent reference = accessEventRepository.getReferenceById(accessEvent.getId());
        Alert saved = alertRepository.saveAndFlush(new Alert(
                reference,
                "sensitive table access",
                AlertSeverity.MEDIUM,
                "alice accessed customer_accounts"
        ));

        assertThat(saved.getAccessEvent().getId()).isEqualTo(accessEvent.getId());
    }

    private AccessEvent accessEvent() {
        DatabaseUser user = databaseUserRepository.saveAndFlush(new DatabaseUser("alice-" + System.nanoTime()));
        DatabaseTable table = databaseTableRepository.saveAndFlush(new DatabaseTable("accounts-" + System.nanoTime(), true));
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
