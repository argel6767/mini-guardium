package com.guardium_clone.traffic_simulator.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.traffic_simulator.model.QueryType;
import jakarta.validation.Validation;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestEventRequestTests {

    @Test
    void validRequestMatchesIngestionContract() {
        IngestEventRequest request = new IngestEventRequest(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void invalidRequiredFieldsMatchIngestionValidationRules() {
        IngestEventRequest request = new IngestEventRequest(
                " ",
                " ",
                null,
                Instant.parse("2026-07-02T22:30:00Z"),
                -1,
                " ",
                "select * from customer_accounts"
        );

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("username", "tableName", "queryType", "rowCount", "sourceIp");
        }
    }
}
