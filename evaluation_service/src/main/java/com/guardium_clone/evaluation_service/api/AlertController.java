package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.service.AlertDashboardService;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertDashboardService alertDashboardService;

    public AlertController(AlertDashboardService alertDashboardService) {
        this.alertDashboardService = alertDashboardService;
    }

    @GetMapping
    public PagedResponse<AlertResponse> listAlerts(
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @PageableDefault(size = 25, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return alertDashboardService.listAlerts(
                severity,
                ruleName,
                tableName,
                username,
                createdFrom,
                createdTo,
                pageable
        );
    }

    @GetMapping("/summary")
    public AlertSummaryResponse summarizeAlerts() {
        return alertDashboardService.summarizeAlerts();
    }
}


