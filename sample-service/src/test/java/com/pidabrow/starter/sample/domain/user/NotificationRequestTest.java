package com.pidabrow.starter.sample.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain contract tests for the {@link NotificationRequest} record.
 *
 * These tests verify the domain invariants enforced by the compact constructor.
 * In the current HTTP flow, the NotificationRequest is only created internally
 * by CreateUserUseCase. However, the record is a public domain model with a public
 * constructor — these tests ensure it can never exist in an invalid state,
 * regardless of the caller.
 */
@DisplayName("NotificationRequest domain model contract tests")
class NotificationRequestTest {

    private static final UUID VALID_ID = UUID.randomUUID();
    private static final UUID VALID_TENANT_ID = UUID.randomUUID();
    private static final UUID VALID_USER_ID = UUID.randomUUID();
    private static final String VALID_TEMPLATE_NAME = "WELCOME_EMAIL";
    private static final Map<String, Object> VALID_PAYLOAD = Map.of("key", "value");

    @Test
    @DisplayName("Should create pending request with UUID v7")
    void should_create_pending_request_with_uuid_v7() {
        NotificationRequest request = NotificationRequest.pending(
                VALID_TENANT_ID, VALID_USER_ID, VALID_TEMPLATE_NAME, VALID_PAYLOAD
        );

        assertThat(request.id()).isNotNull();
        assertThat(request.id().version()).isEqualTo(7);
        assertThat(request.tenantId()).isEqualTo(VALID_TENANT_ID);
        assertThat(request.userId()).isEqualTo(VALID_USER_ID);
        assertThat(request.templateName()).isEqualTo(VALID_TEMPLATE_NAME);
        assertThat(request.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(request.retryCount()).isZero();
    }

    @Test
    @DisplayName("Should throw when id is null")
    void should_throw_when_id_is_null() {
        assertThatThrownBy(() -> new NotificationRequest(
                null, VALID_TENANT_ID, VALID_USER_ID,
                VALID_TEMPLATE_NAME, VALID_PAYLOAD, NotificationStatus.PENDING, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    @DisplayName("Should throw when tenantId is null")
    void should_throw_when_tenant_id_is_null() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, null, VALID_USER_ID,
                VALID_TEMPLATE_NAME, VALID_PAYLOAD, NotificationStatus.PENDING, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be null");
    }

    @Test
    @DisplayName("Should throw when userId is null")
    void should_throw_when_user_id_is_null() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, null,
                VALID_TEMPLATE_NAME, VALID_PAYLOAD, NotificationStatus.PENDING, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId must not be null");
    }

    @Test
    @DisplayName("Should throw when templateName is null")
    void should_throw_when_template_name_is_null() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                null, VALID_PAYLOAD, NotificationStatus.PENDING, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateName must not be blank");
    }

    @Test
    @DisplayName("Should throw when templateName is blank")
    void should_throw_when_template_name_is_blank() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                "  ", VALID_PAYLOAD, NotificationStatus.PENDING, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateName must not be blank");
    }

    @Test
    @DisplayName("Should throw when status is null")
    void should_throw_when_status_is_null() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                VALID_TEMPLATE_NAME, VALID_PAYLOAD, null, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status must not be null");
    }

    @Test
    @DisplayName("Should throw when retryCount is negative")
    void should_throw_when_retry_count_is_negative() {
        assertThatThrownBy(() -> new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                VALID_TEMPLATE_NAME, VALID_PAYLOAD, NotificationStatus.PENDING, -1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryCount must be >= 0");
    }

    @Test
    @DisplayName("Should default to empty map when payload is null")
    void should_default_to_empty_map_when_payload_is_null() {
        NotificationRequest request = new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                VALID_TEMPLATE_NAME, null, NotificationStatus.PENDING, 0
        );

        assertThat(request.payload()).isNotNull();
        assertThat(request.payload()).isEmpty();
    }

    @Test
    @DisplayName("Should make payload unmodifiable")
    void should_make_payload_unmodifiable() {
        Map<String, Object> mutablePayload = new HashMap<>();
        mutablePayload.put("key", "value");

        NotificationRequest request = new NotificationRequest(
                VALID_ID, VALID_TENANT_ID, VALID_USER_ID,
                VALID_TEMPLATE_NAME, mutablePayload, NotificationStatus.PENDING, 0
        );

        assertThatThrownBy(() -> request.payload().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

