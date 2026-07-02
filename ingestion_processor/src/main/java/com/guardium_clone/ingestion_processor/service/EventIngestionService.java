package com.guardium_clone.ingestion_processor.service;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.repository.AccessEventRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseTableRepository;
import com.guardium_clone.ingestion_processor.repository.DatabaseUserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {

    private final AccessEventRepository accessEventRepository;
    private final DatabaseTableRepository databaseTableRepository;
    private final DatabaseUserRepository databaseUserRepository;

    public EventIngestionService(
            AccessEventRepository accessEventRepository,
            DatabaseTableRepository databaseTableRepository,
            DatabaseUserRepository databaseUserRepository
    ) {
        this.accessEventRepository = accessEventRepository;
        this.databaseTableRepository = databaseTableRepository;
        this.databaseUserRepository = databaseUserRepository;
    }

    @Transactional
    public AccessEvent ingest(IngestEventRequest request) {
        DatabaseUser user = databaseUserRepository.findByUsername(request.username())
                .orElseGet(() -> databaseUserRepository.save(new DatabaseUser(request.username())));
        DatabaseTable table = databaseTableRepository.findByName(request.tableName())
                .orElseGet(() -> databaseTableRepository.save(new DatabaseTable(request.tableName(), false)));

        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();
        AccessEvent event = new AccessEvent(
                user,
                table,
                request.queryType(),
                occurredAt,
                request.rowCount(),
                request.sourceIp(),
                request.queryText()
        );

        return accessEventRepository.save(event);
    }
}