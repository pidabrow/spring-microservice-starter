package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.UserActor;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.AuditLog;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.data.repository.AuditLogRepository;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.application.usecase.DeleteUserUseCase;
import com.pidabrow.starter.sample.application.usecase.UpdateUserUseCase;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for user domain events and audit logging.
 * 
 * Verifies:
 * - UserCreatedEvent -> AuditLog entry exists
 * - UserUpdatedEvent with JSON Patch -> AuditLog shows delta
 * - UserDeletedEvent -> AuditLog entry exists
 * - NotificationRequestedEvent -> AuditLog entry exists
 * - All events correctly propagate tenantId to audit logs
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserEventsAuditIntegrationTest {

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
    private UpdateUserUseCase updateUserUseCase;

    @Autowired
    private DeleteUserUseCase deleteUserUseCase;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        transactionTemplate.execute(status -> {
            Tenant tenant = Tenant.create("Test Tenant");
            tenantId = tenant.getId();
            entityManager.persist(tenant);
            entityManager.flush();
            entityManager.clear();
            return null;
        });
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should create audit log entry for UserCreatedEvent")
    void should_create_audit_log_entry_for_user_created_event() {
        // Given: tenant context and actor context are set
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(UserActor.of(userId));

        UserPreferences preferences = new UserPreferences(true, true);

        // When: creating a user and committing transaction
        publishUserCreationInTransaction(preferences);

        // Then: audit log entry is created for UserCreatedEvent
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSizeGreaterThanOrEqualTo(1);

        AuditLog userCreatedLog = auditLogs.stream()
                .filter(log -> log.getEntityType().equals("User") && log.getAction().equals("CREATE"))
                .findFirst()
                .orElseThrow();
        assertThat(userCreatedLog.getTenantId()).isEqualTo(tenantId);
        assertThat(userCreatedLog.getAction()).isEqualTo("CREATE");
        assertThat(userCreatedLog.getActorType()).isEqualTo(AuditLog.ActorType.USER);
        assertThat(userCreatedLog.getActorId()).isEqualTo(userId);
        assertThat(userCreatedLog.getEventClassName()).contains("UserCreatedEvent");
    }

    @Test
    @DisplayName("Should create audit log entry for NotificationRequestedEvent")
    void should_create_audit_log_entry_for_notification_requested_event() {
        // Given: tenant context and actor context are set
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(UserActor.of(userId));

        UserPreferences preferences = new UserPreferences(true, true);

        // When: creating a user (which also creates a notification request) and committing
        publishUserCreationInTransaction(preferences);

        // Then: audit log entry is created for NotificationRequestedEvent
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSizeGreaterThanOrEqualTo(2);

        AuditLog notificationLog = auditLogs.stream()
                .filter(log -> log.getEntityType().equals("NotificationRequest") && log.getAction().equals("CREATE"))
                .findFirst()
                .orElseThrow();
        assertThat(notificationLog.getTenantId()).isEqualTo(tenantId);
        assertThat(notificationLog.getAction()).isEqualTo("CREATE");
        assertThat(notificationLog.getEventClassName()).contains("NotificationRequestedEvent");
    }

    @Test
    @DisplayName("Should create audit log entry with JSON Patch delta for UserUpdatedEvent")
    void should_create_audit_log_entry_with_json_patch_delta_for_user_updated_event() {
        // Given: tenant context and actor context are set, and a user exists
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(UserActor.of(userId));

        UserPreferences preferences = new UserPreferences(true, true);
        User user = publishUserCreationInTransaction(preferences);

        // Clear audit logs from creation
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // When: updating user's lastName and committing
        publishUserUpdateInTransaction(user.id(), "Doe", "UpdatedLastName");

        // Then: audit log entry contains JSON Patch delta
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);

        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("UPDATE");
        assertThat(auditLog.getEntityType()).isEqualTo("User");
        assertThat(auditLog.getChanges()).isNotNull();
        assertThat(auditLog.getChanges()).contains("lastName");
        assertThat(auditLog.getEventClassName()).contains("UserUpdatedEvent");
    }

    @Test
    @DisplayName("Should create audit log entry for UserDeletedEvent")
    void should_create_audit_log_entry_for_user_deleted_event() {
        // Given: tenant context and actor context are set, and a user exists
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(UserActor.of(userId));

        UserPreferences preferences = new UserPreferences(true, true);
        User user = publishUserCreationInTransaction(preferences);

        // Clear audit logs from creation
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // When: deleting user and committing
        publishUserDeletionInTransaction(user.id());

        // Then: audit log entry is created for UserDeletedEvent
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);

        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("DELETE");
        assertThat(auditLog.getEntityType()).isEqualTo("User");
        assertThat(auditLog.getEventClassName()).contains("UserDeletedEvent");
    }

    private User publishUserCreationInTransaction(UserPreferences preferences) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            User user = createUserUseCase.execute(
                    "test@example.com",
                    "+1234567890",
                    "John",
                    "Doe",
                    preferences
            );
            entityManager.flush();
            return user;
        });
    }

    private void publishUserUpdateInTransaction(UUID userId, String firstName, String lastName) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            updateUserUseCase.execute(
                    userId,
                    null, // email unchanged
                    null, // phoneNumber unchanged
                    firstName,
                    lastName,
                    null // preferences unchanged
            );
            entityManager.flush();
            return null;
        });
    }

    private void publishUserDeletionInTransaction(UUID userId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            deleteUserUseCase.execute(userId);
            entityManager.flush();
            return null;
        });
    }
}

