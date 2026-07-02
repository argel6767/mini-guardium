package com.guardium_clone.ingestion_processor.repository;

import com.guardium_clone.ingestion_processor.model.AccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessEventRepository extends JpaRepository<AccessEvent, Long> {
}