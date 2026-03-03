package com.pidabrow.starter.sample.api.dto;

import java.util.UUID;

/**
 * Response DTO for user.
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        UserPreferencesDto preferences
) {
}

