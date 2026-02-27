package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing the update of a User.
 * Contains a JSON Patch (RFC 6902) delta describing the changes.
 * This is an immutable record implementing DomainEvent.
 */
public record UserUpdatedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        String delta,
        Instant occurredAt
) implements DomainEvent {
    
    public UserUpdatedEvent {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("Entity type cannot be null or blank");
        }
        if (delta == null || delta.isBlank()) {
            throw new IllegalArgumentException("Delta cannot be null or blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
    }
    
    public static UserUpdatedEvent of(UUID userId, UUID tenantId, String delta) {
        return new UserUpdatedEvent(userId, tenantId, User.ENTITY_TYPE, delta, Instant.now());
    }
}

