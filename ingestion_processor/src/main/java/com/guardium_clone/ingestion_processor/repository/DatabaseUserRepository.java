package com.guardium_clone.ingestion_processor.repository;

import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseUserRepository extends JpaRepository<DatabaseUser, Long> {
    Optional<DatabaseUser> findByUsername(String username);
}