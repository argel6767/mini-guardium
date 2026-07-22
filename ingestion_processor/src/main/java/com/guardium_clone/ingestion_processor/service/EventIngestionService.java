package com.guardium_clone.ingestion_processor.service;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {

    private static final Logger LOGGER = LogManager.getLogger(EventIngestionService.class);

    private final IngestionEventRepository ingestionEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EventIngestionService(
            IngestionEventRepository ingestionEventRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ingestionEventRepository = ingestionEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IngestionEvent enqueue(IngestEventRequest request) {
        IngestionEvent event = new IngestionEvent(
                request.username(),
                request.tableName(),
                request.queryType(),
                request.occurredAt(),
                request.rowCount(),
                request.sourceIp(),
                request.queryText()
        );

        IngestionEvent savedEvent = ingestionEventRepository.save(event);
        try (CloseableThreadContext.Instance ignored = traceContext(savedEvent.getId())) {
            LOGGER.info(
                    "Accepted ingestion event for username={}, tableName={}, queryType={}",
                    savedEvent.getUsername(),
                    savedEvent.getTableName(),
                    savedEvent.getQueryType()
            );
            eventPublisher.publishEvent(new IngestionQueuedEvent(savedEvent.getId()));
            LOGGER.debug("Published ingestion queue event");
        }
        return savedEvent;
    }

    @Transactional(readOnly = true)
    public IngestionEvent getById(Long ingestionId) {
        return ingestionEventRepository.findById(ingestionId)
                .orElseThrow(() -> new IngestionEventNotFoundException(ingestionId));
    }

    private CloseableThreadContext.Instance traceContext(Long ingestionEventId) {
        String eventId = ingestionEventId.toString();
        return CloseableThreadContext
                .put("requestId", "ingestion-" + eventId)
                .put("ingestionEventId", eventId);
    }
}
