package com.guardium_clone.ingestion_processor.repository;

import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseTableRepository extends JpaRepository<DatabaseTable, Long> {
    Optional<DatabaseTable> findByName(String name);
}