package com.pidabrow.starter.sample.infrastructure.persistence.entity;

import com.pidabrow.starter.data.entity.TenantScopedEntity;
import com.pidabrow.starter.sample.domain.user.NotificationRequest;
import com.pidabrow.starter.sample.domain.user.NotificationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for NotificationRequest.
 * This is an outbound adapter implementation detail.
 * Domain layer uses immutable NotificationRequest record.
 */
@Entity
@Table(name = "notification_requests")
public class NotificationRequestEntity extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected NotificationRequestEntity() {
        // Protected no-args constructor for JPA
    }

    private NotificationRequestEntity(
            UUID userId,
            String templateName,
            Map<String, Object> payload,
            NotificationStatus status,
            int retryCount) {
        this.userId = userId;
        this.templateName = templateName;
        this.payload = payload != null ? payload : Collections.emptyMap();
        this.status = status;
        this.retryCount = retryCount;
    }

    public static NotificationRequestEntity fromDomain(NotificationRequest notificationRequest) {
        NotificationRequestEntity entity = new NotificationRequestEntity(
                notificationRequest.userId(),
                notificationRequest.templateName(),
                notificationRequest.payload(),
                notificationRequest.status(),
                notificationRequest.retryCount()
        );
        entity.initId(notificationRequest.id());
        return entity;
    }

    public NotificationRequest toDomain() {
        return new NotificationRequest(
                getId(),
                getTenantId(),
                userId,
                templateName,
                payload != null ? payload : Collections.emptyMap(),
                status,
                retryCount
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }
}

