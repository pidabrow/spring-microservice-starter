package com.pidabrow.starter.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing the creation of an entity.
 */
public record EntityCreatedEvent(
        UUID entityId,
        UUID tenantId,
        String entityType,
        Instant occurredAt
) implements DomainEvent {
    
    public EntityCreatedEvent {
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
    
    public static EntityCreatedEvent of(UUID entityId, UUID tenantId, String entityType) {
        return new EntityCreatedEvent(entityId, tenantId, entityType, Instant.now());
    }
}

