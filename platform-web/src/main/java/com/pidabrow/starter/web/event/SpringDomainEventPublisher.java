package com.pidabrow.starter.web.event;

import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based adapter for DomainEventPublisher port.
 * This is an outbound adapter that publishes events using Spring's ApplicationEventPublisher.
 * 
 * Events published through this adapter will be handled by listeners using
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT).
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

