package com.pidabrow.starter.common.actor;

/**
 * Thread-local holder for actor context.
 * Provides explicit access to actor context without hidden static access patterns.
 */
public final class ActorContextHolder {
    
    private static final ThreadLocal<ActorContext> CONTEXT_HOLDER = new ThreadLocal<>();
    
    private ActorContextHolder() {
        // Utility class
    }
    
    /**
     * Sets the actor context for the current thread.
     */
    public static void setContext(ActorContext context) {
        CONTEXT_HOLDER.set(context);
    }
    
    /**
     * Gets the actor context for the current thread.
     * 
     * @return the actor context, or null if not set
     */
    public static ActorContext getContext() {
        return CONTEXT_HOLDER.get();
    }
    
    /**
     * Clears the actor context for the current thread.
     * Should be called after request processing to prevent context leakage.
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}

