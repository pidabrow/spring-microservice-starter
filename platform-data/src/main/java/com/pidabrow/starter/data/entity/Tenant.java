package com.pidabrow.starter.data.entity;

import com.pidabrow.starter.common.uuid.UuidV7Generator;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant entity representing a tenant in the multi-tenant system.
 * This entity does NOT contain tenant_id as it IS the tenant.
 * 
 * Uses UUID v7 (time-ordered) as primary key to prevent B-Tree fragmentation.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generate();
        }
        // Audit timestamps are persistence concerns, not business logic.
        // Using @PrePersist here is acceptable per ADR-004 which prohibits
        // callbacks for "business events", not infrastructure concerns.
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // Audit timestamp update is a persistence concern.
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Business method to create a new tenant.
     */
    public static Tenant create(String name) {
        Tenant tenant = new Tenant();
        tenant.id = UuidV7Generator.generate();
        tenant.name = name;
        tenant.status = TenantStatus.ACTIVE;
        tenant.createdAt = LocalDateTime.now();
        tenant.updatedAt = LocalDateTime.now();
        return tenant;
    }

    /**
     * Business method to suspend a tenant.
     * Mutates the entity state (acceptable for JPA entities in persistence adapter).
     */
    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Business method to activate a tenant.
     * Mutates the entity state (acceptable for JPA entities in persistence adapter).
     */
    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public enum TenantStatus {
        ACTIVE,
        SUSPENDED
    }
}

