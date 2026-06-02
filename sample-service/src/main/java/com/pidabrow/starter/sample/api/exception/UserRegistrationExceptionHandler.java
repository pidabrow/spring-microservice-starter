package com.pidabrow.starter.sample.api.exception;

import com.pidabrow.starter.sample.domain.user.UserAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for user registration endpoints.
 * Handles UserAlreadyExistsException and maps it to HTTP 409 Conflict.
 */
@RestControllerAdvice(basePackages = "com.pidabrow.starter.sample.api")
public class UserRegistrationExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("User Already Exists");
        // TODO(ADR-010): inject traceId from Micrometer/MDC when observability baseline is implemented
        return problemDetail;
    }
}
