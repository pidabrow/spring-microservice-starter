package com.pidabrow.starter.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface representing a domain event.
 * All domain events must be immutable Java Records.
 * Domain events must not depend on Spring or JPA.
 * 
 * Domain events are published after successful transaction commit
 * and are used for auditing and other cross-cutting concerns.
 */
public sealed interface DomainEvent
        permits EntityCreatedEvent, EntityUpdatedEvent,
                UserCreatedEvent, UserUpdatedEvent, UserDeletedEvent, NotificationRequestedEvent {
    
    /**
     * The ID of the entity that this event relates to.
     */
    UUID entityId();
    
    /**
     * The tenant ID of the entity.
     */
    UUID tenantId();
    
    /**
     * The type of the entity (e.g., "Tenant", "User", "Notification").
     * Must be explicit and mandatory.
     */
    String entityType();
    
    /**
     * The timestamp when the event occurred.
     */
    Instant occurredAt();
}

