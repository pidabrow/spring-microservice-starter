package com.pidabrow.starter.sample.domain.user;

/**
 * Exception thrown when attempting to register a user with an email that already exists
 * within the current tenant context.
 */
public class UserAlreadyExistsException extends RuntimeException {
    
    public UserAlreadyExistsException(String email) {
        super("User with email " + email + " already exists");
    }
}

