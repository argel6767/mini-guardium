package com.guardium_clone.ingestion_processor.messaging;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import com.guardium_clone.ingestion_processor.api.IngestEventRequest;
import com.guardium_clone.ingestion_processor.model.QueryType;
import com.guardium_clone.ingestion_processor.service.EventIngestionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RawIngestionEventListener {

    private static final Logger LOGGER = LogManager.getLogger(RawIngestionEventListener.class);

    private final EventIngestionService eventIngestionService;
    private final Validator validator;

    public RawIngestionEventListener(EventIngestionService eventIngestionService, Validator validator) {
        this.eventIngestionService = eventIngestionService;
        this.validator = validator;
    }

    @RabbitListener(queues = "${ingestion.raw-events.queue:guardium.ingestion-events.ingestion-processor}")
    public void receive(RawIngestionEventMessage message) {
        try (CloseableThreadContext.Instance ignored = traceContext(message)) {
            IngestEventRequest request = toRequest(message);
            validate(request);
            eventIngestionService.enqueue(request);
            LOGGER.info(
                    "Accepted raw ingestion event from RabbitMQ simulatedEventId={}, username={}, tableName={}, queryType={}",
                    message.simulatedEventId(),
                    request.username(),
                    request.tableName(),
                    request.queryType()
            );
        }
    }

    private IngestEventRequest toRequest(RawIngestionEventMessage message) {
        return new IngestEventRequest(
                message.username(),
                message.tableName(),
                QueryType.valueOf(message.queryType()),
                message.occurredAt(),
                message.rowCount(),
                message.sourceIp(),
                message.queryText()
        );
    }

    private void validate(IngestEventRequest request) {
        Set<ConstraintViolation<IngestEventRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid raw ingestion event: " + violations);
        }
    }

    private CloseableThreadContext.Instance traceContext(RawIngestionEventMessage message) {
        String simulatedEventId = message.simulatedEventId() == null ? "unknown" : message.simulatedEventId().toString();
        return CloseableThreadContext.put("simulatedEventId", simulatedEventId);
    }
}