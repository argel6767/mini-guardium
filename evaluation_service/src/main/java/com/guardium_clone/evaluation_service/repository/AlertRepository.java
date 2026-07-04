package com.guardium_clone.evaluation_service.repository;

import com.guardium_clone.evaluation_service.api.AlertSeverityCount;
import com.guardium_clone.evaluation_service.model.Alert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    @Override
    @EntityGraph(attributePaths = {"accessEvent", "accessEvent.user", "accessEvent.table"})
    Page<Alert> findAll(Specification<Alert> specification, Pageable pageable);

    @Query("""
            select a from Alert a
            join fetch a.accessEvent ae
            join fetch ae.user
            join fetch ae.table
            where a.id = :id
            """)
    Optional<Alert> findByIdWithDetails(@Param("id") Long id);

    @Query("select a.severity as severity, count(a) as alertCount from Alert a group by a.severity")
    List<AlertSeverityCount> countAlertsBySeverity();

    @Query("select max(a.createdAt) from Alert a")
    Optional<Instant> findLatestCreatedAt();
}
