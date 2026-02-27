package com.pidabrow.starter.sample.infrastructure.persistence.repository;

import com.pidabrow.starter.sample.infrastructure.persistence.entity.NotificationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for NotificationRequestEntity.
 * Tenant isolation is enforced automatically via Hibernate filter.
 */
@Repository
public interface NotificationRequestEntityRepository extends JpaRepository<NotificationRequestEntity, UUID> {
    
    /**
     * Custom query that respects Hibernate filter.
     * EntityManager.find() does not apply Hibernate filters, so we use JPQL query instead.
     */
    @Query("SELECT n FROM NotificationRequestEntity n WHERE n.id = :id")
    Optional<NotificationRequestEntity> findById(@Param("id") @org.springframework.lang.NonNull UUID id);
}

