package com.guardium_clone.ingestion_processor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
