package com.guardium_clone.evaluation_service.service;

import com.guardium_clone.evaluation_service.events.AlertCreatedEvent;
import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.repository.AccessEventRepository;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import com.guardium_clone.evaluation_service.utils.AccessEventEvaluationUtils;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AccessEventEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessEventEvaluationService.class);
    private static final int LOW_SEVERITY_POINTS = 1;
    private static final String RULE_NAME = "ACCESS_EVENT_RISK";

    private final AccessEventRepository accessEventRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public AccessEventEvaluationService(
            AccessEventRepository accessEventRepository,
            AlertRepository alertRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.accessEventRepository = accessEventRepository;
        this.alertRepository = alertRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void evaluate(AccessEventCreatedMessage message) {
        LOGGER.debug("Access event {} accepted for future alert evaluation", message.accessEventId());
        int severityPoints = 0;

        if (AccessEventEvaluationUtils.isSensitiveTable(message)) {
            severityPoints += LOW_SEVERITY_POINTS;
        }

        if (!AccessEventEvaluationUtils.isUserAllowed(message)) {
            severityPoints += LOW_SEVERITY_POINTS;
        }

        if (AccessEventEvaluationUtils.isUnsafeDelete(message)) {
            severityPoints += LOW_SEVERITY_POINTS;
        }

        int timeRiskPoints = AccessEventEvaluationUtils.evaluateTimeRisk(message);
        int rowCountRiskPoints = AccessEventEvaluationUtils.evaluateRowCountRisk(message);
        severityPoints += timeRiskPoints + rowCountRiskPoints;

        AlertSeverity severity = AccessEventEvaluationUtils.evaluateAccessEventSeverity(severityPoints);
        if (severityPoints == 0) {
            LOGGER.info("Access event {} did not trigger an alert", message.accessEventId());
            return;
        }

        Alert alert = new Alert(
                accessEventRepository.findById(message.accessEventId()).orElseThrow(),
                RULE_NAME,
                severity,
                "Access event %d evaluated with %s severity".formatted(message.accessEventId(), severity)
        );
        Alert savedAlert = alertRepository.save(alert);
        applicationEventPublisher.publishEvent(new AlertCreatedEvent(savedAlert.getId()));

        LOGGER.info("Access event {} severity: {}", message.accessEventId(), severity);
    }
}

