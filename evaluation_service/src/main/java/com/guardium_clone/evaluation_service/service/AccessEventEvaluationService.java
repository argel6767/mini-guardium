package com.guardium_clone.evaluation_service.service;

import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.utils.AccessEventEvaluationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccessEventEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessEventEvaluationService.class);
    private final int LOW_SEVERITY_POINTS = 1;

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
        LOGGER.info("Access event {} severity: {}", message.accessEventId(), severity);
        
    }
}

