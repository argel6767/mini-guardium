package com.guardium_clone.evaluation_service.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.guardium_clone.evaluation_service.service.AccessEventEvaluationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessEventCreatedListenerTests {

    private final AccessEventEvaluationService evaluationService = mock(AccessEventEvaluationService.class);
    private final AccessEventCreatedListener listener = new AccessEventCreatedListener(evaluationService);

    @Test
    void receiveDelegatesMessageToEvaluationService() {
        AccessEventCreatedMessage message = new AccessEventCreatedMessage(
                123L,
                "alice",
                "customer_accounts",
                "SELECT",
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );

        listener.receive(message);

        verify(evaluationService).evaluate(message);
    }
}
