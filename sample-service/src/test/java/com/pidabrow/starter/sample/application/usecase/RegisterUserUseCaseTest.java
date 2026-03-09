package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.correlation.CorrelationContext;
import com.pidabrow.starter.common.correlation.CorrelationContextHolder;
import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserCreatedEvent;
import com.pidabrow.starter.common.security.PasswordEncoder;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.CheckUserExistsPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserAlreadyExistsException;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegisterUserUseCase.
 * Tests password hashing, email normalization, uniqueness checks, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private CheckUserExistsPort checkUserExistsPort;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DomainEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;
    @Captor
    private ArgumentCaptor<User> userCaptor;

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(saveUserPort, checkUserExistsPort, passwordEncoder, eventPublisher);
        TenantContextHolder.setContext(TenantContext.of(TENANT_ID));
        CorrelationContextHolder.setContext(CorrelationContext.of(CORRELATION_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
        CorrelationContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should register user with normalized email and hashed password")
    void should_register_user_with_normalized_email_and_hashed_password() {
        // Given
        String rawEmail = "Test@Example.Com";
        String rawPassword = "SecurePassword123!";
        String hashedPassword = "$2a$12$hashed.password.hash";
        String normalizedEmail = "test@example.com";

        when(checkUserExistsPort.existsByEmail(normalizedEmail)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        // When
        User result = useCase.execute(rawEmail, rawPassword, "John", "Doe");

        // Then
        verify(checkUserExistsPort).existsByEmail(normalizedEmail);
        verify(passwordEncoder).encode(rawPassword);
        verify(saveUserPort).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.email()).isEqualTo(normalizedEmail);
        assertThat(savedUser.passwordHash()).isEqualTo(hashedPassword);
        assertThat(savedUser.firstName()).isEqualTo("John");
        assertThat(savedUser.lastName()).isEqualTo("Doe");
        assertThat(savedUser.tenantId()).isEqualTo(TENANT_ID);
        
        // Verify password hash is set, not raw password
        assertThat(savedUser.passwordHash()).isNotEqualTo(rawPassword);
        assertThat(savedUser.passwordHash()).startsWith("$2a$12$");
        
        // Verify event was published
        verify(eventPublisher).publish(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(UserCreatedEvent.class);
        UserCreatedEvent userCreatedEvent = (UserCreatedEvent) event;
        assertThat(userCreatedEvent.entityId()).isEqualTo(result.id());
        assertThat(userCreatedEvent.tenantId()).isEqualTo(TENANT_ID);
        assertThat(userCreatedEvent.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    @DisplayName("Should normalize email to lowercase")
    void should_normalize_email_to_lowercase() {
        // Given
        String email = "Test@Example.COM";
        String normalizedEmail = "test@example.com";
        
        when(checkUserExistsPort.existsByEmail(normalizedEmail)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute(email, "password", "John", "Doe");

        // Then
        verify(checkUserExistsPort).existsByEmail(normalizedEmail);
        assertThat(result.email()).isEqualTo(normalizedEmail);
    }

    @Test
    @DisplayName("Should trim email before normalization")
    void should_trim_email_before_normalization() {
        // Given
        String email = "  Test@Example.Com  ";
        String normalizedEmail = "test@example.com";
        
        when(checkUserExistsPort.existsByEmail(normalizedEmail)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute(email, "password", "John", "Doe");

        // Then
        verify(checkUserExistsPort).existsByEmail(normalizedEmail);
        assertThat(result.email()).isEqualTo(normalizedEmail);
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when email already exists")
    void should_throw_user_already_exists_exception_when_email_already_exists() {
        // Given
        String email = "existing@example.com";
        String normalizedEmail = "existing@example.com";
        
        when(checkUserExistsPort.existsByEmail(normalizedEmail)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> useCase.execute(email, "password", "John", "Doe"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
        
        verify(checkUserExistsPort).existsByEmail(normalizedEmail);
        verify(saveUserPort, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should hash password using PasswordEncoder")
    void should_hash_password_using_password_encoder() {
        // Given
        String rawPassword = "MySecurePassword123!";
        String hashedPassword = "$2a$12$encoded.password.hash";
        
        when(checkUserExistsPort.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute("test@example.com", rawPassword, "John", "Doe");

        // Then
        verify(passwordEncoder).encode(rawPassword);
        verify(saveUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        
        // Verify that User entity never receives raw password
        assertThat(savedUser.passwordHash()).isEqualTo(hashedPassword);
        assertThat(savedUser.passwordHash()).isNotEqualTo(rawPassword);
    }

    @Test
    @DisplayName("Should publish UserCreatedEvent with correlation ID")
    void should_publish_user_created_event_with_correlation_id() {
        // Given
        when(checkUserExistsPort.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute("test@example.com", "password", "John", "Doe");

        // Then
        verify(eventPublisher).publish(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(UserCreatedEvent.class);
        UserCreatedEvent userCreatedEvent = (UserCreatedEvent) event;
        assertThat(userCreatedEvent.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(userCreatedEvent.entityId()).isEqualTo(result.id());
        assertThat(userCreatedEvent.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("Should create user with default preferences")
    void should_create_user_with_default_preferences() {
        // Given
        when(checkUserExistsPort.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute("test@example.com", "password", "John", "Doe");

        // Then
        verify(saveUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.preferences()).isNotNull();
        assertThat(savedUser.preferences().emailEnabled()).isTrue();
        assertThat(savedUser.preferences().smsEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should map DataIntegrityViolationException to UserAlreadyExistsException (race condition)")
    void should_map_data_integrity_violation_to_user_already_exists_exception() {
        // Given — simulates a concurrent registration where the DB unique constraint fires
        // even though the pre-save existsByEmail check passed (classic check-then-act race)
        String email = "race@example.com";
        when(checkUserExistsPort.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(saveUserPort.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(email, "password", "John", "Doe"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(eventPublisher, never()).publish(any());
    }
}

