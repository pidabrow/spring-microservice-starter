package com.pidabrow.starter.sample.application.port.out;

/**
 * Outbound port for checking if a user exists by email.
 * This is a port interface following hexagonal architecture principles.
 */
public interface CheckUserExistsPort {
    
    /**
     * Checks if a user with the given email exists within the current tenant context.
     * 
     * @param email the email to check (should be normalized to lowercase)
     * @return true if a user with the email exists, false otherwise
     */
    boolean existsByEmail(String email);
}

