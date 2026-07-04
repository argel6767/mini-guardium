package com.guardium_clone.evaluation_service.repository;

import com.guardium_clone.evaluation_service.model.DatabaseTable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseTableRepository extends JpaRepository<DatabaseTable, Long> {
    Optional<DatabaseTable> findByName(String name);
}
