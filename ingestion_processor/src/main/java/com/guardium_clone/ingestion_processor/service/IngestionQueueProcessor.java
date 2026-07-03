package com.guardium_clone.ingestion_processor.service;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.messaging.AccessEventCreatedPublisher;
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
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IngestionQueueProcessor {

    private static final Logger LOGGER = LogManager.getLogger(IngestionQueueProcessor.class);
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final AccessEventRepository accessEventRepository;
    private final AccessEventCreatedPublisher accessEventCreatedPublisher;
    private final DatabaseTableRepository databaseTableRepository;
    private final DatabaseUserRepository databaseUserRepository;
    private final IngestionEventRepository ingestionEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final PriorityBlockingQueue<QueuedIngestionWork> workQueue = new PriorityBlockingQueue<>();
    private final Set<Long> queuedIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    public IngestionQueueProcessor(
            AccessEventRepository accessEventRepository,
            AccessEventCreatedPublisher accessEventCreatedPublisher,
            DatabaseTableRepository databaseTableRepository,
            DatabaseUserRepository databaseUserRepository,
            IngestionEventRepository ingestionEventRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.accessEventRepository = accessEventRepository;
        this.accessEventCreatedPublisher = accessEventCreatedPublisher;
        this.databaseTableRepository = databaseTableRepository;
        this.databaseUserRepository = databaseUserRepository;
        this.ingestionEventRepository = ingestionEventRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueueCommittedEvent(IngestionQueuedEvent event) {
        try (CloseableThreadContext.Instance ignored = traceContext(event.ingestionId())) {
            LOGGER.debug("Received committed ingestion event");
            enqueue(event.ingestionId(), IngestionStatus.PENDING);
            drainQueue();
        }
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
            LOGGER.info(
                    "Queued retry sweep work pendingCount={}, retryableFailedCount={}",
                    pendingEvents.size(),
                    failedEvents.size()
            );
            drainQueue();
        } else {
            LOGGER.debug("Retry sweep found no eligible ingestion events");
        }
    }

    private void enqueue(Long ingestionId, IngestionStatus status) {
        try (CloseableThreadContext.Instance ignored = traceContext(ingestionId)) {
            if (queuedIds.add(ingestionId)) {
                int priority = priorityFor(status);
                workQueue.offer(new QueuedIngestionWork(ingestionId, priority, Instant.now()));
                LOGGER.debug("Queued ingestion work status={}, priority={}", status, priority);
            } else {
                LOGGER.debug("Skipped duplicate ingestion work queue request status={}", status);
            }
        }
    }

    private void drainQueue() {
        if (!workerRunning.compareAndSet(false, true)) {
            LOGGER.debug("Ingestion worker already running; queued work will be drained by active worker");
            return;
        }

        LOGGER.debug("Starting ingestion queue drain");
        int processedWorkItems = 0;
        try {
            QueuedIngestionWork work = workQueue.poll();
            while (work != null) {
                queuedIds.remove(work.ingestionId());
                processQueuedEvent(work.ingestionId());
                processedWorkItems++;
                work = workQueue.poll();
            }
        } finally {
            workerRunning.set(false);
            LOGGER.debug("Finished ingestion queue drain processedWorkItems={}", processedWorkItems);
        }

        if (!workQueue.isEmpty()) {
            LOGGER.debug("Additional ingestion work arrived during drain; restarting drain");
            drainQueue();
        }
    }

    private void processQueuedEvent(Long eventId) {
        try (CloseableThreadContext.Instance ignored = traceContext(eventId)) {
            transactionTemplate.executeWithoutResult(status -> {
                IngestionEvent queuedEvent = ingestionEventRepository.findById(eventId).orElse(null);
                Instant now = Instant.now();
                if (queuedEvent == null) {
                    LOGGER.warn("Skipped ingestion work because the ingestion event no longer exists");
                    return;
                }
                if (!isReadyForProcessing(queuedEvent, now)) {
                    LOGGER.debug(
                            "Skipped ingestion event because it is not ready status={}, retryCount={}, nextAttemptAt={}",
                            queuedEvent.getStatus(),
                            queuedEvent.getRetryCount(),
                            queuedEvent.getNextAttemptAt()
                    );
                    return;
                }

                queuedEvent.setStatus(IngestionStatus.PROCESSING);
                queuedEvent.setLastAttemptAt(now);
                queuedEvent.setNextAttemptAt(null);
                LOGGER.info(
                        "Processing ingestion event status={}, retryCount={}, tableName={}, queryType={}",
                        queuedEvent.getStatus(),
                        queuedEvent.getRetryCount(),
                        queuedEvent.getTableName(),
                        queuedEvent.getQueryType()
                );
                try {
                    DatabaseUser user = databaseUserRepository.findByUsername(queuedEvent.getUsername())
                            .orElseGet(() -> databaseUserRepository.save(new DatabaseUser(queuedEvent.getUsername())));
                    DatabaseTable table = databaseTableRepository.findByName(queuedEvent.getTableName())
                            .orElseGet(() -> databaseTableRepository.save(new DatabaseTable(queuedEvent.getTableName(), false)));
                    Instant occurredAt = queuedEvent.getOccurredAt() != null ? queuedEvent.getOccurredAt() : Instant.now();

                    AccessEvent accessEvent = accessEventRepository.save(new AccessEvent(
                            user,
                            table,
                            queuedEvent.getQueryType(),
                            occurredAt,
                            queuedEvent.getRowCount(),
                            queuedEvent.getSourceIp(),
                            queuedEvent.getQueryText()
                    ));
                    accessEventCreatedPublisher.publish(accessEvent);
                    queuedEvent.setStatus(IngestionStatus.PROCESSED);
                    LOGGER.info("Processed ingestion event");
                } catch (RuntimeException exception) {
                    int retryCount = queuedEvent.getRetryCount() + 1;
                    queuedEvent.setRetryCount(retryCount);
                    queuedEvent.setStatus(IngestionStatus.FAILED);
                    queuedEvent.setNextAttemptAt(nextAttemptAt(retryCount, now));
                    if (queuedEvent.getNextAttemptAt() == null) {
                        LOGGER.error("Ingestion event failed permanently retryCount={}", retryCount, exception);
                    } else {
                        LOGGER.warn(
                                "Ingestion event failed and will be retried retryCount={}, nextAttemptAt={}",
                                retryCount,
                                queuedEvent.getNextAttemptAt(),
                                exception
                        );
                    }
                }
            });
        }
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

    private CloseableThreadContext.Instance traceContext(Long ingestionEventId) {
        String eventId = ingestionEventId.toString();
        return CloseableThreadContext
                .put("requestId", "ingestion-" + eventId)
                .put("ingestionEventId", eventId);
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
