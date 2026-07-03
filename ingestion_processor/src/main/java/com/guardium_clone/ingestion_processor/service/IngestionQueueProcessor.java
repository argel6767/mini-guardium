package com.guardium_clone.ingestion_processor.service;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.model.IngestionStatus;
import com.guardium_clone.ingestion_processor.repository.AccessEventRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseTableRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseUserRepository;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IngestionQueueProcessor {

    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final AccessEventRepository accessEventRepository;
    private final DatabaseTableRepository databaseTableRepository;
    private final DatabaseUserRepository databaseUserRepository;
    private final IngestionEventRepository ingestionEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final PriorityBlockingQueue<QueuedIngestionWork> workQueue = new PriorityBlockingQueue<>();
    private final Set<Long> queuedIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    public IngestionQueueProcessor(
            AccessEventRepository accessEventRepository,
            DatabaseTableRepository databaseTableRepository,
            DatabaseUserRepository databaseUserRepository,
            IngestionEventRepository ingestionEventRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.accessEventRepository = accessEventRepository;
        this.databaseTableRepository = databaseTableRepository;
        this.databaseUserRepository = databaseUserRepository;
        this.ingestionEventRepository = ingestionEventRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueueCommittedEvent(IngestionQueuedEvent event) {
        enqueue(event.ingestionId(), IngestionStatus.PENDING);
        drainQueue();
    }

    @Scheduled(fixedDelayString = "${ingestion.worker.sweep-delay-ms:5000}")
    public void sweepRetryableEvents() {
        Instant now = Instant.now();
        List<IngestionEvent> pendingEvents = ingestionEventRepository.findTop50ByStatusOrderByCreatedAtAsc(IngestionStatus.PENDING);
        pendingEvents.forEach(event -> enqueue(event.getId(), event.getStatus()));

        List<IngestionEvent> failedEvents = ingestionEventRepository
                .findTop50ByStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        IngestionStatus.FAILED,
                        MAX_RETRY_COUNT,
                        now
                );
        failedEvents.forEach(event -> enqueue(event.getId(), event.getStatus()));

        if (!pendingEvents.isEmpty() || !failedEvents.isEmpty()) {
            drainQueue();
        }
    }

    private void enqueue(Long ingestionId, IngestionStatus status) {
        if (queuedIds.add(ingestionId)) {
            workQueue.offer(new QueuedIngestionWork(ingestionId, priorityFor(status), Instant.now()));
        }
    }

    private void drainQueue() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            QueuedIngestionWork work = workQueue.poll();
            while (work != null) {
                queuedIds.remove(work.ingestionId());
                processQueuedEvent(work.ingestionId());
                work = workQueue.poll();
            }
        } finally {
            workerRunning.set(false);
        }

        if (!workQueue.isEmpty()) {
            drainQueue();
        }
    }

    private void processQueuedEvent(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            IngestionEvent queuedEvent = ingestionEventRepository.findById(eventId).orElse(null);
            Instant now = Instant.now();
            if (queuedEvent == null || !isReadyForProcessing(queuedEvent, now)) {
                return;
            }

            queuedEvent.setStatus(IngestionStatus.PROCESSING);
            queuedEvent.setLastAttemptAt(now);
            queuedEvent.setNextAttemptAt(null);
            try {
                DatabaseUser user = databaseUserRepository.findByUsername(queuedEvent.getUsername())
                        .orElseGet(() -> databaseUserRepository.save(new DatabaseUser(queuedEvent.getUsername())));
                DatabaseTable table = databaseTableRepository.findByName(queuedEvent.getTableName())
                        .orElseGet(() -> databaseTableRepository.save(new DatabaseTable(queuedEvent.getTableName(), false)));
                Instant occurredAt = queuedEvent.getOccurredAt() != null ? queuedEvent.getOccurredAt() : Instant.now();

                accessEventRepository.save(new AccessEvent(
                        user,
                        table,
                        queuedEvent.getQueryType(),
                        occurredAt,
                        queuedEvent.getRowCount(),
                        queuedEvent.getSourceIp(),
                        queuedEvent.getQueryText()
                ));
                queuedEvent.setStatus(IngestionStatus.PROCESSED);
            } catch (RuntimeException exception) {
                int retryCount = queuedEvent.getRetryCount() + 1;
                queuedEvent.setRetryCount(retryCount);
                queuedEvent.setStatus(IngestionStatus.FAILED);
                queuedEvent.setNextAttemptAt(nextAttemptAt(retryCount, now));
            }
        });
    }

    private boolean isReadyForProcessing(IngestionEvent event, Instant now) {
        if (event.getStatus() == IngestionStatus.PENDING) {
            return true;
        }
        if (event.getStatus() != IngestionStatus.FAILED || event.getRetryCount() >= MAX_RETRY_COUNT) {
            return false;
        }
        return event.getNextAttemptAt() == null || !event.getNextAttemptAt().isAfter(now);
    }

    private Instant nextAttemptAt(int retryCount, Instant attemptAt) {
        if (retryCount >= MAX_RETRY_COUNT) {
            return null;
        }

        long exponent = 1L << Math.min(retryCount - 1, 10);
        long baseDelayMillis = BASE_BACKOFF.toMillis() * exponent;
        long cappedDelayMillis = Math.min(baseDelayMillis, MAX_BACKOFF.toMillis());
        long jitterMillis = ThreadLocalRandom.current().nextLong(Math.max(1, cappedDelayMillis / 2));
        return attemptAt.plusMillis(cappedDelayMillis + jitterMillis);
    }

    private int priorityFor(IngestionStatus status) {
        return status == IngestionStatus.PENDING ? 0 : 1;
    }

    private record QueuedIngestionWork(Long ingestionId, int priority, Instant queuedAt)
            implements Comparable<QueuedIngestionWork> {

        @Override
        public int compareTo(QueuedIngestionWork other) {
            int priorityComparison = Integer.compare(priority, other.priority);
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            int timeComparison = queuedAt.compareTo(other.queuedAt);
            if (timeComparison != 0) {
                return timeComparison;
            }
            return Long.compare(ingestionId, other.ingestionId);
        }
    }
}
