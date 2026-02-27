package com.pidabrow.starter.sample.application.port.out;

import com.pidabrow.starter.sample.domain.user.User;

/**
 * Outbound port for saving a user.
 * This is a port interface following hexagonal architecture principles.
 */
public interface SaveUserPort {
    
    /**
     * Saves a user.
     * 
     * @param user the user to save
     * @return the saved user (with generated ID if applicable)
     */
    User save(User user);
}

