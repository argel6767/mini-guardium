package com.guardium_clone.traffic_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TrafficEventFactoryTests {

    @Test
    void nextEventCreatesValidIngestionPayload() {
        TrafficEventFactory factory = new TrafficEventFactory(
                Clock.fixed(Instant.parse("2026-07-02T22:30:00Z"), ZoneOffset.UTC),
                new Random(1)
        );

        var event = factory.nextEvent();

        assertThat(event.username()).isNotBlank();
        assertThat(event.tableName()).isNotBlank();
        assertThat(event.queryType()).isNotNull();
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-02T22:30:00Z"));
        assertThat(event.rowCount()).isNotNegative();
        assertThat(event.sourceIp()).startsWith("10.0.");
        assertThat(event.queryText()).isNotBlank();
    }
}
