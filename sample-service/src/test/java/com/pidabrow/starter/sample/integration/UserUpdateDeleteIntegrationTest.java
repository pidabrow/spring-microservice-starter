package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.SystemActor;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.AuditLog;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.data.repository.AuditLogRepository;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.testing.AbstractIntegrationTest;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.application.usecase.DeleteUserUseCase;
import com.pidabrow.starter.sample.application.usecase.UpdateUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.NotificationRequestEntityRepository;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for update and delete flows through real persistence.
 *
 * Verifies:
 * - User not found scenarios (non-existent UUID, tenant filter)
 * - No audit log when update has no changes (no-op)
 * - Cascade delete of notification requests before user
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("User update/delete integration tests")
class UserUpdateDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private UpdateUserUseCase updateUserUseCase;

    @Autowired
    private DeleteUserUseCase deleteUserUseCase;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private NotificationRequestEntityRepository notificationRequestEntityRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            return null;
        });
        tx.execute(status -> {
            Tenant tenant = Tenant.create("Test Tenant");
            tenantId = tenant.getId();
            entityManager.persist(tenant);
            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when updating non-existent user")
    void should_throw_no_such_element_when_updating_non_existent_user() {
        UUID nonExistentId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status ->
                updateUserUseCase.execute(nonExistentId, "new@email.com", null, null, null, null)
        ))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when deleting non-existent user")
    void should_throw_no_such_element_when_deleting_non_existent_user() {
        UUID nonExistentId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            deleteUserUseCase.execute(nonExistentId);
            return null;
        }))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should not create audit log when update has no changes")
    void should_not_create_audit_log_when_update_has_no_changes() {
        // Given: create a user
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        User user = createUserInTransaction();

        // Clear audit logs from creation
        clearAuditLogs();

        // When: update with all nulls (no-op — all fields preserved from existing)
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            updateUserUseCase.execute(user.id(), null, null, null, null, null);
            entityManager.flush();
            return null;
        });

        // Then: no audit log entry created (no UserUpdatedEvent published for no-op)
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).isEmpty();
    }

    @Test
    @DisplayName("Should delete user with associated notification requests")
    void should_delete_user_with_associated_notification_requests() {
        // Given: create a user (which also creates a notification request)
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        User user = createUserInTransaction();

        // Verify notification request exists
        assertThat(notificationRequestEntityRepository.findAll()).isNotEmpty();

        // When: delete the user (should cascade delete notification requests first)
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            deleteUserUseCase.execute(user.id());
            entityManager.flush();
            return null;
        });

        // Then: both user and notification requests are deleted
        assertThat(userEntityRepository.findAll()).isEmpty();
        assertThat(notificationRequestEntityRepository.findAll()).isEmpty();
    }

    private User createUserInTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            User user = createUserUseCase.execute(
                    "test@example.com", "+1234567890", "John", "Doe",
                    new UserPreferences(true, true)
            );
            entityManager.flush();
            return user;
        });
    }

    private void clearAuditLogs() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            return null;
        });
    }
}

