package com.guardium_clone.evaluation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.evaluation_service.api.AlertMapper;
import com.guardium_clone.evaluation_service.api.AlertRateSnapshotResponse;
import com.guardium_clone.evaluation_service.events.AlertCreatedEvent;
import com.guardium_clone.evaluation_service.model.AccessEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.model.DatabaseTable;
import com.guardium_clone.evaluation_service.model.DatabaseUser;
import com.guardium_clone.evaluation_service.model.QueryType;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertStreamServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-02T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AlertRepository alertRepository;

    @Test
    void handleAlertCreatedRecordsRatesBySeverityAndRule() {
        Alert alert = alert(AlertSeverity.HIGH, "ACCESS_EVENT_RISK");
        when(alertRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(alert));
        AlertStreamService service = new AlertStreamService(alertRepository, new AlertMapper(), CLOCK);

        service.handleAlertCreated(new AlertCreatedEvent(1L));

        AlertRateSnapshotResponse snapshot = service.rateSnapshot();
        assertThat(snapshot.overallPerMinute()).isEqualTo(1.0);
        assertThat(snapshot.bySeverityPerMinute()).containsEntry(AlertSeverity.HIGH, 1.0);
        assertThat(snapshot.bySeverityPerMinute()).containsEntry(AlertSeverity.LOW, 0.0);
        assertThat(snapshot.byRulePerMinute()).containsEntry("ACCESS_EVENT_RISK", 1.0);
    }

    @Test
    void handleAlertCreatedIgnoresMissingAlertRows() {
        when(alertRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());
        AlertStreamService service = new AlertStreamService(alertRepository, new AlertMapper(), CLOCK);

        service.handleAlertCreated(new AlertCreatedEvent(99L));

        AlertRateSnapshotResponse snapshot = service.rateSnapshot();
        assertThat(snapshot.overallPerMinute()).isZero();
        verify(alertRepository).findByIdWithDetails(99L);
    }

    private Alert alert(AlertSeverity severity, String ruleName) {
        AccessEvent accessEvent = new AccessEvent(
                new DatabaseUser("alice"),
                new DatabaseTable("customer_accounts", true),
                QueryType.SELECT,
                Instant.parse("2026-07-02T11:59:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
        Alert alert = new Alert(accessEvent, ruleName, severity, "alice accessed customer_accounts");
        alert.setCreatedAt(Instant.parse("2026-07-02T12:00:00Z"));
        return alert;
    }
}
