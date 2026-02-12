package com.pidabrow.starter.data.entity;

import com.pidabrow.starter.common.uuid.UuidV7Generator;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.ParamDef;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base entity class for tenant-scoped entities.
 * Every entity except Tenant MUST include a tenant_id.
 * Tenant association is by ID only, not via JPA relationships.
 * 
 * Tenant isolation is enforced at the persistence layer using Hibernate Filter.
 * 
 * Uses UUID v7 (time-ordered) as primary key to prevent B-Tree fragmentation.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = java.util.UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generate();
        }
        if (tenantId == null) {
            // Automatic tenant ID assignment from context.
            // Note: While ADR-004 discourages JPA callbacks for business logic,
            // this is a **security enforcement** at the persistence boundary,
            // not a business event. Tenant ID must be set automatically to prevent
            // accidental omission, which would be a critical security vulnerability.
            tenantId = com.pidabrow.starter.common.tenant.TenantContextHolder.getTenantId();
        }
        // Timestamps are now database-driven via @Generated annotation
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

