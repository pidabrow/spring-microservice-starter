package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserDeletedEvent;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.DeleteNotificationRequestPort;
import com.pidabrow.starter.sample.application.port.out.DeleteUserPort;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
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
 * Unit tests for {@link DeleteUserUseCase}.
 *
 * Tests business logic: user not found, cascade delete order, event publishing.
 * Ports are mocked — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteUserUseCase unit tests")
class DeleteUserUseCaseTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FindUserPort findUserPort;

    @Mock
    private DeleteUserPort deleteUserPort;

    @Mock
    private DeleteNotificationRequestPort deleteNotificationRequestPort;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;

    private DeleteUserUseCase useCase;

    private User existingUser;

    @BeforeEach
    void setUp() {
        useCase = new DeleteUserUseCase(
                findUserPort, deleteUserPort, deleteNotificationRequestPort, eventPublisher
        );
        TenantContextHolder.setContext(TenantContext.of(TENANT_ID));

        existingUser = new User(
                USER_ID, TENANT_ID, "john@example.com", "+1234567890",
                "John", "Doe", new UserPreferences(true, false)
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when user not found")
    void should_throw_no_such_element_when_user_not_found() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(USER_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");

        verify(deleteNotificationRequestPort, never()).deleteByUserId(any());
        verify(deleteUserPort, never()).deleteById(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should delete notification requests before user")
    void should_delete_notification_requests_before_user() {
        // This ordering is critical: notification_requests has FK → users.
        // If user is deleted first, FK constraint fails.
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(existingUser));

        useCase.execute(USER_ID);

        InOrder inOrder = inOrder(deleteNotificationRequestPort, deleteUserPort);
        inOrder.verify(deleteNotificationRequestPort).deleteByUserId(USER_ID);
        inOrder.verify(deleteUserPort).deleteById(USER_ID);
    }

    @Test
    @DisplayName("Should publish UserDeletedEvent")
    void should_publish_user_deleted_event() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(existingUser));

        useCase.execute(USER_ID);

        verify(eventPublisher).publish(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(UserDeletedEvent.class);
        assertThat(event.entityId()).isEqualTo(USER_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
    }
}

