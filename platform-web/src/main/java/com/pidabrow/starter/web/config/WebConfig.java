package com.pidabrow.starter.web.config;

import com.pidabrow.starter.web.actor.ActorContextInterceptor;
import com.pidabrow.starter.web.correlation.CorrelationContextInterceptor;
import com.pidabrow.starter.web.tenant.TenantContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for web/REST layer.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Correlation ID should be set first for traceability
        registry.addInterceptor(new CorrelationContextInterceptor());
        registry.addInterceptor(new TenantContextInterceptor());
        registry.addInterceptor(new ActorContextInterceptor());
    }
}
