package com.guardium_clone.evaluation_service.repository;

import com.guardium_clone.evaluation_service.api.AlertSeverityCount;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query(
            value = """
                    select a from Alert a
                    join fetch a.accessEvent ae
                    join fetch ae.user u
                    join fetch ae.table t
                    where (:severity is null or a.severity = :severity)
                    and (:ruleName is null or a.ruleName = :ruleName)
                    and (:tableName is null or t.name = :tableName)
                    and (:username is null or u.username = :username)
                    and (:createdFrom is null or a.createdAt >= :createdFrom)
                    and (:createdTo is null or a.createdAt <= :createdTo)
                    """,
            countQuery = """
                    select count(a) from Alert a
                    join a.accessEvent ae
                    join ae.user u
                    join ae.table t
                    where (:severity is null or a.severity = :severity)
                    and (:ruleName is null or a.ruleName = :ruleName)
                    and (:tableName is null or t.name = :tableName)
                    and (:username is null or u.username = :username)
                    and (:createdFrom is null or a.createdAt >= :createdFrom)
                    and (:createdTo is null or a.createdAt <= :createdTo)
                    """
    )
    Page<Alert> findDashboardAlerts(
            @Param("severity") AlertSeverity severity,
            @Param("ruleName") String ruleName,
            @Param("tableName") String tableName,
            @Param("username") String username,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable
    );

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
