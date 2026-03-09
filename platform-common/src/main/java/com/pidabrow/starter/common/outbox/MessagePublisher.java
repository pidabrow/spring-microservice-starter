package com.pidabrow.starter.common.outbox;

import java.util.Map;

/**
 * Outbound port for publishing messages to external systems (e.g., Kafka).
 * <p>
 * Implementations reside in infrastructure adapters and must be package-private.
 * This port enables the Transactional Outbox pattern (ADR-007) by decoupling
 * the relay service from the concrete messaging technology.
 */
public interface MessagePublisher {

    /**
     * Publishes a message to the specified destination.
     *
     * @param destination  the target topic or queue name
     * @param key          the partition key for ordering guarantees
     * @param payload      the message body as a map
     * @param headers      message headers (e.g., x-tenant-id, x-message-type)
     */
    void publish(String destination, String key, Map<String, Object> payload, Map<String, String> headers);
}

