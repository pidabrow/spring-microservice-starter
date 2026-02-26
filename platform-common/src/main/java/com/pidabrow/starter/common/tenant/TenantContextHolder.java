package com.pidabrow.starter.common.tenant;

/**
 * Thread-local holder for tenant context.
 * Provides explicit access to tenant context without hidden static access patterns.
 */
public final class TenantContextHolder {
    
    private static final ThreadLocal<TenantContext> CONTEXT_HOLDER = new ThreadLocal<>();
    
    private TenantContextHolder() {
        // Utility class
    }
    
    /**
     * Sets the tenant context for the current thread.
     */
    public static void setContext(TenantContext context) {
        CONTEXT_HOLDER.set(context);
    }
    
    /**
     * Gets the tenant context for the current thread.
     * 
     * @return the tenant context, or null if not set
     */
    public static TenantContext getContext() {
        return CONTEXT_HOLDER.get();
    }
    
    /**
     * Gets the tenant ID from the current context.
     * 
     * @return the tenant ID, or null if context is not set
     */
    public static java.util.UUID getTenantId() {
        TenantContext context = getContext();
        return context != null ? context.tenantId() : null;
    }
    
    /**
     * Clears the tenant context for the current thread.
     * Should be called after request processing to prevent context leakage.
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}

