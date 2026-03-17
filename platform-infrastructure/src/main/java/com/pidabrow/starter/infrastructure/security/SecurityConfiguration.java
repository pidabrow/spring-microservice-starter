package com.pidabrow.starter.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Configuration for security infrastructure adapters.
 * Provides BCrypt password encoder with cost factor 12 as per ADR-008.
 */
@Configuration
class SecurityConfiguration {
    
    /**
     * BCrypt password encoder with cost factor 12.
     * This provides ~250-500ms delay per check to thwart brute-force attacks.
     * Exposed as BCryptPasswordEncoder so the adapter can inject it without casting.
     */
    @Bean
    BCryptPasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

