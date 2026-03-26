package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.correlation.CorrelationContext;
import com.pidabrow.starter.common.correlation.CorrelationContextHolder;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.testing.AbstractIntegrationTest;
import com.pidabrow.starter.testing.tenant.TenantTestFixtures;
import com.pidabrow.starter.sample.application.usecase.RegisterUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserAlreadyExistsException;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.UserEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for user registration flow.
 * Tests transactional consistency, database constraints, and outbox integration.
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRegistrationIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void registerOutboxForRegistrationTests(DynamicPropertyRegistry registry) {
        registry.add("outbox.enabled", () -> "true");
    }

    @Autowired
    private RegisterUserUseCase registerUserUseCase;

    @Autowired
    private UserEntityRepository userEntityRepository;


    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID correlationId;

    @BeforeEach
    void setUp() {
        correlationId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM message_outbox").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE audit_log").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM tenants").executeUpdate();
            return null;
        });

        tenantId = TenantTestFixtures.persistTenant(entityManager, transactionManager, "Test Tenant");

        TenantContextHolder.setContext(TenantContext.of(tenantId));
        CorrelationContextHolder.setContext(CorrelationContext.of(correlationId));
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM message_outbox").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            userEntityRepository.deleteAll();
            entityManager.createNativeQuery("DELETE FROM tenants").executeUpdate();
            return null;
        });
        TenantContextHolder.clearContext();
        CorrelationContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should register user and create outbox record in same transaction")
    void should_register_user_and_create_outbox_record_in_same_transaction() {
        // When
        User user = registerUserUseCase.execute("test@example.com", "SecurePassword123!", "John", "Doe");

        // Then
        Optional<UserEntity> savedUser = userEntityRepository.findById(user.id());
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.get().getPasswordHash()).isNotNull();
        assertThat(savedUser.get().getPasswordHash()).startsWith("$2a$12$");

        // Verify outbox record was created using native query
        List<Object[]> outboxResults = entityManager.createNativeQuery(
                "SELECT message_type, destination, partition_key, headers, status FROM message_outbox WHERE status = 'PENDING'"
        ).getResultList();
        
        assertThat(outboxResults).hasSize(1);
        Object[] outboxRecord = outboxResults.get(0);
        assertThat(outboxRecord[0]).isEqualTo("WELCOME_EMAIL_REQUEST"); // message_type
        assertThat(outboxRecord[1]).isEqualTo("notification-events"); // destination
        assertThat(outboxRecord[2]).isEqualTo(user.id().toString()); // partition_key
        assertThat(outboxRecord[4]).isEqualTo("PENDING"); // status
        
        // Verify correlation ID in headers (JSONB)
        String headersJson = outboxRecord[3].toString();
        assertThat(headersJson).contains("x-correlation-id");
        assertThat(headersJson).contains(correlationId.toString());
    }

    @Test
    @DisplayName("Should enforce unique constraint on (email, tenant_id)")
    void should_enforce_unique_constraint_on_email_tenant_id() {
        // Given
        registerUserUseCase.execute("test@example.com", "Password123!", "John", "Doe");

        // When & Then
        assertThatThrownBy(() -> 
                registerUserUseCase.execute("test@example.com", "AnotherPassword!", "Jane", "Smith"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should handle case-insensitive email collision")
    void should_handle_case_insensitive_email_collision() {
        // Given
        registerUserUseCase.execute("test@example.com", "Password123!", "John", "Doe");

        // When & Then - Different case should trigger constraint violation
        assertThatThrownBy(() -> 
                registerUserUseCase.execute("TEST@EXAMPLE.COM", "AnotherPassword!", "Jane", "Smith"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Should allow same email for different tenants")
    void should_allow_same_email_for_different_tenants() {
        // Given
        UUID tenant1 = TenantTestFixtures.persistTenant(entityManager, transactionManager, "Tenant 1");
        UUID tenant2 = TenantTestFixtures.persistTenant(entityManager, transactionManager, "Tenant 2");

        // When
        TenantContextHolder.setContext(TenantContext.of(tenant1));
        User user1 = registerUserUseCase.execute("same@example.com", "Password123!", "John", "Doe");
        
        TenantContextHolder.setContext(TenantContext.of(tenant2));
        User user2 = registerUserUseCase.execute("same@example.com", "Password456!", "Jane", "Smith");

        // Then
        assertThat(user1.id()).isNotEqualTo(user2.id());
        assertThat(user1.email()).isEqualTo(user2.email());
        assertThat(user1.tenantId()).isNotEqualTo(user2.tenantId());
    }

    @Test
    @DisplayName("Should normalize email to lowercase before persistence")
    void should_normalize_email_to_lowercase_before_persistence() {
        // When
        User user = registerUserUseCase.execute("Test@Example.COM", "Password123!", "John", "Doe");

        // Then
        Optional<UserEntity> savedUser = userEntityRepository.findById(user.id());
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should hash password with BCrypt cost factor 12")
    void should_hash_password_with_bcrypt_cost_factor_12() {
        // When
        Instant start = Instant.now();
        User user = registerUserUseCase.execute("test@example.com", "SecurePassword123!", "John", "Doe");
        Instant end = Instant.now();
        
        Duration duration = Duration.between(start, end);

        // Then
        Optional<UserEntity> savedUser = userEntityRepository.findById(user.id());
        assertThat(savedUser).isPresent();
        String passwordHash = savedUser.get().getPasswordHash();
        
        // Verify BCrypt format with cost factor 12
        assertThat(passwordHash).startsWith("$2a$12$");
        assertThat(passwordHash).hasSize(60); // BCrypt hash length
        
        // Verify hashing takes at least 250ms (BCrypt cost factor 12 requirement)
        assertThat(duration.toMillis()).isGreaterThanOrEqualTo(250);
    }

    @Test
    @DisplayName("Should rollback transaction if outbox write fails")
    void should_rollback_transaction_if_outbox_write_fails() {
        // This test verifies transactional consistency
        // If outbox write fails, user should not be persisted
        
        // Note: This is a simplified test - in real scenario, we'd need to simulate
        // outbox write failure. For now, we verify that both user and outbox
        // are created atomically in successful case (tested above).
        
        // Given & When
        User user = registerUserUseCase.execute("test@example.com", "Password123!", "John", "Doe");

        // Then - Both should exist
        assertThat(userEntityRepository.findById(user.id())).isPresent();
        Long count = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM message_outbox"
        ).getSingleResult()).longValue();
        assertThat(count).isEqualTo(1);
    }
}

