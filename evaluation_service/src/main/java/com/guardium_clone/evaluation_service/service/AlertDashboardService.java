package com.guardium_clone.evaluation_service.service;

import com.guardium_clone.evaluation_service.api.AlertMapper;
import com.guardium_clone.evaluation_service.api.AlertResponse;
import com.guardium_clone.evaluation_service.api.AlertSeverityCount;
import com.guardium_clone.evaluation_service.api.AlertSummaryResponse;
import com.guardium_clone.evaluation_service.api.PagedResponse;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertDashboardService {

    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;

    public AlertDashboardService(AlertRepository alertRepository, AlertMapper alertMapper) {
        this.alertRepository = alertRepository;
        this.alertMapper = alertMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<AlertResponse> listAlerts(
            AlertSeverity severity,
            String ruleName,
            String tableName,
            String username,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable
    ) {
        return PagedResponse.from(alertRepository.findAll(
                alertFilter(severity, blankToNull(ruleName), blankToNull(tableName), blankToNull(username), createdFrom, createdTo),
                pageable
        ).map(alertMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public AlertSummaryResponse summarizeAlerts() {
        Map<AlertSeverity, Long> countsBySeverity = new EnumMap<>(AlertSeverity.class);
        for (AlertSeverity severity : AlertSeverity.values()) {
            countsBySeverity.put(severity, 0L);
        }
        for (AlertSeverityCount severityCount : alertRepository.countAlertsBySeverity()) {
            countsBySeverity.put(severityCount.getSeverity(), severityCount.getAlertCount());
        }

        return new AlertSummaryResponse(
                alertRepository.count(),
                alertRepository.findLatestCreatedAt().orElse(null),
                countsBySeverity
        );
    }

    private Specification<Alert> alertFilter(
            AlertSeverity severity,
            String ruleName,
            String tableName,
            String username,
            Instant createdFrom,
            Instant createdTo
    ) {
        return (root, query, criteriaBuilder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (severity != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), severity));
            }
            if (ruleName != null) {
                predicates.add(criteriaBuilder.equal(root.get("ruleName"), ruleName));
            }
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (tableName != null || username != null) {
                Join<Object, Object> accessEvent = root.join("accessEvent");
                if (tableName != null) {
                    predicates.add(criteriaBuilder.equal(accessEvent.join("table").get("name"), tableName));
                }
                if (username != null) {
                    predicates.add(criteriaBuilder.equal(accessEvent.join("user").get("username"), username));
                }
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
