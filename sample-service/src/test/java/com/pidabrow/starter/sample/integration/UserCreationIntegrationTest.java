package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.testing.AbstractIntegrationTest;
import com.pidabrow.starter.testing.assertions.TenantIsolationAssertions;
import com.pidabrow.starter.sample.application.port.out.SaveNotificationRequestPort;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.NotificationRequestEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.UserEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.NotificationRequestEntityRepository;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Integration test for user creation with notification outbox.
 * 
 * Verifies:
 * - User and NotificationRequest are saved atomically
 * - Tenant isolation is enforced
 * - Rollback occurs if notification save fails
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @SpyBean
    private SaveNotificationRequestPort saveNotificationRequestPort;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private NotificationRequestEntityRepository notificationRequestEntityRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM message_outbox").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM tenants").executeUpdate();

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
    @DisplayName("Should create user and notification request atomically")
    @Transactional
    void should_create_user_and_notification_request_atomically() {
        // Given: tenant context is set
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantAId);

        UserPreferences preferences = new UserPreferences(true, true);

        // When: creating a user
        User user = createUserUseCase.execute(
                "test@example.com",
                "+1234567890",
                "John",
                "Doe",
                preferences
        );

        entityManager.flush();
        entityManager.clear();

        // Then: user is saved
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantAId);
        var foundUser = userEntityRepository.findById(user.id());
        assertThat(foundUser).isPresent();
        UserEntity userEntity = foundUser.get();
        assertThat(userEntity.getEmail()).isEqualTo("test@example.com");
        assertThat(userEntity.getFirstName()).isEqualTo("John");
        assertThat(userEntity.getLastName()).isEqualTo("Doe");
        assertThat(userEntity.getPhoneNumber()).isEqualTo("+1234567890");

        // And: notification request is saved
        List<NotificationRequestEntity> notifications = notificationRequestEntityRepository.findAll();
        assertThat(notifications).hasSize(1);
        NotificationRequestEntity notification = notifications.get(0);
        assertThat(notification.getUserId()).isEqualTo(user.id());
        assertThat(notification.getStatus().name()).isEqualTo("PENDING");
        assertThat(notification.getTemplateName()).isEqualTo("WELCOME_EMAIL_AND_SMS");
    }

    @Test
    @DisplayName("Should enforce tenant isolation when creating users")
    @Transactional
    void should_enforce_tenant_isolation_when_creating_users() {
        // Given: create user for tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantAId);

        UserPreferences preferences = new UserPreferences(true, false);
        User userA = createUserUseCase.execute(
                "tenantA@example.com",
                "+1111111111",
                "TenantA",
                "User",
                preferences
        );

        entityManager.flush();
        entityManager.clear();

        // When: create user for tenant B
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantBId);

        User userB = createUserUseCase.execute(
                "tenantB@example.com",
                "+2222222222",
                "TenantB",
                "User",
                preferences
        );

        entityManager.flush();
        entityManager.clear();

        // Then: querying tenant A only returns tenant A's user
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantAId);
        List<UserEntity> tenantAUsers = userEntityRepository.findAll();
        assertThat(tenantAUsers).hasSize(1);
        assertThat(tenantAUsers.get(0).getEmail()).isEqualTo("tenantA@example.com");
        assertThat(tenantAUsers.get(0).getTenantId()).isEqualTo(tenantAId);

        // And: querying tenant B only returns tenant B's user
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        TenantIsolationAssertions.enableTenantFilter(entityManager, tenantBId);
        List<UserEntity> tenantBUsers = userEntityRepository.findAll();
        assertThat(tenantBUsers).hasSize(1);
        assertThat(tenantBUsers.get(0).getEmail()).isEqualTo("tenantB@example.com");
        assertThat(tenantBUsers.get(0).getTenantId()).isEqualTo(tenantBId);
    }

    @Test
    @DisplayName("Should rollback user when notification request save fails (atomic transaction)")
    void should_rollback_user_when_notification_save_fails() {
        // Given: tenant context is set, no users exist, and notification save is stubbed to throw
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        assertThat(userEntityRepository.findAll()).as("pre-condition: no users exist").isEmpty();
        doThrow(new RuntimeException("Simulated notification failure"))
                .when(saveNotificationRequestPort).save(any());

        UserPreferences preferences = new UserPreferences(true, true);

        // When: creating a user (notification save will fail)
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                createUserUseCase.execute("rollback@example.com", "+9999999999", "Roll", "Back", preferences)
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated notification failure");

        // Then: user is NOT saved (transaction was rolled back)
        List<UserEntity> users = userEntityRepository.findAll();
        assertThat(users).isEmpty();

        // And: no notification requests exist
        List<NotificationRequestEntity> notifications = notificationRequestEntityRepository.findAll();
        assertThat(notifications).isEmpty();
    }
}

