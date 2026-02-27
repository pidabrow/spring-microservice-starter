package com.pidabrow.starter.common.event;

/**
 * Outbound port for publishing domain events.
 * Implementations should publish events after successful transaction commit.
 * 
 * This is a port interface following hexagonal architecture principles.
 * The implementation resides in the outbound adapter (platform-web).
 */
public interface DomainEventPublisher {
    
    /**
     * Publishes a domain event.
     * The event will be handled by listeners after transaction commit.
     * 
     * @param event the domain event to publish
     */
    void publish(DomainEvent event);
}

