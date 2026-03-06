package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.SystemActor;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.application.usecase.FindUsersUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for FindUsersUseCase with real persistence.
 *
 * Verifies:
 * - findAll() returns only users for the active tenant (not other tenants' users)
 * - findById() does not return users from another tenant
 * - Tenant isolation is enforced at the database level via Hibernate filter
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FindUsersUseCase integration tests")
class FindUsersIntegrationTest {

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
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private FindUsersUseCase findUsersUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Clean up
        tx.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            return null;
        });
        // Create two tenants
        tx.execute(status -> {
            Tenant tenantA = Tenant.create("Tenant A");
            tenantAId = tenantA.getId();
            entityManager.persist(tenantA);

            Tenant tenantB = Tenant.create("Tenant B");
            tenantBId = tenantB.getId();
            entityManager.persist(tenantB);

            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }

    @Test
    @DisplayName("Should return only users belonging to the active tenant when three users exist across two tenants")
    void should_return_only_users_for_active_tenant_when_multiple_tenants_have_users() {
        // Given: 2 users for tenant A, 1 user for tenant B (3 total in DB)
        ActorContextHolder.setContext(SystemActor.instance());

        createUserForTenant(tenantAId, "alice@example.com", "Alice", "Smith");
        createUserForTenant(tenantAId, "bob@example.com", "Bob", "Jones");
        createUserForTenant(tenantBId, "charlie@example.com", "Charlie", "Brown");

        // When: findAll() as tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<User> tenantAUsers = tx.execute(status -> findUsersUseCase.findAll());

        // Then: only 2 users returned (not 3)
        assertThat(tenantAUsers).hasSize(2);
        assertThat(tenantAUsers).extracting(User::email)
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
        assertThat(tenantAUsers).extracting(User::tenantId)
                .containsOnly(tenantAId);

        // When: findAll() as tenant B
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        List<User> tenantBUsers = tx.execute(status -> findUsersUseCase.findAll());

        // Then: only 1 user returned
        assertThat(tenantBUsers).hasSize(1);
        assertThat(tenantBUsers.get(0).email()).isEqualTo("charlie@example.com");
        assertThat(tenantBUsers.get(0).tenantId()).isEqualTo(tenantBId);
    }

    @Test
    @DisplayName("Should not find user by ID when it belongs to another tenant")
    void should_not_find_user_by_id_when_it_belongs_to_another_tenant() {
        // Given: user created for tenant A
        ActorContextHolder.setContext(SystemActor.instance());
        User userA = createUserForTenant(tenantAId, "secret@example.com", "Secret", "User");

        // When: findById() as tenant B
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Then: user not visible — NoSuchElementException
        assertThatThrownBy(() -> tx.execute(status -> findUsersUseCase.findById(userA.id())))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should find user by ID when it belongs to the same tenant")
    void should_find_user_by_id_when_it_belongs_to_same_tenant() {
        // Given: user created for tenant A
        ActorContextHolder.setContext(SystemActor.instance());
        User userA = createUserForTenant(tenantAId, "alice@example.com", "Alice", "Smith");

        // When: findById() as tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User found = tx.execute(status -> findUsersUseCase.findById(userA.id()));

        // Then: user found
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(userA.id());
        assertThat(found.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Should return empty list when tenant has no users but other tenant does")
    void should_return_empty_list_when_tenant_has_no_users_but_other_tenant_does() {
        // Given: users only for tenant A
        ActorContextHolder.setContext(SystemActor.instance());
        createUserForTenant(tenantAId, "alice@example.com", "Alice", "Smith");

        // When: findAll() as tenant B (which has no users)
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<User> tenantBUsers = tx.execute(status -> findUsersUseCase.findAll());

        // Then: empty list (not tenant A's users)
        assertThat(tenantBUsers).isEmpty();
    }

    private User createUserForTenant(UUID tenantId, String email, String firstName, String lastName) {
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            User user = createUserUseCase.execute(
                    email, "+1234567890", firstName, lastName,
                    new UserPreferences(true, true)
            );
            entityManager.flush();
            return user;
        });
    }
}

