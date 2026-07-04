package com.guardium_clone.traffic_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class TrafficEventFactoryTests {

    private static final Instant NOW = Instant.parse("2026-07-15T18:00:00Z");
    private static final ZoneId EVENT_TIME_ZONE = ZoneId.of("America/New_York");

    @Test
    void nextEventCreatesValidIngestionPayloadWithinRecentHistory() {
        TrafficEventFactory factory = new TrafficEventFactory(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PredictableRandomGenerator(0, 0, 0, 0, 0, 0, 0)
        );

        var event = factory.nextEvent();

        assertThat(event.username()).isNotBlank();
        assertThat(event.tableName()).isNotBlank();
        assertThat(event.queryType()).isNotNull();
        assertThat(event.occurredAt()).isBetween(NOW.minusSeconds(14 * 24 * 60 * 60), NOW);
        assertThat(event.rowCount()).isNotNegative();
        assertThat(event.sourceIp()).startsWith("10.0.");
        assertThat(event.queryText()).isNotBlank();
    }

    @Test
    void etlWorkerUsuallyGeneratesEventsNearExpectedRunTime() {
        TrafficEventFactory factory = new TrafficEventFactory(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PredictableRandomGenerator(5, 0, 0, 0, 0, 0, 0)
        );

        var event = factory.nextEvent();

        assertThat(event.username()).isEqualTo("etl_worker");
        LocalTime localTime = event.occurredAt().atZone(EVENT_TIME_ZONE).toLocalTime();
        assertThat(localTime).isBetween(LocalTime.of(1, 30), LocalTime.of(2, 29));
    }

    @Test
    void sameDayFutureGeneratedTimeIsClampedToNow() {
        TrafficEventFactory factory = new TrafficEventFactory(
                Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC),
                new PredictableRandomGenerator(0, 0, 0, 0, 0, 0, 0)
        );

        var event = factory.nextEvent();

        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-15T10:00:00Z"));
    }

    private static final class PredictableRandomGenerator implements RandomGenerator {

        private final int[] values;
        private int index;

        private PredictableRandomGenerator(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = nextValue();
            return Math.floorMod(value, bound);
        }

        @Override
        public int nextInt(int origin, int bound) {
            int value = nextValue();
            return origin + Math.floorMod(value, bound - origin);
        }

        @Override
        public long nextLong(long origin, long bound) {
            long value = nextValue();
            return origin + Math.floorMod(value, bound - origin);
        }

        @Override
        public long nextLong() {
            return nextValue();
        }

        @Override
        public int nextInt() {
            return nextValue();
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }

        @Override
        public boolean nextBoolean() {
            return nextInt(2) == 0;
        }

        @Override
        public float nextFloat() {
            return 0.0f;
        }

        private int nextValue() {
            if (index >= values.length) {
                return 0;
            }
            return values[index++];
        }
    }
}
