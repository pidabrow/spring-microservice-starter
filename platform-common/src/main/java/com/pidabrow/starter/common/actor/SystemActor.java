package com.pidabrow.starter.common.actor;

import java.util.UUID;

/**
 * Represents a system actor (automated processes, background jobs, etc.).
 */
public record SystemActor() implements ActorContext {
    
    @Override
    public ActorType actorType() {
        return ActorType.SYSTEM;
    }
    
    @Override
    public UUID actorId() {
        return null;
    }
    
    public static SystemActor instance() {
        return new SystemActor();
    }
}

