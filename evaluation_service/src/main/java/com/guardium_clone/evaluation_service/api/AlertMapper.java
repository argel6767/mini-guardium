package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AccessEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    public AlertResponse toResponse(Alert alert) {
        AccessEvent accessEvent = alert.getAccessEvent();
        return new AlertResponse(
                alert.getId(),
                alert.getRuleName(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getCreatedAt(),
                new AlertAccessEventResponse(
                        accessEvent.getId(),
                        accessEvent.getUser().getUsername(),
                        accessEvent.getTable().getName(),
                        accessEvent.getQueryType(),
                        accessEvent.getOccurredAt(),
                        accessEvent.getRowCount(),
                        accessEvent.getSourceIp()
                )
        );
    }

    public AlertSeverityEventResponse toSeverityEvent(Alert alert) {
        return new AlertSeverityEventResponse(
                alert.getId(),
                alert.getSeverity(),
                alert.getRuleName(),
                alert.getCreatedAt()
        );
    }
}
