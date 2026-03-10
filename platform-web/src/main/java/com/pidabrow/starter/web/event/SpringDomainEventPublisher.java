package com.pidabrow.starter.web.event;

import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based adapter for DomainEventPublisher port.
 * This is an outbound adapter that publishes events using Spring's ApplicationEventPublisher.
 * 
 * Events published through this adapter are dispatched synchronously within the
 * current transaction via {@code @EventListener} (see {@code IntegrationEventListener}).
 * This guarantees that the Outbox write and the business change share the same ACID
 * transaction, eliminating the Dual-Write problem described in ADR-007.
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    
    SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
    
    @Override
    public void publish(DomainEvent event) {
        if (event != null) {
            applicationEventPublisher.publishEvent(event);
        }
    }
}

