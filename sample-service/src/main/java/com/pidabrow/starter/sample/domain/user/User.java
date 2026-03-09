package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Immutable domain model representing a user within a tenant.
 */
public record User(
        UUID id,
        UUID tenantId,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        UserPreferences preferences,
        String passwordHash
) {

    public static final String ENTITY_TYPE = "User";

    public User {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber must not be blank");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("firstName must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must not be null");
        }
        // passwordHash can be null for users created without password (e.g., via CreateUserUseCase)
    }

    /**
     * Factory method creating a new User instance for the given tenant.
     * <p>
     * The user is "born" with a UUID v7 identifier to align with persistence rules.
     */
    public static User create(
            UUID tenantId,
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            UserPreferences preferences
    ) {
        UUID id = UuidV7Generator.generate();
        return new User(id, tenantId, email, phoneNumber, firstName, lastName, preferences, null);
    }
    
    /**
     * Factory method creating a new User instance with password hash for registration.
     * <p>
     * The user is "born" with a UUID v7 identifier to align with persistence rules.
     */
    public static User createWithPassword(
            UUID tenantId,
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            UserPreferences preferences,
            String passwordHash
    ) {
        UUID id = UuidV7Generator.generate();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be null or blank");
        }
        return new User(id, tenantId, email, phoneNumber, firstName, lastName, preferences, passwordHash);
    }
}


