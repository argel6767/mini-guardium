package com.guardium_clone.ingestion_processor.service;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.IngestionEvent;
import com.guardium_clone.ingestion_processor.repository.IngestionEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {

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
        eventPublisher.publishEvent(new IngestionQueuedEvent(savedEvent.getId()));
        return savedEvent;
    }
}
