package com.pidabrow.starter.web.actor;

import com.pidabrow.starter.common.actor.ActorContext;
import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.SystemActor;
import com.pidabrow.starter.common.actor.UserActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Interceptor that resolves actor context from HTTP headers.
 * This is an inbound adapter responsibility per hexagonal architecture.
 * 
 * MVP implementation:
 * - X-Actor-Type: SYSTEM | USER (missing = SYSTEM)
 * - X-Actor-Id: <uuid> required for USER
 * 
 * Note: Header-based resolution is MVP only.
 * JWT-based authentication will replace it in a future iteration.
 * Headers must never be trusted in production.
 */
public class ActorContextInterceptor implements HandlerInterceptor {
    
    private static final String ACTOR_TYPE_HEADER = "X-Actor-Type";
    private static final String ACTOR_ID_HEADER = "X-Actor-Id";
    
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        
        String actorTypeHeader = request.getHeader(ACTOR_TYPE_HEADER);
        String actorIdHeader = request.getHeader(ACTOR_ID_HEADER);
        
        ActorContext context;
        
        if (actorTypeHeader == null || actorTypeHeader.isBlank() || "SYSTEM".equalsIgnoreCase(actorTypeHeader)) {
            // Default to SYSTEM if not specified
            context = SystemActor.instance();
        } else if ("USER".equalsIgnoreCase(actorTypeHeader)) {
            // USER requires actor ID
            if (actorIdHeader == null || actorIdHeader.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
            try {
                UUID userId = UUID.fromString(actorIdHeader);
                context = UserActor.of(userId);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        
        ActorContextHolder.setContext(context);
        return true;
    }
    
    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex) {
        // Clear context to prevent thread-local leakage
        ActorContextHolder.clearContext();
    }
}

