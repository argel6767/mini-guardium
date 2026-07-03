package com.guardium_clone.ingestion_processor.messaging;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.service.EventIngestionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RawIngestionEventListenerTests {

    private final EventIngestionService eventIngestionService = mock(EventIngestionService.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final RawIngestionEventListener listener = new RawIngestionEventListener(eventIngestionService, validator);

    @Test
    void receiveMapsMessageToIngestionRequest() {
        RawIngestionEventMessage message = message("SELECT", "alice", "customer_accounts", 42);

        listener.receive(message);

        ArgumentCaptor<IngestEventRequest> requestCaptor = ArgumentCaptor.forClass(IngestEventRequest.class);
        verify(eventIngestionService).enqueue(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .isEqualTo(new IngestEventRequest(
                        "alice",
                        "customer_accounts",
                        QueryType.SELECT,
                        Instant.parse("2026-07-02T22:30:00Z"),
                        42,
                        "10.0.0.12",
                        "select * from customer_accounts"
                ));
    }

    @Test
    void receiveThrowsForInvalidMessageSoRabbitRetryCanHandleFailure() {
        RawIngestionEventMessage message = message("SELECT", " ", "customer_accounts", 42);

        assertThatThrownBy(() -> listener.receive(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid raw ingestion event");
        verifyNoInteractions(eventIngestionService);
    }

    @Test
    void receiveThrowsForUnknownQueryTypeSoRabbitRetryCanHandleFailure() {
        RawIngestionEventMessage message = message("TRUNCATE", "alice", "customer_accounts", 42);

        assertThatThrownBy(() -> listener.receive(message))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventIngestionService);
    }

    private RawIngestionEventMessage message(String queryType, String username, String tableName, long rowCount) {
        return new RawIngestionEventMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                username,
                tableName,
                queryType,
                Instant.parse("2026-07-02T22:30:00Z"),
                rowCount,
                "10.0.0.12",
                "select * from customer_accounts"
        );
    }
}