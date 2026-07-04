package com.guardium_clone.evaluation_service.utils;

import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.model.Role;
import com.guardium_clone.evaluation_service.model.Table;
import com.guardium_clone.evaluation_service.model.TableAccess;
import com.guardium_clone.evaluation_service.model.TablePermission;
import com.guardium_clone.evaluation_service.model.TimeWindow;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public class AccessEventEvaluationUtils {

    private static final int NORMAL_POINTS = 0;
    private static final int MEDIUM_POINTS = 3;
    private static final int HIGH_POINTS = 6;
    private static final int CRITICAL_POINTS = 10;

    private static final Map<Role, List<TableAccess>> roleTableAccessMap = Map.of(
            Role.ADMIN, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.values())),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.values())),
                    new TableAccess(Table.PAYMENT_CARDS, Set.of(TablePermission.values())),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.values())),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.values())),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.values()))),
            Role.EMPLOYEE, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.values())),
                    new TableAccess(Table.PAYMENT_CARDS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ, TablePermission.WRITE)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.values()))),
            Role.GUEST, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of()),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.PAYMENT_CARDS, Set.of()),
                    new TableAccess(Table.AUDIT_LOGS, Set.of()),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of()),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ))),
            Role.ETL_WORKER, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.PAYMENT_CARDS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE))),
            Role.REPORTING_SERVICE, List.of(
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.PAYMENT_CARDS, Set.of()),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ)))
    );

    private static final List<Table> sensitiveTables = List.of(
            Table.AUDIT_LOGS,
            Table.EMPLOYEES_RECORDS,
            Table.INVENTORY,
            Table.PAYMENT_CARDS,
            Table.CUSTOMERS_ACCOUNTS
    );

    private static final Map<String, Role> userRoles = Map.of(
            "alice", Role.EMPLOYEE,
            "bob", Role.EMPLOYEE,
            "carol", Role.GUEST,
            "dba_admin", Role.ADMIN,
            "etl_worker", Role.ETL_WORKER,
            "reporting_service", Role.REPORTING_SERVICE
    );

    private static final Map<String, Table> tableNames = Map.of(
            "customer_accounts", Table.CUSTOMERS_ACCOUNTS,
            "customers_accounts", Table.CUSTOMERS_ACCOUNTS,
            "orders", Table.ORDERS,
            "payment_cards", Table.PAYMENT_CARDS,
            "audit_log", Table.AUDIT_LOGS,
            "audit_logs", Table.AUDIT_LOGS,
            "employee_records", Table.EMPLOYEES_RECORDS,
            "employees_records", Table.EMPLOYEES_RECORDS,
            "inventory", Table.INVENTORY
    );

    private static final NavigableMap<Integer, AlertSeverity> severityThresholds =
            Collections.unmodifiableNavigableMap(
                    new TreeMap<>(Map.of(
                            0, AlertSeverity.LOW,
                            3, AlertSeverity.MEDIUM,
                            6, AlertSeverity.HIGH,
                            10, AlertSeverity.CRITICAL
                    ))
            );

    public static boolean isSensitiveTable(AccessEventCreatedMessage message) {
        return sensitiveTables.contains(resolveTable(message.tableName()));
    }

    public static boolean isUserAllowed(AccessEventCreatedMessage message) {
        Role role = resolveRole(message.username());
        Table tableName = resolveTable(message.tableName());
        TablePermission permission = getTablePermission(message.queryType());

        return roleTableAccessMap.get(role).stream()
                .anyMatch(access ->
                        access.table() == tableName
                                && access.permissions().contains(permission)
                );
    }

    private static TablePermission getTablePermission(String queryType) {
        return switch (queryType) {
            case "SELECT" -> TablePermission.READ;
            case "INSERT", "CREATE" -> TablePermission.WRITE;
            case "UPDATE", "ALTER" -> TablePermission.UPDATE;
            case "DELETE", "DROP" -> TablePermission.DELETE;
            case "OTHER" -> TablePermission.READ;
            default -> throw new IllegalArgumentException(
                    "Unknown query type: " + queryType
            );
        };
    }

    public static boolean isUnsafeDelete(AccessEventCreatedMessage message) {
        return message.queryType().equals("DELETE")
                && (!message.queryText().contains("WHERE"));
    }

    public static int evaluateTimeRisk(AccessEventCreatedMessage message) {
        LocalTime time = message.occurredAt()
                .atZone(ZoneId.of("America/New_York"))
                .toLocalTime();

        Role role = resolveRole(message.username());

        int timeRisk = calulateTimeRiskForRole(role, time);
        return timeRisk;
    }

    private static int calulateTimeRiskForRole(Role role, LocalTime time) {
        return switch (role) {
            case EMPLOYEE -> {
                if (new TimeWindow(
                        LocalTime.of(7, 0),
                        LocalTime.of(19, 0)
                ).contains(time)) {
                    yield NORMAL_POINTS;
                }

                if (new TimeWindow(
                        LocalTime.of(19, 0),
                        LocalTime.of(22, 0)
                ).contains(time)) {
                    yield MEDIUM_POINTS;
                }

                yield HIGH_POINTS;
            }

            case ADMIN -> {
                if (new TimeWindow(
                        LocalTime.of(6, 0),
                        LocalTime.of(23, 0)
                ).contains(time)) {
                    yield NORMAL_POINTS;
                }

                if (new TimeWindow(
                        LocalTime.of(23, 0),
                        LocalTime.of(2, 0)
                ).contains(time)) {
                    yield MEDIUM_POINTS;
                }

                yield HIGH_POINTS;
            }

            case GUEST -> {
                if (new TimeWindow(
                        LocalTime.of(6, 0),
                        LocalTime.of(0, 0)
                ).contains(time)) {
                    yield NORMAL_POINTS;
                }

                if (new TimeWindow(
                        LocalTime.of(0, 0),
                        LocalTime.of(3, 0)
                ).contains(time)) {
                    yield MEDIUM_POINTS;
                }

                yield HIGH_POINTS;
            }

            case ETL_WORKER -> {
                LocalTime expected = LocalTime.of(2, 0);
                long minutesAway = Math.abs(
                        Duration.between(expected, time).toMinutes()
                );

                if (minutesAway <= 30) {
                    yield NORMAL_POINTS;
                }

                if (minutesAway <= 60) {
                    yield MEDIUM_POINTS;
                }

                if (minutesAway <= 180) {
                    yield HIGH_POINTS;
                }

                yield CRITICAL_POINTS;
            }

            case REPORTING_SERVICE -> {
                if (new TimeWindow(
                        LocalTime.of(5, 0),
                        LocalTime.of(22, 0)
                ).contains(time)) {
                    yield NORMAL_POINTS;
                }

                if (new TimeWindow(
                        LocalTime.of(22, 0),
                        LocalTime.of(1, 0)
                ).contains(time)) {
                    yield MEDIUM_POINTS;
                }

                yield HIGH_POINTS;
            }
        };
    }

    public static int evaluateRowCountRisk(AccessEventCreatedMessage message) {
        TablePermission permission = getTablePermission(message.queryType());
        long rowCount = message.rowCount();

        return switch (permission) {
            case READ -> {
                if (rowCount > 100_000) {
                    yield HIGH_POINTS;
                }

                if (rowCount > 10_000) {
                    yield MEDIUM_POINTS;
                }

                yield NORMAL_POINTS;
            }

            case WRITE -> {
                if (rowCount > 10_000) {
                    yield HIGH_POINTS;
                }

                if (rowCount > 1_000) {
                    yield MEDIUM_POINTS;
                }

                yield NORMAL_POINTS;
            }

            case UPDATE -> {
                if (rowCount > 10_000) {
                    yield HIGH_POINTS;
                }

                if (rowCount > 1_000) {
                    yield MEDIUM_POINTS;
                }

                yield NORMAL_POINTS;
            }

            case DELETE -> {
                if (rowCount > 1_000) {
                    yield HIGH_POINTS;
                }

                if (rowCount > 100) {
                    yield MEDIUM_POINTS;
                }

                yield NORMAL_POINTS;
            }
        };
    }

    public static AlertSeverity evaluateAccessEventSeverity(int severityPoints) {
        return severityThresholds.floorEntry(severityPoints).getValue();
    }

    private static Role resolveRole(String username) {
        String normalized = username.toLowerCase(Locale.ROOT);
        Role role = userRoles.get(normalized);
        if (role != null) {
            return role;
        }
        return Role.fromString(username);
    }

    private static Table resolveTable(String tableName) {
        String normalized = tableName.toLowerCase(Locale.ROOT);
        Table table = tableNames.get(normalized);
        if (table != null) {
            return table;
        }
        return Table.fromString(tableName);
    }
}

