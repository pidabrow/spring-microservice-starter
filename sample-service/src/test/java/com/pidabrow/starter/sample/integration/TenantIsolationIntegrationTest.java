package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.sample.entity.TestEntity;
import com.pidabrow.starter.sample.repository.TestEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving tenant isolation at the persistence layer.
 * 
 * This test verifies that:
 * - Queries return data only for the active tenant context
 * - Cross-tenant data access is not possible without explicitly changing tenant context
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TenantIsolationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestEntityRepository testEntityRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantAId;
    private UUID tenantBId;
    
    private void enableTenantFilter(UUID tenantId) {
        org.hibernate.Session session = entityManager.unwrap(org.hibernate.Session.class);
        org.hibernate.Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
    }

    @BeforeEach
    void setUp() {
        // Create two tenants
        Tenant tenantA = Tenant.create("Tenant A");
        tenantAId = tenantA.getId();
        entityManager.persist(tenantA);

        Tenant tenantB = Tenant.create("Tenant B");
        tenantBId = tenantB.getId();
        entityManager.persist(tenantB);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Transactional
    void should_return_entities_for_active_tenant_context_when_querying() {
        // Given: entities for both tenants
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TestEntity entityA1 = TestEntity.create("Entity A1");
        TestEntity entityA2 = TestEntity.create("Entity A2");
        testEntityRepository.save(entityA1);
        testEntityRepository.save(entityA2);

        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TestEntity entityB1 = TestEntity.create("Entity B1");
        TestEntity entityB2 = TestEntity.create("Entity B2");
        testEntityRepository.save(entityB1);
        testEntityRepository.save(entityB2);

        entityManager.flush();
        entityManager.clear();

        // When: querying with tenant A context
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        enableTenantFilter(tenantAId);
        List<TestEntity> tenantAEntities = testEntityRepository.findAll();

        // Then: only tenant A entities are returned
        assertThat(tenantAEntities).hasSize(2);
        assertThat(tenantAEntities).extracting(TestEntity::getName)
                .containsExactlyInAnyOrder("Entity A1", "Entity A2");
        assertThat(tenantAEntities).extracting(e -> e.getTenantId())
                .containsOnly(tenantAId);
    }

    @Test
    @Transactional
    void should_not_return_entities_from_other_tenants_when_querying() {
        // Given: entities for both tenants
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TestEntity entityA = TestEntity.create("Entity A");
        testEntityRepository.save(entityA);

        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TestEntity entityB = TestEntity.create("Entity B");
        testEntityRepository.save(entityB);

        entityManager.flush();
        entityManager.clear();

        // When: querying with tenant B context
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        enableTenantFilter(tenantBId);
        List<TestEntity> tenantBEntities = testEntityRepository.findAll();

        // Then: only tenant B entities are returned, not tenant A
        assertThat(tenantBEntities).hasSize(1);
        assertThat(tenantBEntities.get(0).getName()).isEqualTo("Entity B");
        assertThat(tenantBEntities.get(0).getTenantId()).isEqualTo(tenantBId);
    }

    @Test
    @Transactional
    void should_not_find_entity_by_id_from_other_tenant_when_querying() {
        // Given: entity for tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        enableTenantFilter(tenantAId);
        TestEntity entityA = TestEntity.create("Entity A");
        testEntityRepository.save(entityA);
        UUID entityAId = entityA.getId();

        entityManager.flush();
        entityManager.clear();

        // When: querying with tenant B context
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        enableTenantFilter(tenantBId);
        var foundEntity = testEntityRepository.findById(entityAId);

        // Then: entity is not found (filtered out by tenant isolation)
        assertThat(foundEntity).isEmpty();
    }

    @Test
    @Transactional
    void should_find_entity_by_id_for_same_tenant_when_querying() {
        // Given: entity for tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TestEntity entityA = TestEntity.create("Entity A");
        testEntityRepository.save(entityA);
        UUID entityAId = entityA.getId();

        entityManager.flush();
        entityManager.clear();

        // When: querying with same tenant context
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        enableTenantFilter(tenantAId);
        var foundEntity = testEntityRepository.findById(entityAId);

        // Then: entity is found
        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getName()).isEqualTo("Entity A");
        assertThat(foundEntity.get().getTenantId()).isEqualTo(tenantAId);
    }

    @Test
    @Transactional
    void should_filter_by_name_only_within_tenant_when_querying() {
        // Given: entities with same name in different tenants
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TestEntity entityA = TestEntity.create("Common Name");
        testEntityRepository.save(entityA);

        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TestEntity entityB = TestEntity.create("Common Name");
        testEntityRepository.save(entityB);

        entityManager.flush();
        entityManager.clear();

        // When: querying by name with tenant A context
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        enableTenantFilter(tenantAId);
        List<TestEntity> found = testEntityRepository.findByName("Common Name");

        // Then: only tenant A entity is returned
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getTenantId()).isEqualTo(tenantAId);
    }
}

