package com.pidabrow.starter.infrastructure.outbox;

/**
 * Status of an outbox message in the state machine.
 * <p>
 * Flow: PENDING → SENT (on Kafka ACK) or PENDING → FAILED (after max retries).
 */
enum MessageOutboxStatus {
    PENDING,
    SENT,
    FAILED
}

