package com.pidabrow.starter.common.correlation;

import java.util.UUID;

/**
 * Immutable correlation context representing the correlation ID for a request.
 * Used for end-to-end traceability across distributed systems.
 */
public record CorrelationContext(UUID correlationId) {
    
    public CorrelationContext {
        if (correlationId == null) {
            throw new IllegalArgumentException("Correlation ID cannot be null");
        }
    }
    
    public static CorrelationContext of(UUID correlationId) {
        return new CorrelationContext(correlationId);
    }
}

