package com.guardium_clone.evaluation_service.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardium_clone.evaluation_service.model.AccessEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.model.DatabaseTable;
import com.guardium_clone.evaluation_service.model.DatabaseUser;
import com.guardium_clone.evaluation_service.model.QueryType;
import com.guardium_clone.evaluation_service.repository.AccessEventRepository;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import com.guardium_clone.evaluation_service.repository.DatabaseTableRepository;
import com.guardium_clone.evaluation_service.repository.DatabaseUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessEventRepository accessEventRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DatabaseTableRepository databaseTableRepository;

    @Autowired
    private DatabaseUserRepository databaseUserRepository;

    @BeforeEach
    void resetData() {
        alertRepository.deleteAll();
        accessEventRepository.deleteAll();
        databaseUserRepository.deleteAll();
        databaseTableRepository.deleteAll();
    }

    @Test
    void listAlertsFiltersBySeverityUserTableAndTimeRange() throws Exception {
        createAlert(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                AlertSeverity.HIGH,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T10:00:00Z"),
                Instant.parse("2026-07-02T10:01:00Z")
        );
        createAlert(
                "carol",
                "orders",
                QueryType.DELETE,
                AlertSeverity.LOW,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T11:00:00Z"),
                Instant.parse("2026-07-02T11:01:00Z")
        );

        mockMvc.perform(get("/alerts")
                        .param("severity", "HIGH")
                        .param("username", "alice")
                        .param("tableName", "customer_accounts")
                        .param("createdFrom", "2026-07-02T09:00:00Z")
                        .param("createdTo", "2026-07-02T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.content[0].accessEvent.username").value("alice"))
                .andExpect(jsonPath("$.content[0].accessEvent.tableName").value("customer_accounts"))
                .andExpect(jsonPath("$.content[0].accessEvent.queryText").doesNotExist());
    }


    @Test
    void listAlertsUsesIdAsTieBreakerWhenCreatedAtMatches() throws Exception {
        Instant sameCreatedAt = Instant.parse("2026-07-02T10:01:00Z");
        createAlert(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                AlertSeverity.HIGH,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T10:00:00Z"),
                sameCreatedAt
        );
        createAlert(
                "bob",
                "orders",
                QueryType.SELECT,
                AlertSeverity.HIGH,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T10:00:30Z"),
                sameCreatedAt
        );

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].accessEvent.username").value("bob"))
                .andExpect(jsonPath("$.content[1].accessEvent.username").value("alice"));
    }
    @Test
    void summarizeAlertsReturnsTotalLatestAndCountsForEverySeverity() throws Exception {
        createAlert(
                "alice",
                "orders",
                QueryType.SELECT,
                AlertSeverity.LOW,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T10:00:00Z"),
                Instant.parse("2026-07-02T10:01:00Z")
        );
        createAlert(
                "carol",
                "customer_accounts",
                QueryType.DELETE,
                AlertSeverity.CRITICAL,
                "ACCESS_EVENT_RISK",
                Instant.parse("2026-07-02T11:00:00Z"),
                Instant.parse("2026-07-02T11:01:00Z")
        );

        mockMvc.perform(get("/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(2))
                .andExpect(jsonPath("$.latestAlertCreatedAt").value("2026-07-02T11:01:00Z"))
                .andExpect(jsonPath("$.countsBySeverity.LOW").value(1))
                .andExpect(jsonPath("$.countsBySeverity.MEDIUM").value(0))
                .andExpect(jsonPath("$.countsBySeverity.HIGH").value(0))
                .andExpect(jsonPath("$.countsBySeverity.CRITICAL").value(1));
    }

    private void createAlert(
            String username,
            String tableName,
            QueryType queryType,
            AlertSeverity severity,
            String ruleName,
            Instant occurredAt,
            Instant createdAt
    ) {
        DatabaseUser user = databaseUserRepository.saveAndFlush(new DatabaseUser(username));
        DatabaseTable table = databaseTableRepository.saveAndFlush(new DatabaseTable(tableName, true));
        AccessEvent accessEvent = accessEventRepository.saveAndFlush(new AccessEvent(
                user,
                table,
                queryType,
                occurredAt,
                42,
                "10.0.0.12",
                "select * from " + tableName
        ));
        Alert alert = new Alert(accessEvent, ruleName, severity, username + " accessed " + tableName);
        alert.setCreatedAt(createdAt);
        alertRepository.saveAndFlush(alert);
    }
}

