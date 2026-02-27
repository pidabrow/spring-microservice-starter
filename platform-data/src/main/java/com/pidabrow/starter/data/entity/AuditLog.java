package com.pidabrow.starter.data.entity;

import com.pidabrow.starter.common.actor.ActorContext;
import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.EntityCreatedEvent;
import com.pidabrow.starter.common.event.EntityUpdatedEvent;
import com.pidabrow.starter.common.uuid.UuidV7Generator;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit log entity.
 * Records domain events for auditing purposes.
 * 
 * This entity does NOT extend TenantScopedEntity because it needs to track
 * tenantId from the event, not from the current context.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    
    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;
    
    @Column(name = "action", nullable = false, updatable = false)
    private String action;
    
    @Column(name = "event_class_name", nullable = false, updatable = false)
    private String eventClassName;
    
    @Column(name = "actor_type", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private ActorType actorType;
    
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    
    @Column(name = "changes", columnDefinition = "TEXT", updatable = false)
    private String changes;
    
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private LocalDateTime createdAt;
    
    protected AuditLog() {
        // Protected no-args constructor for JPA
    }
    
    private AuditLog(
            UUID id,
            UUID tenantId,
            String entityType,
            UUID entityId,
            String action,
            String eventClassName,
            ActorType actorType,
            UUID actorId,
            String changes) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.eventClassName = eventClassName;
        this.actorType = actorType;
        this.actorId = actorId;
        this.changes = changes;
    }
    
    /**
     * Static factory method to create an AuditLog from a domain event and actor context.
     * 
     * @param event the domain event
     * @param actorContext the actor context
     * @return a new AuditLog instance
     */
    public static AuditLog fromEvent(DomainEvent event, ActorContext actorContext) {
        UUID id = UuidV7Generator.generate();
        String action = determineAction(event);
        String eventClassName = event.getClass().getName();
        ActorType actorType = actorContext.actorType() == ActorContext.ActorType.SYSTEM 
                ? ActorType.SYSTEM 
                : ActorType.USER;
        UUID actorId = actorContext.actorId();
        String changes = extractChanges(event);
        
        return new AuditLog(
                id,
                event.tenantId(),
                event.entityType(),
                event.entityId(),
                action,
                eventClassName,
                actorType,
                actorId,
                changes
        );
    }
    
    private static String determineAction(DomainEvent event) {
        return switch (event) {
            case EntityCreatedEvent ignored -> "CREATE";
            case EntityUpdatedEvent ignored -> "UPDATE";
        };
    }
    
    private static String extractChanges(DomainEvent event) {
        return switch (event) {
            case EntityUpdatedEvent updatedEvent -> updatedEvent.delta();
            case EntityCreatedEvent ignored -> null;
        };
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public UUID getEntityId() {
        return entityId;
    }
    
    public String getAction() {
        return action;
    }
    
    public String getEventClassName() {
        return eventClassName;
    }
    
    public ActorType getActorType() {
        return actorType;
    }
    
    public UUID getActorId() {
        return actorId;
    }
    
    public String getChanges() {
        return changes;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public enum ActorType {
        SYSTEM,
        USER
    }
}

