package com.pidabrow.starter.web.correlation;

import com.pidabrow.starter.common.correlation.CorrelationContext;
import com.pidabrow.starter.common.correlation.CorrelationContextHolder;
import com.pidabrow.starter.common.uuid.UuidV7Generator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Interceptor that resolves correlation context from HTTP header X-Correlation-Id.
 * If the header is not present, generates a new UUID v7.
 * This is an inbound adapter responsibility per hexagonal architecture.
 */
public class CorrelationContextInterceptor implements HandlerInterceptor {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        
        String correlationIdHeader = request.getHeader(CORRELATION_ID_HEADER);
        
        UUID correlationId;
        if (correlationIdHeader == null || correlationIdHeader.isBlank()) {
            // Generate new correlation ID if not provided
            correlationId = UuidV7Generator.generate();
        } else {
            try {
                correlationId = UUID.fromString(correlationIdHeader);
            } catch (IllegalArgumentException e) {
                // Invalid UUID format, generate new one
                correlationId = UuidV7Generator.generate();
            }
        }
        
        CorrelationContext context = CorrelationContext.of(correlationId);
        CorrelationContextHolder.setContext(context);
        return true;
    }
    
    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex) {
        // Clear context to prevent thread-local leakage
        CorrelationContextHolder.clearContext();
    }
}

