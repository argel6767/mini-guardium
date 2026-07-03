package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class TrafficEventFactory {

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
                Instant.now(clock),
                rowCount,
                sourceIp,
                queryTextFor(queryType, tableName)
        );
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
}
