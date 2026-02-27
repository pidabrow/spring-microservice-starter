package com.pidabrow.starter.sample.domain.user;

import com.pidabrow.starter.common.uuid.UuidV7Generator;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable domain model representing a notification outbox entry.
 */
public record NotificationRequest(
        UUID id,
        UUID tenantId,
        UUID userId,
        String templateName,
        Map<String, Object> payload,
        NotificationStatus status,
        int retryCount
) {

    public NotificationRequest {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName must not be blank");
        }
        if (payload == null) {
            payload = Collections.emptyMap();
        }
        payload = Collections.unmodifiableMap(payload);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be >= 0");
        }
    }

    /**
     * Factory method for creating a new pending notification request.
     */
    public static NotificationRequest pending(
            UUID tenantId,
            UUID userId,
            String templateName,
            Map<String, Object> payload
    ) {
        UUID id = UuidV7Generator.generate();
        Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;
        return new NotificationRequest(
                id,
                tenantId,
                userId,
                templateName,
                safePayload,
                NotificationStatus.PENDING,
                0
        );
    }
}


