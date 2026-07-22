package com.guardium_clone.evaluation_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AccessEventCreatedContractTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void sharedContractDeserializesIntoConsumerMessage() throws Exception {
        AccessEventCreatedMessage message = jsonMapper.readValue(
                contractPath().toFile(),
                AccessEventCreatedMessage.class
        );

        assertThat(message.accessEventId()).isEqualTo(91L);
        assertThat(message.username()).isEqualTo("contract-user");
        assertThat(message.tableName()).isEqualTo("customer_accounts");
        assertThat(message.queryType()).isEqualTo("SELECT");
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-07-21T12:00:00Z"));
        assertThat(message.rowCount()).isEqualTo(42);
        assertThat(message.sourceIp()).isEqualTo("10.0.0.12");
        assertThat(message.queryText()).isEqualTo("select * from customer_accounts");
    }

    private Path contractPath() {
        return Path.of(System.getProperty("user.dir"), "..", "contracts", "messages", "access-event-created.json")
                .normalize();
    }
}
