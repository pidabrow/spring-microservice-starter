package com.pidabrow.starter.sample.application.port.out;

import com.pidabrow.starter.sample.domain.user.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for finding a user.
 * This is a port interface following hexagonal architecture principles.
 */
public interface FindUserPort {
    
    /**
     * Finds a user by ID.
     * 
     * @param userId the user ID
     * @return the user if found, empty otherwise
     */
    Optional<User> findById(UUID userId);
}

