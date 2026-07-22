package com.guardium_clone.traffic_simulator.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardium_clone.messaging.RawIngestionEventMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RawIngestionEventContractTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void serializedMessageMatchesSharedContract() throws Exception {
        RawIngestionEventMessage message = new RawIngestionEventMessage(
                UUID.fromString("3d45d0aa-1e51-4b21-a606-e17d97a96f36"),
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
                .isEqualTo(jsonMapper.readTree(contractPath().toFile()).toString());
    }

    private Path contractPath() {
        return Path.of(System.getProperty("user.dir"), "..", "contracts", "messages", "raw-ingestion-event.json")
                .normalize();
    }
}


