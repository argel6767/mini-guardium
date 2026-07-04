package com.guardium_clone.evaluation_service.repository;

import com.guardium_clone.evaluation_service.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
}
