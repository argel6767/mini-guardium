package com.guardium_clone.evaluation_service.api;

import com.guardium_clone.evaluation_service.model.AlertSeverity;

public interface AlertSeverityCount {

    AlertSeverity getSeverity();

    long getAlertCount();
}
