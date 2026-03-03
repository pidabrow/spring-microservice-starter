package com.pidabrow.starter.sample.infrastructure.persistence.repository;

import com.pidabrow.starter.sample.infrastructure.persistence.entity.NotificationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Deletes all notification requests for a given user within the given tenant.
     * Tenant ID is passed explicitly because Hibernate filters do not apply to bulk JPQL DELETE statements.
     */
    @Modifying
    @Query("DELETE FROM NotificationRequestEntity n WHERE n.userId = :userId AND n.tenantId = :tenantId")
    void deleteByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}

