package com.pidabrow.starter.web.tenant;

import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Interceptor that resolves tenant context from HTTP header X-Tenant-Id.
 * This is an inbound adapter responsibility per hexagonal architecture.
 */
public class TenantContextInterceptor implements HandlerInterceptor {
    
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        
        try {
            UUID tenantId = UUID.fromString(tenantIdHeader);
            TenantContext context = TenantContext.of(tenantId);
            TenantContextHolder.setContext(context);
            return true;
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
    }
    
    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex) {
        // Clear context to prevent thread-local leakage
        TenantContextHolder.clearContext();
    }
}

