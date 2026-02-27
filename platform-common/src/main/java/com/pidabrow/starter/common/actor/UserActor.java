package com.pidabrow.starter.common.actor;

import java.util.UUID;

/**
 * Represents a user actor (authenticated user performing an action).
 */
public record UserActor(UUID userId) implements ActorContext {
    
    public UserActor {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
    }
    
    @Override
    public ActorType actorType() {
        return ActorType.USER;
    }
    
    @Override
    public UUID actorId() {
        return userId;
    }
    
    public static UserActor of(UUID userId) {
        return new UserActor(userId);
    }
}

