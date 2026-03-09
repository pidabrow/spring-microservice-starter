package com.pidabrow.starter.common.correlation;

import com.pidabrow.starter.common.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Thread-local holder for correlation context.
 * Provides explicit access to correlation ID without hidden static access patterns.
 * If no correlation ID is set, generates a new UUID v7 on demand.
 */
public final class CorrelationContextHolder {
    
    private static final ThreadLocal<CorrelationContext> CONTEXT_HOLDER = new ThreadLocal<>();
    
    private CorrelationContextHolder() {
        // Utility class
    }
    
    /**
     * Sets the correlation context for the current thread.
     */
    public static void setContext(CorrelationContext context) {
        CONTEXT_HOLDER.set(context);
    }
    
    /**
     * Gets the correlation context for the current thread.
     * 
     * @return the correlation context, or null if not set
     */
    public static CorrelationContext getContext() {
        return CONTEXT_HOLDER.get();
    }
    
    /**
     * Gets the correlation ID from the current context.
     * If no context is set, generates a new UUID v7 and sets it.
     * 
     * @return the correlation ID, never null
     */
    public static UUID getCorrelationId() {
        CorrelationContext context = getContext();
        if (context == null) {
            UUID generatedId = UuidV7Generator.generate();
            setContext(CorrelationContext.of(generatedId));
            return generatedId;
        }
        return context.correlationId();
    }
    
    /**
     * Clears the correlation context for the current thread.
     * Should be called after request processing to prevent context leakage.
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}

