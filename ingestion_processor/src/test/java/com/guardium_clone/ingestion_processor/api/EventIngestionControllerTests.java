package com.guardium_clone.ingestion_processor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guardium_clone.ingestion_processor.messaging.AccessEventCreatedPublisher;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.repository.AccessEventRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseTableRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseUserRepository;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIngestionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessEventRepository accessEventRepository;

    @Autowired
    private DatabaseTableRepository databaseTableRepository;

    @Autowired
    private DatabaseUserRepository databaseUserRepository;

    @Autowired
    private IngestionEventRepository ingestionEventRepository;

    @MockitoBean
    private AccessEventCreatedPublisher accessEventCreatedPublisher;

    @BeforeEach
    void cleanDatabase() {
        accessEventRepository.deleteAll();
        ingestionEventRepository.deleteAll();
        databaseTableRepository.deleteAll();
        databaseUserRepository.deleteAll();
    }

    @Test
    void ingestEventQueuesEventForProcessing() throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "tableName": "customer_accounts",
                                  "queryType": "SELECT",
                                  "occurredAt": "2026-07-02T22:30:00Z",
                                  "rowCount": 42,
                                  "sourceIp": "10.0.0.12",
                                  "queryText": "select * from customer_accounts"
                                }
                                """))
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(responseBody).contains("\"ingestionId\":");
        assertThat(responseBody).contains("\"status\":\"PENDING\"");
        assertThat(responseBody).contains("\"acceptedAt\":");
        assertThat(responseBody).doesNotContain(
                "\"username\"",
                "\"tableName\"",
                "\"queryText\"",
                "\"user\"",
                "\"table\"",
                "\"sensitive\"",
                "\"id\""
        );

        waitForProcessedIngestionEvent();

        assertThat(ingestionEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getUsername()).isEqualTo("alice");
                    assertThat(event.getTableName()).isEqualTo("customer_accounts");
                    assertThat(event.getQueryType()).isEqualTo(QueryType.SELECT);
                    assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-07-02T22:30:00Z"));
                    assertThat(event.getRowCount()).isEqualTo(42);
                    assertThat(event.getSourceIp()).isEqualTo("10.0.0.12");
                    assertThat(event.getQueryText()).isEqualTo("select * from customer_accounts");
                    assertThat(event.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
                    assertThat(event.getCreatedAt()).isNotNull();
                    assertThat(event.getUpdatedAt()).isNotNull();
                });
        assertThat(accessEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getQueryType()).isEqualTo(QueryType.SELECT);
                    assertThat(event.getRowCount()).isEqualTo(42);
                    assertThat(event.getSourceIp()).isEqualTo("10.0.0.12");
                    assertThat(event.getQueryText()).isEqualTo("select * from customer_accounts");
                });
        assertThat(databaseUserRepository.findByUsername("alice")).isPresent();
        assertThat(databaseTableRepository.findByName("customer_accounts")).isPresent();
        verify(accessEventCreatedPublisher).publish(any());
    }

    @Test
    void ingestEventRejectsMissingRequiredFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "occurredAt": "2026-07-02T22:30:00Z",
                                  "rowCount": 42,
                                  "queryText": "select * from customer_accounts"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(ingestionEventRepository.findAll()).isEmpty();
        assertThat(accessEventRepository.findAll()).isEmpty();
    }

    @Test
    void actuatorHealthDoesNotRequireAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void ingestEventRejectsBlankFieldsAndNegativeRowCount() throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "tableName": " ",
                                  "queryType": "SELECT",
                                  "occurredAt": "2026-07-02T22:30:00Z",
                                  "rowCount": -1,
                                  "sourceIp": " ",
                                  "queryText": "select * from customer_accounts"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(ingestionEventRepository.findAll()).isEmpty();
        assertThat(accessEventRepository.findAll()).isEmpty();
    }

    @Test
    void getEventReturnsStatusWithoutPayloadOrRetryInternals() throws Exception {
        IngestionEvent event = ingestionEventRepository.save(new IngestionEvent(
                "alice", "customer_accounts", QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"), 42, "10.0.0.12",
                "select * from customer_accounts"
        ));

        MvcResult result = mockMvc.perform(get("/events/{ingestionId}", event.getId())).andReturn();
        String responseBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(responseBody).contains("\"ingestionId\":" + event.getId());
        assertThat(responseBody).contains("\"status\":\"PENDING\"");
        assertThat(responseBody).contains("\"acceptedAt\":", "\"updatedAt\":");
        assertThat(responseBody).doesNotContain(
                "\"username\"", "\"tableName\"", "\"queryType\"", "\"rowCount\"",
                "\"sourceIp\"", "\"queryText\"", "\"retryCount\"",
                "\"lastAttemptAt\"", "\"nextAttemptAt\""
        );
    }

    @Test
    void getEventReturnsNotFoundForUnknownId() throws Exception {
        MvcResult result = mockMvc.perform(get("/events/{ingestionId}", Long.MAX_VALUE)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
    private void waitForProcessedIngestionEvent() throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean processed = ingestionEventRepository.findAll().stream()
                    .anyMatch(event -> event.getStatus() == IngestionStatus.PROCESSED);
            if (processed) {
                return;
            }
            Thread.sleep(25);
        }
    }
}
