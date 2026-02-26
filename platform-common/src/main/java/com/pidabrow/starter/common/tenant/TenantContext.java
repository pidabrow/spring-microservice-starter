package com.pidabrow.starter.common.tenant;

import java.util.UUID;

/**
 * Immutable tenant context representing the current tenant for a request.
 * This is a security boundary and must be set explicitly per request.
 */
public record TenantContext(UUID tenantId) {
    
    public TenantContext {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
    }
    
    public static TenantContext of(UUID tenantId) {
        return new TenantContext(tenantId);
    }
}

