package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.exception.BusinessException;

/**
 * Exception thrown when attempting to register a user with an email that already exists
 * within the current tenant context.
 */
public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException(String email) {
        super("User with email " + email + " already exists");
    }
}

