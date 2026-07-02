package com.guardium_clone.ingestion_processor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.repository.AccessEventRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseTableRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseUserRepository;
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

    @Test
    void ingestEventStoresAccessEvent() throws Exception {
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
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.getResponse().getContentAsString()).contains("\"eventId\":");
        assertThat(accessEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getQueryType()).isEqualTo(QueryType.SELECT);
                    assertThat(event.getRowCount()).isEqualTo(42);
                    assertThat(event.getSourceIp()).isEqualTo("10.0.0.12");
                });
        assertThat(databaseUserRepository.findByUsername("alice")).isPresent();
        assertThat(databaseTableRepository.findByName("customer_accounts")).isPresent();
    }
}