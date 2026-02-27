package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing a notification request being persisted in the Outbox.
 * This event is published when a NotificationRequest is saved.
 * Note: We audit the REQUEST, not the actual delivery (which will be handled by the Outbox processor later).
 * This is an immutable record implementing DomainEvent.
 */
public record NotificationRequestedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        Instant occurredAt
) implements DomainEvent {
    
    public NotificationRequestedEvent {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("Entity type cannot be null or blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
    }
    
    public static NotificationRequestedEvent of(UUID notificationRequestId, UUID tenantId) {
        return new NotificationRequestedEvent(
                notificationRequestId,
                tenantId,
                "NotificationRequest",
                Instant.now()
        );
    }
}

