package com.guardium_clone.ingestion_processor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.repository.AccessEventRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseTableRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseUserRepository;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
}

class IngestionQueueProcessorTests {

    private AccessEventRepository accessEventRepository;
    private DatabaseTableRepository databaseTableRepository;
    private DatabaseUserRepository databaseUserRepository;
    private IngestionEventRepository ingestionEventRepository;
    private IngestionQueueProcessor processor;

    @BeforeEach
    void setUp() {
        accessEventRepository = mock(AccessEventRepository.class);
        databaseTableRepository = mock(DatabaseTableRepository.class);
        databaseUserRepository = mock(DatabaseUserRepository.class);
        ingestionEventRepository = mock(IngestionEventRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        processor = new IngestionQueueProcessor(
                accessEventRepository,
                databaseTableRepository,
                databaseUserRepository,
                ingestionEventRepository,
                transactionTemplate
        );
    }

    @Test
    void committedPendingEventCreatesAccessEventAndMarksIngestionProcessed() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.PENDING);
        event.setOccurredAt(Instant.parse("2026-07-02T22:30:00Z"));
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(databaseUserRepository.findByUsername("alice")).thenReturn(Optional.of(new DatabaseUser("alice")));
        when(databaseTableRepository.findByName("customer_accounts"))
                .thenReturn(Optional.of(new DatabaseTable("customer_accounts", false)));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getLastAttemptAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isNull();

        ArgumentCaptor<AccessEvent> accessEventCaptor = ArgumentCaptor.forClass(AccessEvent.class);
        verify(accessEventRepository).save(accessEventCaptor.capture());
        assertThat(accessEventCaptor.getValue())
                .satisfies(accessEvent -> {
                    assertThat(accessEvent.getUser().getUsername()).isEqualTo("alice");
                    assertThat(accessEvent.getTable().getName()).isEqualTo("customer_accounts");
                    assertThat(accessEvent.getQueryType()).isEqualTo(QueryType.SELECT);
                    assertThat(accessEvent.getOccurredAt()).isEqualTo(Instant.parse("2026-07-02T22:30:00Z"));
                    assertThat(accessEvent.getRowCount()).isEqualTo(42);
                    assertThat(accessEvent.getSourceIp()).isEqualTo("10.0.0.12");
                    assertThat(accessEvent.getQueryText()).isEqualTo("select * from customer_accounts");
                });
    }

    @Test
    void committedPendingEventCreatesMissingUserAndTable() {
        IngestionEvent event = ingestionEvent(1L, "bob", "orders", IngestionStatus.PENDING);
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(databaseUserRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(databaseUserRepository.save(any(DatabaseUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(databaseTableRepository.findByName("orders")).thenReturn(Optional.empty());
        when(databaseTableRepository.save(any(DatabaseTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        ArgumentCaptor<AccessEvent> accessEventCaptor = ArgumentCaptor.forClass(AccessEvent.class);
        verify(accessEventRepository).save(accessEventCaptor.capture());
        assertThat(accessEventCaptor.getValue().getUser().getUsername()).isEqualTo("bob");
        assertThat(accessEventCaptor.getValue().getTable().getName()).isEqualTo("orders");
        assertThat(accessEventCaptor.getValue().getOccurredAt()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
    }

    @Test
    void processingFailureMarksEventFailedAndSchedulesRetryWithBackoff() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.PENDING);
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(databaseUserRepository.findByUsername("alice")).thenReturn(Optional.of(new DatabaseUser("alice")));
        when(databaseTableRepository.findByName("customer_accounts"))
                .thenReturn(Optional.of(new DatabaseTable("customer_accounts", false)));
        doThrow(new RuntimeException("write failed")).when(accessEventRepository).save(any(AccessEvent.class));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastAttemptAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isAfterOrEqualTo(event.getLastAttemptAt().plusSeconds(5));
        assertThat(event.getNextAttemptAt()).isBefore(event.getLastAttemptAt().plusSeconds(8));
    }

    @Test
    void finalRetryFailureDoesNotScheduleAnotherAttempt() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.FAILED);
        event.setRetryCount(4);
        event.setNextAttemptAt(Instant.now().minusSeconds(1));
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(databaseUserRepository.findByUsername("alice")).thenReturn(Optional.of(new DatabaseUser("alice")));
        when(databaseTableRepository.findByName("customer_accounts"))
                .thenReturn(Optional.of(new DatabaseTable("customer_accounts", false)));
        doThrow(new RuntimeException("write failed")).when(accessEventRepository).save(any(AccessEvent.class));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void notDueFailedEventIsSkipped() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.FAILED);
        event.setRetryCount(1);
        event.setNextAttemptAt(Instant.now().plusSeconds(60));
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(accessEventRepository, never()).save(any(AccessEvent.class));
    }

    @Test
    void maxedFailedEventIsSkipped() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.FAILED);
        event.setRetryCount(5);
        event.setNextAttemptAt(Instant.now().minusSeconds(60));
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.FAILED);
        verify(accessEventRepository, never()).save(any(AccessEvent.class));
    }

    @Test
    void completedEventIsSkipped() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.PROCESSED);
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(1L));

        assertThat(event.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
        verify(accessEventRepository, never()).save(any(AccessEvent.class));
    }

    @Test
    void missingEventIsSkipped() {
        when(ingestionEventRepository.findById(404L)).thenReturn(Optional.empty());

        processor.enqueueCommittedEvent(new IngestionQueuedEvent(404L));

        verify(accessEventRepository, never()).save(any(AccessEvent.class));
    }

    @Test
    void sweepQueuesPendingBeforeDueFailuresAndDrainsByPriority() {
        IngestionEvent failed = ingestionEvent(1L, "failed-user", "failed-table", IngestionStatus.FAILED);
        failed.setRetryCount(1);
        failed.setNextAttemptAt(Instant.now().minusSeconds(1));
        IngestionEvent pending = ingestionEvent(2L, "pending-user", "pending-table", IngestionStatus.PENDING);

        when(ingestionEventRepository.findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus.PENDING))
                .thenReturn(List.of(pending));
        when(ingestionEventRepository.findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(),
                anyInt(),
                any(Instant.class)
        )).thenReturn(List.of(failed));
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(ingestionEventRepository.findById(2L)).thenReturn(Optional.of(pending));
        when(databaseUserRepository.findByUsername(any())).thenAnswer(invocation -> Optional.of(new DatabaseUser(invocation.getArgument(0))));
        when(databaseTableRepository.findByName(any())).thenAnswer(invocation -> Optional.of(new DatabaseTable(invocation.getArgument(0), false)));
        List<String> processedUsers = new ArrayList<>();
        when(accessEventRepository.save(any(AccessEvent.class))).thenAnswer(invocation -> {
            AccessEvent accessEvent = invocation.getArgument(0);
            processedUsers.add(accessEvent.getUser().getUsername());
            return accessEvent;
        });

        processor.sweepRetryableEvents();

        assertThat(processedUsers).containsExactly("pending-user", "failed-user");
        assertThat(pending.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
        assertThat(failed.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
    }

    @Test
    void sweepDoesNothingWhenNoEligibleEventsExist() {
        when(ingestionEventRepository.findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus.PENDING))
                .thenReturn(List.of());
        when(ingestionEventRepository.findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(),
                anyInt(),
                any(Instant.class)
        )).thenReturn(List.of());

        processor.sweepRetryableEvents();

        verify(ingestionEventRepository, never()).findById(any());
        verify(accessEventRepository, never()).save(any(AccessEvent.class));
    }

    @Test
    void duplicateQueuedIdsAreOnlyProcessedOnceAtATime() {
        IngestionEvent event = ingestionEvent(1L, "alice", "customer_accounts", IngestionStatus.PENDING);
        when(ingestionEventRepository.findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus.PENDING))
                .thenReturn(List.of(event, event));
        when(ingestionEventRepository.findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(),
                anyInt(),
                any(Instant.class)
        )).thenReturn(List.of());
        when(ingestionEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(databaseUserRepository.findByUsername("alice")).thenReturn(Optional.of(new DatabaseUser("alice")));
        when(databaseTableRepository.findByName("customer_accounts"))
                .thenReturn(Optional.of(new DatabaseTable("customer_accounts", false)));

        processor.sweepRetryableEvents();

        verify(accessEventRepository).save(any(AccessEvent.class));
        assertThat(event.getStatus()).isEqualTo(IngestionStatus.PROCESSED);
    }

    private IngestionEvent ingestionEvent(Long id, String username, String tableName, IngestionStatus status) {
        IngestionEvent event = new IngestionEvent(
                username,
                tableName,
                QueryType.SELECT,
                null,
                42,
                "10.0.0.12",
                "select * from " + tableName
        );
        ReflectionTestUtils.setField(event, "id", id);
        event.setStatus(status);
        return event;
    }
}
