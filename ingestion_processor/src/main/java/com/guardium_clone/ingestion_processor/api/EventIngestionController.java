package com.guardium_clone.ingestion_processor.api;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.service.EventIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventIngestionController {

    private final EventIngestionService eventIngestionService;

    public EventIngestionController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestEventResponse ingestEvent(@RequestBody IngestEventRequest request) {
        AccessEvent event = eventIngestionService.ingest(request);
        return new IngestEventResponse(event.getId());
    }
}