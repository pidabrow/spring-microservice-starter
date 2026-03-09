package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.correlation.CorrelationContextHolder;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.NotificationRequestedEvent;
import com.pidabrow.starter.common.event.UserCreatedEvent;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.SaveNotificationRequestPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.NotificationRequest;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Use case for creating a user.
 * This is an inbound port implementation following hexagonal architecture.
 * 
 * Atomic Action: Saves User AND NotificationRequest in one transaction.
 * Event: Publishes UserCreatedEvent after successful commit.
 */
@Component
public class CreateUserUseCase {

    private final SaveUserPort saveUserPort;
    private final SaveNotificationRequestPort saveNotificationRequestPort;
    private final DomainEventPublisher eventPublisher;

    public CreateUserUseCase(
            SaveUserPort saveUserPort,
            SaveNotificationRequestPort saveNotificationRequestPort,
            DomainEventPublisher eventPublisher) {
        this.saveUserPort = saveUserPort;
        this.saveNotificationRequestPort = saveNotificationRequestPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User execute(
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            UserPreferences preferences) {
        
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        // Create user domain object
        User user = User.create(tenantId, email, phoneNumber, firstName, lastName, preferences);

        // Save user (atomic transaction)
        User savedUser = saveUserPort.save(user);

        // Create notification request based on preferences
        String templateName = determineTemplateName(preferences);
        Map<String, Object> payload = Map.of(
                "userId", savedUser.id().toString(),
                "email", savedUser.email(),
                "firstName", savedUser.firstName(),
                "lastName", savedUser.lastName()
        );

        NotificationRequest notificationRequest = NotificationRequest.pending(
                tenantId,
                savedUser.id(),
                templateName,
                payload
        );

        // Save notification request (same transaction)
        NotificationRequest savedNotificationRequest = saveNotificationRequestPort.save(notificationRequest);

        // Publish domain events (will be handled AFTER_COMMIT)
        // 1. UserCreatedEvent for the user creation
        java.util.UUID correlationId = CorrelationContextHolder.getCorrelationId();
        UserCreatedEvent userCreatedEvent = UserCreatedEvent.of(savedUser.id(), tenantId, correlationId);
        eventPublisher.publish(userCreatedEvent);

        // 2. NotificationRequestedEvent for the notification outbox entry
        NotificationRequestedEvent notificationRequestedEvent = NotificationRequestedEvent.of(
                savedNotificationRequest.id(),
                tenantId
        );
        eventPublisher.publish(notificationRequestedEvent);

        return savedUser;
    }

    private String determineTemplateName(UserPreferences preferences) {
        if (preferences.emailEnabled() && preferences.smsEnabled()) {
            return "WELCOME_EMAIL_AND_SMS";
        } else if (preferences.emailEnabled()) {
            return "WELCOME_EMAIL";
        } else if (preferences.smsEnabled()) {
            return "WELCOME_SMS";
        } else {
            return "WELCOME_EMAIL"; // Default fallback
        }
    }
}

