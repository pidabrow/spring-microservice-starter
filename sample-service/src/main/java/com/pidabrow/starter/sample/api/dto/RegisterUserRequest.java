package com.pidabrow.starter.sample.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user registration.
 * Raw password is accepted only in this inbound adapter (REST DTO) per ADR-008.
 */
public record RegisterUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        
        @NotBlank(message = "Password is required")
        String password,
        
        @NotBlank(message = "First name is required")
        String firstName,
        
        @NotBlank(message = "Last name is required")
        String lastName
) {
    @Override
    public String toString() {
        // Exclude password from toString to prevent logging sensitive data
        return "RegisterUserRequest{email='" + email + "', firstName='" + firstName + "', lastName='" + lastName + "'}";
    }
}

