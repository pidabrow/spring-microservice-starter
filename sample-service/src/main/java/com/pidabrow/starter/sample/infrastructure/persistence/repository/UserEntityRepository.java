package com.pidabrow.starter.sample.infrastructure.persistence.repository;

import com.pidabrow.starter.sample.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserEntity.
 * Tenant isolation is enforced automatically via Hibernate filter.
 */
@Repository
public interface UserEntityRepository extends JpaRepository<UserEntity, UUID> {
    
    /**
     * Custom query that respects Hibernate filter.
     * EntityManager.find() does not apply Hibernate filters, so we use JPQL query instead.
     */
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findById(@Param("id") @org.springframework.lang.NonNull UUID id);
}

