package com.guardium_clone.ingestion_processor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import java.time.Instant;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class EventIngestionServiceTests {

    private IngestionEventRepository ingestionEventRepository;
    private ApplicationEventPublisher eventPublisher;
    private EventIngestionService service;

    @BeforeEach
    void setUp() {
        ingestionEventRepository = mock(IngestionEventRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new EventIngestionService(ingestionEventRepository, eventPublisher);
    }

    @AfterEach
    void clearThreadContext() {
        ThreadContext.clearAll();
    }

    @Test
    void enqueueSavesPendingIngestionEventAndPublishesItsId() {
        IngestEventRequest request = new IngestEventRequest(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
        when(ingestionEventRepository.save(any(IngestionEvent.class))).thenAnswer(invocation -> {
            IngestionEvent saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        IngestionEvent saved = service.enqueue(request);

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getTableName()).isEqualTo("customer_accounts");
        assertThat(saved.getQueryType()).isEqualTo(QueryType.SELECT);
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-07-02T22:30:00Z"));
        assertThat(saved.getRowCount()).isEqualTo(42);
        assertThat(saved.getSourceIp()).isEqualTo("10.0.0.12");
        assertThat(saved.getQueryText()).isEqualTo("select * from customer_accounts");
        assertThat(saved.getStatus()).isEqualTo(IngestionStatus.PENDING);

        ArgumentCaptor<IngestionQueuedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionQueuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().ingestionId()).isEqualTo(99L);
    }

    @Test
    void enqueueAddsRequestMetadataWhilePublishingAndClearsItAfterward() {
        IngestEventRequest request = new IngestEventRequest(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                null,
                42,
                "10.0.0.12",
                null
        );
        when(ingestionEventRepository.save(any(IngestionEvent.class))).thenAnswer(invocation -> {
            IngestionEvent saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 123L);
            return saved;
        });

        whenPublishingEventCaptureThreadContext();

        service.enqueue(request);

        verify(eventPublisher).publishEvent(any(IngestionQueuedEvent.class));
        assertThat(ThreadContext.get("requestId")).isNull();
        assertThat(ThreadContext.get("ingestionEventId")).isNull();
    }

    private void whenPublishingEventCaptureThreadContext() {
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(ThreadContext.get("requestId")).isEqualTo("ingestion-123");
            assertThat(ThreadContext.get("ingestionEventId")).isEqualTo("123");
            return null;
        }).when(eventPublisher).publishEvent(any(IngestionQueuedEvent.class));
    }
}
