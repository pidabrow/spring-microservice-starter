package com.pidabrow.starter.data.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP aspect that automatically enables tenant filter for all @Transactional methods.
 * This ensures tenant isolation at the persistence layer without requiring
 * explicit filtering in application or domain layers.
 * 
 * Note: While this uses broad AOP (which is generally avoided per cursor rules),
 * it is justified here as a **security boundary**. Tenant isolation must be
 * automatic and foolproof to prevent data leakage. This is not business logic,
 * but infrastructure-level enforcement of a critical security constraint.
 */
@Aspect
@Component
class TenantFilterAspect {
    
    private final TenantFilterTransactionManager tenantFilterTransactionManager;
    
    public TenantFilterAspect(TenantFilterTransactionManager tenantFilterTransactionManager) {
        this.tenantFilterTransactionManager = tenantFilterTransactionManager;
    }
    
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        tenantFilterTransactionManager.enableTenantFilterIfNeeded();
        return joinPoint.proceed();
    }
}

