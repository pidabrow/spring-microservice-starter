package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.NotificationRequestedEvent;
import com.pidabrow.starter.common.event.UserCreatedEvent;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.SaveNotificationRequestPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.NotificationRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CreateUserUseCase}.
 *
 * Tests business logic: template selection and event publishing.
 * Ports are mocked — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateUserUseCase unit tests")
class CreateUserUseCaseTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private SaveUserPort saveUserPort;

    @Mock
    private SaveNotificationRequestPort saveNotificationRequestPort;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<NotificationRequest> notificationCaptor;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;

    private CreateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUserUseCase(saveUserPort, saveNotificationRequestPort, eventPublisher);
        TenantContextHolder.setContext(TenantContext.of(TENANT_ID));

        // Ports return whatever is passed to them (identity save)
        when(saveUserPort.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(saveNotificationRequestPort.save(any(NotificationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should select WELCOME_EMAIL_AND_SMS template when both channels enabled")
    void should_select_welcome_email_and_sms_template_when_both_channels_enabled() {
        UserPreferences preferences = new UserPreferences(true, true);

        useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(saveNotificationRequestPort).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().templateName()).isEqualTo("WELCOME_EMAIL_AND_SMS");
    }

    @Test
    @DisplayName("Should select WELCOME_EMAIL template when only email enabled")
    void should_select_welcome_email_template_when_only_email_enabled() {
        UserPreferences preferences = new UserPreferences(true, false);

        useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(saveNotificationRequestPort).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().templateName()).isEqualTo("WELCOME_EMAIL");
    }

    @Test
    @DisplayName("Should select WELCOME_SMS template when only SMS enabled")
    void should_select_welcome_sms_template_when_only_sms_enabled() {
        UserPreferences preferences = new UserPreferences(false, true);

        useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(saveNotificationRequestPort).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().templateName()).isEqualTo("WELCOME_SMS");
    }

    @Test
    @DisplayName("Should fallback to WELCOME_EMAIL template when both channels disabled")
    void should_fallback_to_welcome_email_template_when_both_channels_disabled() {
        UserPreferences preferences = new UserPreferences(false, false);

        useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(saveNotificationRequestPort).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().templateName()).isEqualTo("WELCOME_EMAIL");
    }

    @Test
    @DisplayName("Should publish UserCreatedEvent")
    void should_publish_user_created_event() {
        UserPreferences preferences = new UserPreferences(true, false);

        User savedUser = useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(eventPublisher, times(2)).publish(eventCaptor.capture());
        DomainEvent firstEvent = eventCaptor.getAllValues().get(0);
        assertThat(firstEvent).isInstanceOf(UserCreatedEvent.class);
        assertThat(firstEvent.entityId()).isEqualTo(savedUser.id());
        assertThat(firstEvent.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("Should publish NotificationRequestedEvent")
    void should_publish_notification_requested_event() {
        UserPreferences preferences = new UserPreferences(true, false);

        useCase.execute("test@example.com", "+1234567890", "John", "Doe", preferences);

        verify(eventPublisher, times(2)).publish(eventCaptor.capture());
        DomainEvent secondEvent = eventCaptor.getAllValues().get(1);
        assertThat(secondEvent).isInstanceOf(NotificationRequestedEvent.class);
        assertThat(secondEvent.tenantId()).isEqualTo(TENANT_ID);
    }
}

