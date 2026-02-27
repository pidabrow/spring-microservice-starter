package com.pidabrow.starter.sample.infrastructure.persistence.entity;

import com.pidabrow.starter.data.entity.TenantScopedEntity;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * JPA entity for User.
 * This is an outbound adapter implementation detail.
 * Domain layer uses immutable User record.
 */
@Entity
@Table(name = "users")
public class UserEntity extends TenantScopedEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "preferences", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> preferences;

    protected UserEntity() {
        // Protected no-args constructor for JPA
    }

    private UserEntity(
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            Map<String, Object> preferences) {
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.preferences = preferences;
    }

    public static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity(
                user.email(),
                user.phoneNumber(),
                user.firstName(),
                user.lastName(),
                Map.of(
                        "emailEnabled", user.preferences().emailEnabled(),
                        "smsEnabled", user.preferences().smsEnabled()
                )
        );
        return entity;
    }

    public User toDomain() {
        Map<String, Object> prefs = this.preferences != null ? this.preferences : Map.of();
        Boolean emailEnabled = (Boolean) prefs.getOrDefault("emailEnabled", false);
        Boolean smsEnabled = (Boolean) prefs.getOrDefault("smsEnabled", false);
        UserPreferences userPreferences = new UserPreferences(
                emailEnabled != null ? emailEnabled : false,
                smsEnabled != null ? smsEnabled : false
        );
        return new User(
                getId(),
                getTenantId(),
                email,
                phoneNumber,
                firstName,
                lastName,
                userPreferences
        );
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    /**
     * Updates entity from domain model.
     * This allows updates without public setters, maintaining immutability principles.
     * Used by persistence adapter for updates.
     */
    public void updateFromDomain(User user) {
        this.email = user.email();
        this.phoneNumber = user.phoneNumber();
        this.firstName = user.firstName();
        this.lastName = user.lastName();
        this.preferences = Map.of(
                "emailEnabled", user.preferences().emailEnabled(),
                "smsEnabled", user.preferences().smsEnabled()
        );
    }
}

