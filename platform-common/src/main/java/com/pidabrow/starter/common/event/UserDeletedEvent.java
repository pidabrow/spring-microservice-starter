package com.pidabrow.starter.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing the deletion of a User.
 * This is an immutable record implementing DomainEvent.
 */
public record UserDeletedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        Instant occurredAt
) implements DomainEvent {
    
    public UserDeletedEvent {
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
    
    public static UserDeletedEvent of(UUID userId, UUID tenantId) {
        return new UserDeletedEvent(userId, tenantId, "User", Instant.now());
    }
}

