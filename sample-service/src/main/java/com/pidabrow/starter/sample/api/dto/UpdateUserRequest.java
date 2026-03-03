package com.pidabrow.starter.sample.api.dto;

import jakarta.validation.constraints.Email;

/**
 * Request DTO for updating a user.
 * All fields are optional — null fields will preserve existing values.
 */
public record UpdateUserRequest(
        @Email(message = "Email must be valid")
        String email,

        String phoneNumber,

        String firstName,

        String lastName,

        UserPreferencesDto preferences
) {
}

