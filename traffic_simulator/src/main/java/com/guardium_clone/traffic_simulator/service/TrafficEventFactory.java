package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class TrafficEventFactory {

    private static final int EVENT_HISTORY_DAYS = 14;
    private static final ZoneId EVENT_TIME_ZONE = ZoneId.of("America/New_York");
    private static final List<String> USERNAMES = List.of(
            "alice",
            "bob",
            "carol",
            "dba_admin",
            "reporting_service",
            "etl_worker"
    );
    private static final List<String> TABLE_NAMES = List.of(
            "customer_accounts",
            "orders",
            "payment_cards",
            "audit_log",
            "employee_records",
            "inventory"
    );
    private static final List<QueryType> QUERY_TYPES = List.of(
            QueryType.SELECT,
            QueryType.SELECT,
            QueryType.SELECT,
            QueryType.INSERT,
            QueryType.UPDATE,
            QueryType.DELETE
    );
    private static final Map<String, TimeProfile> TIME_PROFILES = Map.of(
            "alice", new TimeProfile(
                    new WeightedTimeWindow(85, LocalTime.of(7, 0), LocalTime.of(19, 0)),
                    new WeightedTimeWindow(12, LocalTime.of(19, 0), LocalTime.of(22, 0)),
                    new WeightedTimeWindow(3, LocalTime.of(22, 0), LocalTime.of(7, 0))
            ),
            "bob", new TimeProfile(
                    new WeightedTimeWindow(85, LocalTime.of(7, 0), LocalTime.of(19, 0)),
                    new WeightedTimeWindow(12, LocalTime.of(19, 0), LocalTime.of(22, 0)),
                    new WeightedTimeWindow(3, LocalTime.of(22, 0), LocalTime.of(7, 0))
            ),
            "carol", new TimeProfile(
                    new WeightedTimeWindow(95, LocalTime.of(6, 0), LocalTime.of(0, 0)),
                    new WeightedTimeWindow(4, LocalTime.of(0, 0), LocalTime.of(3, 0)),
                    new WeightedTimeWindow(1, LocalTime.of(3, 0), LocalTime.of(6, 0))
            ),
            "dba_admin", new TimeProfile(
                    new WeightedTimeWindow(90, LocalTime.of(6, 0), LocalTime.of(23, 0)),
                    new WeightedTimeWindow(8, LocalTime.of(23, 0), LocalTime.of(2, 0)),
                    new WeightedTimeWindow(2, LocalTime.of(2, 0), LocalTime.of(6, 0))
            ),
            "reporting_service", new TimeProfile(
                    new WeightedTimeWindow(95, LocalTime.of(5, 0), LocalTime.of(22, 0)),
                    new WeightedTimeWindow(4, LocalTime.of(22, 0), LocalTime.of(1, 0)),
                    new WeightedTimeWindow(1, LocalTime.of(1, 0), LocalTime.of(5, 0))
            ),
            "etl_worker", new TimeProfile(
                    new WeightedTimeWindow(97, LocalTime.of(1, 30), LocalTime.of(2, 30)),
                    new WeightedTimeWindow(2, LocalTime.of(1, 0), LocalTime.of(3, 0)),
                    new WeightedTimeWindow(1, LocalTime.of(3, 0), LocalTime.of(1, 0))
            )
    );

    private final Clock clock;
    private final RandomGenerator randomGenerator;

    public TrafficEventFactory() {
        this(Clock.systemUTC(), RandomGenerator.getDefault());
    }

    TrafficEventFactory(Clock clock, RandomGenerator randomGenerator) {
        this.clock = clock;
        this.randomGenerator = randomGenerator;
    }

    public IngestEventRequest nextEvent() {
        String username = randomItem(USERNAMES);
        String tableName = randomItem(TABLE_NAMES);
        QueryType queryType = randomItem(QUERY_TYPES);
        long rowCount = rowCountFor(queryType);
        String sourceIp = "10.0.%d.%d".formatted(
                randomGenerator.nextInt(0, 8),
                randomGenerator.nextInt(2, 255)
        );

        return new IngestEventRequest(
                username,
                tableName,
                queryType,
                occurredAtFor(username),
                rowCount,
                sourceIp,
                queryTextFor(queryType, tableName)
        );
    }

    private Instant occurredAtFor(String username) {
        Instant now = Instant.now(clock);
        ZonedDateTime nowInEventZone = now.atZone(EVENT_TIME_ZONE);
        LocalDate eventDate = nowInEventZone.toLocalDate()
                .minusDays(randomGenerator.nextInt(EVENT_HISTORY_DAYS));
        LocalTime eventTime = TIME_PROFILES.get(username).randomTime(randomGenerator);
        Instant occurredAt = LocalDateTime.of(eventDate, eventTime)
                .atZone(EVENT_TIME_ZONE)
                .toInstant();

        if (occurredAt.isAfter(now)) {
            return now;
        }
        return occurredAt;
    }

    private <T> T randomItem(List<T> values) {
        return values.get(randomGenerator.nextInt(values.size()));
    }

    private long rowCountFor(QueryType queryType) {
        return switch (queryType) {
            case SELECT -> randomGenerator.nextLong(1, 5_001);
            case INSERT -> randomGenerator.nextLong(1, 101);
            case UPDATE -> randomGenerator.nextLong(1, 501);
            case DELETE -> randomGenerator.nextLong(1, 251);
            case CREATE, ALTER, DROP, OTHER -> 0;
        };
    }

    private String queryTextFor(QueryType queryType, String tableName) {
        return switch (queryType) {
            case SELECT -> "select * from %s".formatted(tableName);
            case INSERT -> "insert into %s values (...)".formatted(tableName);
            case UPDATE -> "update %s set updated_at = now() where id = ?".formatted(tableName);
            case DELETE -> deleteQueryFor(tableName);
            case CREATE -> "create table %s_archive (...)".formatted(tableName);
            case ALTER -> "alter table %s add column reviewed_at timestamp".formatted(tableName);
            case DROP -> "drop table %s_archive".formatted(tableName);
            case OTHER -> null;
        };
    }

    private String deleteQueryFor(String tableName) {
        if (randomGenerator.nextInt(10) == 0) {
            return "delete from %s".formatted(tableName);
        }
        return "delete from %s where id = ?".formatted(tableName);
    }

    private record TimeProfile(List<WeightedTimeWindow> windows, int totalWeight) {

        private TimeProfile(WeightedTimeWindow... windows) {
            this(List.of(windows), List.of(windows).stream().mapToInt(WeightedTimeWindow::weight).sum());
        }

        private LocalTime randomTime(RandomGenerator randomGenerator) {
            int selectedWeight = randomGenerator.nextInt(totalWeight);
            int runningWeight = 0;
            for (WeightedTimeWindow window : windows) {
                runningWeight += window.weight();
                if (selectedWeight < runningWeight) {
                    return window.randomTime(randomGenerator);
                }
            }
            return windows.getLast().randomTime(randomGenerator);
        }
    }

    private record WeightedTimeWindow(int weight, LocalTime start, LocalTime end) {

        private LocalTime randomTime(RandomGenerator randomGenerator) {
            int startMinute = start.toSecondOfDay() / 60;
            int endMinute = end.toSecondOfDay() / 60;
            int totalMinutes = endMinute > startMinute
                    ? endMinute - startMinute
                    : (24 * 60) - startMinute + endMinute;
            int selectedMinute = (startMinute + randomGenerator.nextInt(totalMinutes)) % (24 * 60);
            return LocalTime.of(selectedMinute / 60, selectedMinute % 60);
        }
    }
}
