package com.pidabrow.starter.common.exception;

/**
 * Base exception class for all business logic exceptions.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
