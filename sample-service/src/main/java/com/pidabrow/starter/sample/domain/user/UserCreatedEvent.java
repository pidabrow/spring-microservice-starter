package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing the creation of a User.
 * This is an immutable record implementing DomainEvent.
 */
public record UserCreatedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        Instant occurredAt
) implements DomainEvent {
    
    public UserCreatedEvent {
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
    
    public static UserCreatedEvent of(UUID userId, UUID tenantId) {
        return new UserCreatedEvent(userId, tenantId, User.ENTITY_TYPE, Instant.now());
    }
}

