package com.pidabrow.starter.data.config;

import com.pidabrow.starter.common.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Transaction synchronization that enables tenant filter at the start of each transaction.
 * This ensures tenant isolation at the persistence layer.
 */
@Component
public class TenantFilterTransactionManager {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * Enables tenant filter for the current transaction if tenant context is set.
     * Should be called at the start of each transaction.
     */
    public void enableTenantFilterIfNeeded() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TenantFilterSynchronization(tenantId));
            // Enable filter immediately for current session
            if (entityManager != null) {
                enableFilter(entityManager, tenantId);
            }
        }
    }
    
    private void enableFilter(EntityManager entityManager, UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
    }
    
    private class TenantFilterSynchronization implements TransactionSynchronization {
        private final UUID tenantId;
        
        public TenantFilterSynchronization(UUID tenantId) {
            this.tenantId = tenantId;
        }
        
        @Override
        public void beforeCommit(boolean readOnly) {
            if (entityManager != null) {
                enableFilter(entityManager, tenantId);
            }
        }
    }
}

