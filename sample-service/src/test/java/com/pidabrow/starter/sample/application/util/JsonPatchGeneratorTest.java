package com.pidabrow.starter.sample.application.util;

import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonPatchGenerator}.
 *
 * This utility is used by UpdateUserUseCase to generate RFC 6902 deltas.
 * The empty-array check ("[]") drives the no-op detection logic in UpdateUserUseCase.
 */
@DisplayName("JsonPatchGenerator unit tests")
class JsonPatchGeneratorTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("Should return empty array when objects are identical")
    void should_return_empty_array_when_objects_are_identical() {
        User user = new User(ID, TENANT_ID, "a@b.com", "+1", "A", "B", new UserPreferences(true, false));

        String patch = JsonPatchGenerator.generatePatch(user, user);

        assertThat(patch).isEqualTo("[]");
    }

    @Test
    @DisplayName("Should generate replace operation for changed field")
    void should_generate_replace_operation_for_changed_field() {
        User before = new User(ID, TENANT_ID, "old@b.com", "+1", "A", "B", new UserPreferences(true, false));
        User after = new User(ID, TENANT_ID, "new@b.com", "+1", "A", "B", new UserPreferences(true, false));

        String patch = JsonPatchGenerator.generatePatch(before, after);

        assertThat(patch).contains("\"op\":\"replace\"");
        assertThat(patch).contains("email");
        assertThat(patch).contains("new@b.com");
    }

    @Test
    @DisplayName("Should generate multiple operations for multiple changes")
    void should_generate_multiple_operations_for_multiple_changes() {
        User before = new User(ID, TENANT_ID, "old@b.com", "+1", "OldFirst", "OldLast", new UserPreferences(true, false));
        User after = new User(ID, TENANT_ID, "new@b.com", "+1", "NewFirst", "OldLast", new UserPreferences(true, false));

        String patch = JsonPatchGenerator.generatePatch(before, after);

        assertThat(patch).contains("email");
        assertThat(patch).contains("firstName");
        // lastName didn't change, should not be in the patch
        assertThat(patch).doesNotContain("lastName");
    }

    @Test
    @DisplayName("Should handle nested object changes")
    void should_handle_nested_object_changes() {
        User before = new User(ID, TENANT_ID, "a@b.com", "+1", "A", "B", new UserPreferences(true, false));
        User after = new User(ID, TENANT_ID, "a@b.com", "+1", "A", "B", new UserPreferences(true, true));

        String patch = JsonPatchGenerator.generatePatch(before, after);

        assertThat(patch).isNotEqualTo("[]");
        assertThat(patch).contains("smsEnabled");
    }
}

