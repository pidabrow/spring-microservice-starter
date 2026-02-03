package com.pidabrow.starter.sample.repository;

import com.pidabrow.starter.sample.entity.TestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TestEntity.
 * Tenant isolation is enforced automatically via Hibernate filter.
 * 
 * Note: Custom query for findById is used because EntityManager.find() 
 * does not respect Hibernate filters. Using JPQL query ensures filter is applied.
 */
@Repository
public interface TestEntityRepository extends JpaRepository<TestEntity, UUID> {
    
    List<TestEntity> findByName(String name);
    
    /**
     * Custom query that respects Hibernate filter.
     * EntityManager.find() does not apply Hibernate filters, so we use JPQL query instead.
     */
    @Query("SELECT e FROM TestEntity e WHERE e.id = :id")
    Optional<TestEntity> findById(@Param("id") UUID id);
}

