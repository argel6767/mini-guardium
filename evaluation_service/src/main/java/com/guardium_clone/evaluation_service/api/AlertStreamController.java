package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.service.AlertStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/alerts/stream")
public class AlertStreamController {

    private final AlertStreamService alertStreamService;

    public AlertStreamController(AlertStreamService alertStreamService) {
        this.alertStreamService = alertStreamService;
    }

    @GetMapping(path = "/severity", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeverityAlerts() {
        return alertStreamService.openSeverityStream();
    }

    @GetMapping(path = "/batches", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlertBatches() {
        return alertStreamService.openBatchStream();
    }

    @GetMapping(path = "/rates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlertRates() {
        return alertStreamService.openRateStream();
    }
}
