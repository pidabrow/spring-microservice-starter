package com.pidabrow.starter.sample.entity;

import com.pidabrow.starter.data.entity.TenantScopedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Test entity for integration tests.
 * This entity extends TenantScopedEntity to verify tenant isolation.
 */
@Entity
@Table(name = "test_entities")
public class TestEntity extends TenantScopedEntity {
    
    private String name;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public static TestEntity create(String name) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        // tenantId will be set automatically from TenantContextHolder in @PrePersist
        return entity;
    }
}

