package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserUpdatedEvent;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.User;
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

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UpdateUserUseCase}.
 *
 * Tests business logic: user not found, no-op detection, partial update, event publishing.
 * Ports are mocked — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserUseCase unit tests")
class UpdateUserUseCaseTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FindUserPort findUserPort;

    @Mock
    private SaveUserPort saveUserPort;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;

    private UpdateUserUseCase useCase;

    private User existingUser;

    @BeforeEach
    void setUp() {
        useCase = new UpdateUserUseCase(findUserPort, saveUserPort, eventPublisher);
        TenantContextHolder.setContext(TenantContext.of(TENANT_ID));

        existingUser = new User(
                USER_ID, TENANT_ID, "old@example.com", "+1111111111",
                "OldFirst", "OldLast", new UserPreferences(true, false), null
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when user not found")
    void should_throw_no_such_element_when_user_not_found() {
        // findUserPort returns empty — this happens when UUID doesn't exist
        // or when tenant filter hides another tenant's user (same observable behavior)
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                USER_ID, "new@example.com", null, null, null, null
        ))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");

        verify(saveUserPort, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when user belongs to another tenant")
    void should_throw_no_such_element_when_user_belongs_to_another_tenant() {
        UUID otherTenantId = UUID.randomUUID();
        User otherTenantUser = new User(
                USER_ID, otherTenantId, "other@example.com", "+9999999999",
                "Other", "User", new UserPreferences(false, false), null
        );
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(otherTenantUser));

        assertThatThrownBy(() -> useCase.execute(
                USER_ID, "hacked@example.com", null, null, null, null
        ))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");

        verify(saveUserPort, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void should_not_publish_event_when_no_fields_changed() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // All fields null → no changes, use case merges with existing values
        useCase.execute(USER_ID, null, null, null, null, null);

        verify(saveUserPort).save(any(User.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should publish event with delta when fields changed")
    void should_publish_event_with_delta_when_fields_changed() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(USER_ID, "new@example.com", null, null, "NewLast", null);

        verify(eventPublisher).publish(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(UserUpdatedEvent.class);
        UserUpdatedEvent updatedEvent = (UserUpdatedEvent) event;
        assertThat(updatedEvent.entityId()).isEqualTo(USER_ID);
        assertThat(updatedEvent.tenantId()).isEqualTo(TENANT_ID);
        assertThat(updatedEvent.delta()).contains("email");
        assertThat(updatedEvent.delta()).contains("lastName");
    }

    @Test
    @DisplayName("Should preserve existing values when update fields are null")
    void should_preserve_existing_values_when_update_fields_are_null() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Only update email, everything else null → preserved from existing
        useCase.execute(USER_ID, "new@example.com", null, null, null, null);

        verify(saveUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.email()).isEqualTo("new@example.com");
        assertThat(savedUser.phoneNumber()).isEqualTo(existingUser.phoneNumber());
        assertThat(savedUser.firstName()).isEqualTo(existingUser.firstName());
        assertThat(savedUser.lastName()).isEqualTo(existingUser.lastName());
        assertThat(savedUser.preferences()).isEqualTo(existingUser.preferences());
    }
}

