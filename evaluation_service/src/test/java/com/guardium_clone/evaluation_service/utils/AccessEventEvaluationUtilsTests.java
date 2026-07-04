package com.guardium_clone.evaluation_service.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AccessEventEvaluationUtilsTests {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 7, 2);

    @ParameterizedTest
    @MethodSource("sensitiveTableCases")
    void isSensitiveTableIdentifiesConfiguredSensitiveTables(String tableName, boolean expected) {
        assertThat(AccessEventEvaluationUtils.isSensitiveTable(message("EMPLOYEE", tableName, "SELECT", 1, "SELECT * FROM " + tableName)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("allowedAccessCases")
    void isUserAllowedMatchesRoleTablePermissionMatrix(String role, String tableName, String queryType, boolean expected) {
        assertThat(AccessEventEvaluationUtils.isUserAllowed(message(role, tableName, queryType, 1, queryType + " query")))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("queryTypePermissionCases")
    void isUserAllowedMapsEachQueryTypeToItsRequiredPermission(String queryType, boolean expected) {
        assertThat(AccessEventEvaluationUtils.isUserAllowed(message("EMPLOYEE", "CUSTOMERS_ACCOUNTS", queryType, 1, queryType + " query")))
                .isEqualTo(expected);
    }

    @Test
    void isUserAllowedRejectsUnknownQueryType() {
        AccessEventCreatedMessage message = message("EMPLOYEE", "ORDERS", "TRUNCATE", 1, "TRUNCATE TABLE orders");

        assertThatThrownBy(() -> AccessEventEvaluationUtils.isUserAllowed(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown query type: TRUNCATE");
    }

    @ParameterizedTest
    @MethodSource("unsafeDeleteCases")
    void isUnsafeDeleteDetectsDeleteStatementsWithoutWhereClause(String queryType, String queryText, boolean expected) {
        assertThat(AccessEventEvaluationUtils.isUnsafeDelete(message("ADMIN", "ORDERS", queryType, 1, queryText)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("timeRiskCases")
    void evaluateTimeRiskUsesRoleSpecificWindows(String role, LocalTime localTime, int expectedPoints) {
        assertThat(AccessEventEvaluationUtils.evaluateTimeRisk(message(role, "ORDERS", "SELECT", 1, "SELECT * FROM orders", localTime)))
                .isEqualTo(expectedPoints);
    }

    @ParameterizedTest
    @MethodSource("rowCountRiskCases")
    void evaluateRowCountRiskUsesQueryTypeSpecificThresholds(String queryType, long rowCount, int expectedPoints) {
        assertThat(AccessEventEvaluationUtils.evaluateRowCountRisk(message("ADMIN", "ORDERS", queryType, rowCount, queryType + " query")))
                .isEqualTo(expectedPoints);
    }

    @Test
    void evaluateRowCountRiskRejectsUnknownQueryType() {
        AccessEventCreatedMessage message = message("ADMIN", "ORDERS", "TRUNCATE", 1, "TRUNCATE TABLE orders");

        assertThatThrownBy(() -> AccessEventEvaluationUtils.evaluateRowCountRisk(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown query type: TRUNCATE");
    }

    @ParameterizedTest
    @MethodSource("severityThresholdCases")
    void evaluateAccessEventSeverityUsesFloorThresholds(int points, AlertSeverity expected) {
        assertThat(AccessEventEvaluationUtils.evaluateAccessEventSeverity(points)).isEqualTo(expected);
    }

    private static Stream<Arguments> sensitiveTableCases() {
        return Stream.of(
                Arguments.of("CUSTOMERS_ACCOUNTS", true),
                Arguments.of("ORDERS", false),
                Arguments.of("AUDIT_LOGS", true),
                Arguments.of("EMPLOYEES_RECORDS", true),
                Arguments.of("INVENTORY", true)
        );
    }

    private static Stream<Arguments> allowedAccessCases() {
        return Stream.of(
                Arguments.of("ADMIN", "CUSTOMERS_ACCOUNTS", "DELETE", true),
                Arguments.of("ADMIN", "ORDERS", "UPDATE", true),
                Arguments.of("ADMIN", "AUDIT_LOGS", "INSERT", true),
                Arguments.of("ADMIN", "EMPLOYEES_RECORDS", "SELECT", true),
                Arguments.of("ADMIN", "INVENTORY", "DELETE", true),
                Arguments.of("EMPLOYEE", "CUSTOMERS_ACCOUNTS", "SELECT", true),
                Arguments.of("EMPLOYEE", "CUSTOMERS_ACCOUNTS", "DELETE", false),
                Arguments.of("EMPLOYEE", "ORDERS", "DELETE", true),
                Arguments.of("EMPLOYEE", "AUDIT_LOGS", "UPDATE", false),
                Arguments.of("EMPLOYEE", "EMPLOYEES_RECORDS", "INSERT", false),
                Arguments.of("EMPLOYEE", "INVENTORY", "UPDATE", true),
                Arguments.of("GUEST", "CUSTOMERS_ACCOUNTS", "SELECT", false),
                Arguments.of("GUEST", "ORDERS", "SELECT", true),
                Arguments.of("GUEST", "AUDIT_LOGS", "SELECT", false),
                Arguments.of("GUEST", "EMPLOYEES_RECORDS", "SELECT", false),
                Arguments.of("GUEST", "INVENTORY", "SELECT", true),
                Arguments.of("GUEST", "INVENTORY", "DELETE", false),
                Arguments.of("ETL_WORKER", "CUSTOMERS_ACCOUNTS", "UPDATE", true),
                Arguments.of("ETL_WORKER", "ORDERS", "DELETE", false),
                Arguments.of("ETL_WORKER", "AUDIT_LOGS", "INSERT", true),
                Arguments.of("ETL_WORKER", "EMPLOYEES_RECORDS", "UPDATE", true),
                Arguments.of("ETL_WORKER", "INVENTORY", "SELECT", true),
                Arguments.of("REPORTING_SERVICE", "CUSTOMERS_ACCOUNTS", "SELECT", false),
                Arguments.of("REPORTING_SERVICE", "ORDERS", "SELECT", true),
                Arguments.of("REPORTING_SERVICE", "AUDIT_LOGS", "INSERT", false),
                Arguments.of("REPORTING_SERVICE", "EMPLOYEES_RECORDS", "SELECT", true),
                Arguments.of("REPORTING_SERVICE", "INVENTORY", "UPDATE", false)
        );
    }

    private static Stream<Arguments> queryTypePermissionCases() {
        return Stream.of(
                Arguments.of("SELECT", true),
                Arguments.of("INSERT", true),
                Arguments.of("UPDATE", true),
                Arguments.of("DELETE", false)
        );
    }

    private static Stream<Arguments> unsafeDeleteCases() {
        return Stream.of(
                Arguments.of("DELETE", "DELETE FROM orders", true),
                Arguments.of("DELETE", "DELETE FROM orders WHERE id = 1", false),
                Arguments.of("SELECT", "SELECT * FROM orders", false)
        );
    }

    private static Stream<Arguments> timeRiskCases() {
        return Stream.of(
                Arguments.of("EMPLOYEE", LocalTime.of(8, 0), 0),
                Arguments.of("EMPLOYEE", LocalTime.of(20, 0), 3),
                Arguments.of("EMPLOYEE", LocalTime.of(23, 0), 6),
                Arguments.of("ADMIN", LocalTime.of(12, 0), 0),
                Arguments.of("ADMIN", LocalTime.of(23, 30), 3),
                Arguments.of("ADMIN", LocalTime.of(3, 0), 6),
                Arguments.of("GUEST", LocalTime.of(7, 0), 0),
                Arguments.of("GUEST", LocalTime.of(1, 0), 3),
                Arguments.of("GUEST", LocalTime.of(4, 0), 6),
                Arguments.of("ETL_WORKER", LocalTime.of(2, 15), 0),
                Arguments.of("ETL_WORKER", LocalTime.of(3, 0), 3),
                Arguments.of("ETL_WORKER", LocalTime.of(5, 0), 6),
                Arguments.of("ETL_WORKER", LocalTime.of(8, 0), 10),
                Arguments.of("REPORTING_SERVICE", LocalTime.of(9, 0), 0),
                Arguments.of("REPORTING_SERVICE", LocalTime.of(23, 0), 3),
                Arguments.of("REPORTING_SERVICE", LocalTime.of(2, 0), 6)
        );
    }

    private static Stream<Arguments> rowCountRiskCases() {
        return Stream.of(
                Arguments.of("SELECT", 10_000L, 0),
                Arguments.of("SELECT", 10_001L, 3),
                Arguments.of("SELECT", 100_001L, 6),
                Arguments.of("INSERT", 1_000L, 0),
                Arguments.of("INSERT", 1_001L, 3),
                Arguments.of("INSERT", 10_001L, 6),
                Arguments.of("UPDATE", 1_000L, 0),
                Arguments.of("UPDATE", 1_001L, 3),
                Arguments.of("UPDATE", 10_001L, 6),
                Arguments.of("DELETE", 100L, 0),
                Arguments.of("DELETE", 101L, 3),
                Arguments.of("DELETE", 1_001L, 6)
        );
    }

    private static Stream<Arguments> severityThresholdCases() {
        return Stream.of(
                Arguments.of(0, AlertSeverity.LOW),
                Arguments.of(1, AlertSeverity.LOW),
                Arguments.of(2, AlertSeverity.LOW),
                Arguments.of(3, AlertSeverity.MEDIUM),
                Arguments.of(5, AlertSeverity.MEDIUM),
                Arguments.of(6, AlertSeverity.HIGH),
                Arguments.of(9, AlertSeverity.HIGH),
                Arguments.of(10, AlertSeverity.CRITICAL),
                Arguments.of(14, AlertSeverity.CRITICAL)
        );
    }

    private static AccessEventCreatedMessage message(String role, String tableName, String queryType, long rowCount, String queryText) {
        return message(role, tableName, queryType, rowCount, queryText, LocalTime.of(12, 0));
    }

    private static AccessEventCreatedMessage message(
            String role,
            String tableName,
            String queryType,
            long rowCount,
            String queryText,
            LocalTime localTime
    ) {
        return new AccessEventCreatedMessage(
                1L,
                role,
                tableName,
                queryType,
                instantAtNewYork(localTime),
                rowCount,
                "10.0.0.12",
                queryText
        );
    }

    private static Instant instantAtNewYork(LocalTime localTime) {
        return ZonedDateTime.of(EVENT_DATE, localTime, NEW_YORK).toInstant();
    }
}
