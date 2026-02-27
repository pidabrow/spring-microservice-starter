package com.pidabrow.starter.sample.domain.user;

/**
 * User notification channel preferences.
 *
 * Domain models are immutable and use Java records.
 */
public record UserPreferences(
        boolean emailEnabled,
        boolean smsEnabled
) {
}


