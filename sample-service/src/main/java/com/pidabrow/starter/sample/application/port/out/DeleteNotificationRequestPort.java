package com.pidabrow.starter.sample.application.port.out;

import java.util.UUID;

/**
 * Outbound port for deleting notification requests.
 * This is a port interface following hexagonal architecture principles.
 */
public interface DeleteNotificationRequestPort {
    
    /**
     * Deletes all notification requests for a given user.
     * 
     * @param userId the user ID
     */
    void deleteByUserId(UUID userId);
}

