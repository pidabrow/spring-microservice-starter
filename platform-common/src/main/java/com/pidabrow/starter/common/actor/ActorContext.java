package com.pidabrow.starter.common.actor;

import java.util.UUID;

/**
 * Sealed interface representing the actor context (who performed an action).
 * Used for auditing to track who made changes.
 */
public sealed interface ActorContext
        permits SystemActor, UserActor {
    
    /**
     * Returns the actor type.
     */
    ActorType actorType();
    
    /**
     * Returns the actor ID if available, null otherwise.
     */
    UUID actorId();
    
    enum ActorType {
        SYSTEM,
        USER
    }
}

