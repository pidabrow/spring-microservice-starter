package com.pidabrow.starter.sample.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain contract tests for the {@link User} record.
 *
 * These tests verify the domain invariants enforced by the compact constructor.
 * In the current HTTP flow, upstream validation (DTO @NotBlank, interceptor) prevents
 * invalid data from reaching the constructor. However, User is a public domain model —
 * any caller (scheduler, event handler, another module) can construct it directly.
 * These tests ensure the domain object can never exist in an invalid state,
 * regardless of the caller.
 */
@DisplayName("User domain model contract tests")
class UserTest {

    private static final UUID VALID_ID = UUID.randomUUID();
    private static final UUID VALID_TENANT_ID = UUID.randomUUID();
    private static final String VALID_EMAIL = "john@example.com";
    private static final String VALID_PHONE = "+1234567890";
    private static final String VALID_FIRST_NAME = "John";
    private static final String VALID_LAST_NAME = "Doe";
    private static final UserPreferences VALID_PREFERENCES = new UserPreferences(true, false);

    @Test
    @DisplayName("Should create user with all valid fields")
    void should_create_user_with_all_valid_fields() {
        User user = new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        );

        assertThat(user.id()).isEqualTo(VALID_ID);
        assertThat(user.tenantId()).isEqualTo(VALID_TENANT_ID);
        assertThat(user.email()).isEqualTo(VALID_EMAIL);
        assertThat(user.phoneNumber()).isEqualTo(VALID_PHONE);
        assertThat(user.firstName()).isEqualTo(VALID_FIRST_NAME);
        assertThat(user.lastName()).isEqualTo(VALID_LAST_NAME);
        assertThat(user.preferences()).isEqualTo(VALID_PREFERENCES);
    }

    @Test
    @DisplayName("Should generate UUID v7 via create factory method")
    void should_generate_uuid_v7_via_create_factory_method() {
        User user = User.create(
                VALID_TENANT_ID, VALID_EMAIL, VALID_PHONE,
                VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        );

        assertThat(user.id()).isNotNull();
        // UUID v7 has version nibble = 7 (bits 48-51)
        assertThat(user.id().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should throw when id is null")
    void should_throw_when_id_is_null() {
        assertThatThrownBy(() -> new User(
                null, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    @DisplayName("Should throw when tenantId is null")
    void should_throw_when_tenant_id_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, null, VALID_EMAIL,
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be null");
    }

    @Test
    @DisplayName("Should throw when email is null")
    void should_throw_when_email_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, null,
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email must not be blank");
    }

    @Test
    @DisplayName("Should throw when email is blank")
    void should_throw_when_email_is_blank() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, "   ",
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email must not be blank");
    }

    @Test
    @DisplayName("Should throw when phoneNumber is null")
    void should_throw_when_phone_number_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                null, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phoneNumber must not be blank");
    }

    @Test
    @DisplayName("Should throw when phoneNumber is blank")
    void should_throw_when_phone_number_is_blank() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                " ", VALID_FIRST_NAME, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phoneNumber must not be blank");
    }

    @Test
    @DisplayName("Should throw when firstName is null")
    void should_throw_when_first_name_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, null, VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName must not be blank");
    }

    @Test
    @DisplayName("Should throw when firstName is blank")
    void should_throw_when_first_name_is_blank() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, "", VALID_LAST_NAME, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName must not be blank");
    }

    @Test
    @DisplayName("Should throw when lastName is null")
    void should_throw_when_last_name_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, VALID_FIRST_NAME, null, VALID_PREFERENCES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastName must not be blank");
    }

    @Test
    @DisplayName("Should throw when preferences is null")
    void should_throw_when_preferences_is_null() {
        assertThatThrownBy(() -> new User(
                VALID_ID, VALID_TENANT_ID, VALID_EMAIL,
                VALID_PHONE, VALID_FIRST_NAME, VALID_LAST_NAME, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferences must not be null");
    }
}

