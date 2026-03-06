package com.pidabrow.starter.infrastructure.outbox;

import com.pidabrow.starter.common.event.*;
import com.pidabrow.starter.common.uuid.UuidV7Generator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for {@link DomainEvent}s and maps them into {@link MessageOutboxEntity} records.
 * <p>
 * <strong>Critical</strong>: Uses {@code @EventListener} (not {@code @TransactionalEventListener(AFTER_COMMIT)})
 * so that the outbox write occurs within the <em>same</em> ACID transaction as the business change.
 * This eliminates the "Dual Write" problem described in ADR-007.
 * <p>
 * This is a package-private infrastructure adapter.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
class IntegrationEventListener {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventListener.class);
    private static final String DEFAULT_DESTINATION = "domain-events";

    private final MessageOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    IntegrationEventListener(MessageOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    void handleDomainEvent(DomainEvent event) {
        String messageType = resolveMessageType(event);
        String originEventType = event.getClass().getName();
        String destination = resolveDestination(event);
        String partitionKey = event.entityId().toString();
        UUID tenantId = event.tenantId();

        Map<String, Object> payload = serializeEvent(event);

        Map<String, String> headers = Map.of(
                "x-tenant-id", tenantId.toString(),
                "x-message-type", messageType,
                "x-correlation-id", UuidV7Generator.generate().toString()
        );

        MessageOutboxEntity outboxRecord = MessageOutboxEntity.create(
                tenantId,
                messageType,
                originEventType,
                destination,
                partitionKey,
                payload,
                headers
        );

        outboxRepository.save(outboxRecord);
        log.debug("Outbox record created: type={}, destination={}, entityId={}",
                messageType, destination, partitionKey);
    }

    private String resolveMessageType(DomainEvent event) {
        return switch (event) {
            case UserCreatedEvent ignored -> "USER_CREATED";
            case UserUpdatedEvent ignored -> "USER_UPDATED";
            case UserDeletedEvent ignored -> "USER_DELETED";
            case EntityCreatedEvent ignored -> "ENTITY_CREATED";
            case EntityUpdatedEvent ignored -> "ENTITY_UPDATED";
            case NotificationRequestedEvent ignored -> "NOTIFICATION_REQUESTED";
        };
    }

    private String resolveDestination(DomainEvent event) {
        return switch (event) {
            case NotificationRequestedEvent ignored -> "notification-events";
            default -> DEFAULT_DESTINATION;
        };
    }

    private Map<String, Object> serializeEvent(DomainEvent event) {
        return objectMapper.convertValue(event, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}
