package com.pidabrow.starter.data.audit;

import com.pidabrow.starter.common.actor.ActorContext;
import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.SystemActor;
import com.pidabrow.starter.common.event.DomainEvent;
import com.pidabrow.starter.data.entity.AuditLog;
import com.pidabrow.starter.data.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener for domain events that creates audit log entries.
 * 
 * This listener uses AFTER_COMMIT phase to ensure audit entries are only
 * written if the business transaction commits successfully.
 * No "ghost" audit entries are allowed on rollback.
 * 
 * This is an outbound adapter that handles events from the application layer.
 */
@Component
class AuditLogListener {
    
    private final AuditLogRepository auditLogRepository;
    
    AuditLogListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleDomainEvent(DomainEvent event) {
        ActorContext actorContext = ActorContextHolder.getContext();
        
        // Default to SYSTEM if no actor context is set
        if (actorContext == null) {
            actorContext = SystemActor.instance();
        }
        
        AuditLog auditLog = AuditLog.fromEvent(event, actorContext);
        auditLogRepository.save(auditLog);
    }
}

