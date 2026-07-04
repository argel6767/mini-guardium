package com.guardium_clone.evaluation_service.repository;

import com.guardium_clone.evaluation_service.model.AccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessEventRepository extends JpaRepository<AccessEvent, Long> {
}
