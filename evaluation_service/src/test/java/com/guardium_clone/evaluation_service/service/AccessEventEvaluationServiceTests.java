package com.guardium_clone.evaluation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.evaluation_service.events.AlertCreatedEvent;
import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.AccessEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.model.DatabaseTable;
import com.guardium_clone.evaluation_service.model.DatabaseUser;
import com.guardium_clone.evaluation_service.model.QueryType;
import com.guardium_clone.evaluation_service.repository.AccessEventRepository;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AccessEventEvaluationServiceTests {

    @Mock
    private AccessEventRepository accessEventRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<Alert> alertCaptor;

    @InjectMocks
    private AccessEventEvaluationService service;

    @Test
    void evaluateDoesNotPersistAlertForAllowedNormalAccess() {
        service.evaluate(new AccessEventCreatedMessage(
                100L,
                "alice",
                "orders",
                "SELECT",
                Instant.parse("2026-07-02T16:00:00Z"),
                100,
                "10.0.0.12",
                "SELECT * FROM orders"
        ));

        verify(accessEventRepository, never()).findById(any());
        verify(alertRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void evaluatePersistsAlertWhenAccessEventHasRiskSignals() {
        AccessEvent accessEvent = new AccessEvent(
                new DatabaseUser("carol"),
                new DatabaseTable("customer_accounts", true),
                QueryType.DELETE,
                Instant.parse("2026-07-02T08:00:00Z"),
                101,
                "10.0.0.12",
                "DELETE FROM customer_accounts"
        );
        when(accessEventRepository.findById(101L)).thenReturn(Optional.of(accessEvent));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.evaluate(new AccessEventCreatedMessage(
                101L,
                "carol",
                "customer_accounts",
                "DELETE",
                Instant.parse("2026-07-02T08:00:00Z"),
                101,
                "10.0.0.12",
                "DELETE FROM customer_accounts"
        ));

        verify(alertRepository).save(alertCaptor.capture());
        Alert alert = alertCaptor.getValue();
        assertThat(alert.getAccessEvent()).isSameAs(accessEvent);
        assertThat(alert.getRuleName()).isEqualTo("ACCESS_EVENT_RISK");
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getMessage()).isEqualTo("Access event 101 evaluated with CRITICAL severity");
        verify(applicationEventPublisher).publishEvent(new AlertCreatedEvent(null));
    }
}

