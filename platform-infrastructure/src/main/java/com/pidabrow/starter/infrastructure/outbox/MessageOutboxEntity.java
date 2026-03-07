package com.pidabrow.starter.infrastructure.outbox;

import com.pidabrow.starter.common.uuid.UuidV7Generator;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing a record in the transactional outbox table.
 * <p>
 * This entity is package-private — only infrastructure adapters within this package
 * may interact with it directly.
 * <p>
 * Follows ADR-007: UUID v7 PK, DB-driven timestamps, JSONB payload/headers.
 */
@Entity
@Table(name = "message_outbox")
class MessageOutboxEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "message_type", nullable = false, updatable = false)
    private String messageType;

    @Column(name = "origin_event_type", updatable = false)
    private String originEventType;

    @Column(name = "destination", nullable = false, updatable = false)
    private String destination;

    @Column(name = "partition_key", nullable = false, updatable = false)
    private String partitionKey;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "headers", updatable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected MessageOutboxEntity() {
        // Protected no-args constructor for JPA
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generate();
        }
        if (status == null) {
            status = MessageOutboxStatus.PENDING;
        }
    }

    // --- Static Factory ---

    static MessageOutboxEntity create(
            UUID tenantId,
            String messageType,
            String originEventType,
            String destination,
            String partitionKey,
            Map<String, Object> payload,
            Map<String, String> headers) {

        var entity = new MessageOutboxEntity();
        entity.id = UuidV7Generator.generate();
        entity.tenantId = tenantId;
        entity.messageType = messageType;
        entity.originEventType = originEventType;
        entity.destination = destination;
        entity.partitionKey = partitionKey;
        entity.payload = payload;
        entity.headers = headers;
        entity.status = MessageOutboxStatus.PENDING;
        entity.retryCount = 0;
        return entity;
    }

    // --- Business Methods (no public setters) ---

    void markSent() {
        this.status = MessageOutboxStatus.SENT;
        this.processedAt = OffsetDateTime.now();
    }

    void markFailed(String error) {
        this.status = MessageOutboxStatus.FAILED;
        this.lastError = error;
    }

    void recordRetryFailure(String error) {
        this.retryCount++;
        this.lastError = error;
    }

    boolean hasExceededMaxRetries(int maxRetries) {
        return retryCount >= maxRetries;
    }

    // --- Getters ---

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getMessageType() {
        return messageType;
    }

    String getOriginEventType() {
        return originEventType;
    }

    String getDestination() {
        return destination;
    }

    String getPartitionKey() {
        return partitionKey;
    }

    Map<String, Object> getPayload() {
        return payload;
    }

    Map<String, String> getHeaders() {
        return headers;
    }

    MessageOutboxStatus getStatus() {
        return status;
    }

    int getRetryCount() {
        return retryCount;
    }

    String getLastError() {
        return lastError;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}

