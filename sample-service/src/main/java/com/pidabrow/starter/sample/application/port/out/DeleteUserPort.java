package com.pidabrow.starter.sample.application.port.out;

import java.util.UUID;

/**
 * Outbound port for deleting a user.
 * This is a port interface following hexagonal architecture principles.
 */
public interface DeleteUserPort {
    
    /**
     * Deletes a user by ID.
     * 
     * @param userId the user ID to delete
     */
    void deleteById(UUID userId);
}

