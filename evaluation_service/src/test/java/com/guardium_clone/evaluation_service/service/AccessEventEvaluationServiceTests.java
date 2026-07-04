package com.guardium_clone.evaluation_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AccessEventEvaluationServiceTests {

    private final AccessEventEvaluationService service = new AccessEventEvaluationService();

    @Test
    void evaluateLogsLowSeverityForAllowedNormalAccess(CapturedOutput output) {
        service.evaluate(new AccessEventCreatedMessage(
                100L,
                "EMPLOYEE",
                "ORDERS",
                "SELECT",
                Instant.parse("2026-07-02T16:00:00Z"),
                100,
                "10.0.0.12",
                "SELECT * FROM orders"
        ));

        assertThat(output).contains("Access event 100 severity: LOW");
    }

    @Test
    void evaluateLogsCombinedSeverityFromMultipleRiskSignals(CapturedOutput output) {
        service.evaluate(new AccessEventCreatedMessage(
                101L,
                "GUEST",
                "CUSTOMERS_ACCOUNTS",
                "DELETE",
                Instant.parse("2026-07-02T08:00:00Z"),
                101,
                "10.0.0.12",
                "DELETE FROM customers_accounts"
        ));

        assertThat(output).contains("Access event 101 severity: CRITICAL");
    }
}

