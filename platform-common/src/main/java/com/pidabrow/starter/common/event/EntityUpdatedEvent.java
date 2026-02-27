package com.pidabrow.starter.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing the update of an entity.
 * Contains a JSON Patch (RFC 6902) delta describing the changes.
 */
public record EntityUpdatedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        String delta,
        Instant occurredAt
) implements DomainEvent {
    
    public EntityUpdatedEvent {
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
    
    public static EntityUpdatedEvent of(UUID entityId, UUID tenantId, String entityType, String delta) {
        return new EntityUpdatedEvent(entityId, tenantId, entityType, delta, Instant.now());
    }
}

