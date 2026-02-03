package com.pidabrow.starter.data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for tenant filter.
 * Tenant filter is automatically enabled via AOP aspect for all @Transactional methods.
 */
@Configuration
@EnableAspectJAutoProxy
public class TenantFilterConfig {
    // Tenant filter is enabled via TenantFilterAspect
}

