package com.guardium_clone.ingestion_processor.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.messaging.RawIngestionEventMessage;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MessageContractTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void rawIngestionContractDeserializesIntoConsumerMessage() throws Exception {
        RawIngestionEventMessage message = jsonMapper.readValue(
                contractPath("raw-ingestion-event.json").toFile(),
                RawIngestionEventMessage.class
        );

        assertThat(message.username()).isEqualTo("contract-user");
        assertThat(message.queryType()).isEqualTo("SELECT");
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-07-21T12:00:00Z"));
        assertThat(message.rowCount()).isEqualTo(42);
    }

    @Test
    void accessEventCreatedMessageMatchesSharedContract() throws Exception {
        AccessEventCreatedMessage message = new AccessEventCreatedMessage(
                91L,
                "contract-user",
                "customer_accounts",
                "SELECT",
                Instant.parse("2026-07-21T12:00:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );

        JsonNode serializedMessage = jsonMapper.valueToTree(message);

        assertThat(serializedMessage.toString())
                .isEqualTo(jsonMapper.readTree(contractPath("access-event-created.json").toFile()).toString());
    }

    private Path contractPath(String filename) {
        return Path.of(System.getProperty("user.dir"), "..", "contracts", "messages", filename).normalize();
    }
}


