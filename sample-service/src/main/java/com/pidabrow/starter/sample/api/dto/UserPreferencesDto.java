package com.pidabrow.starter.sample.api.dto;

/**
 * DTO for user preferences.
 */
public record UserPreferencesDto(
        boolean emailEnabled,
        boolean smsEnabled
) {
}

