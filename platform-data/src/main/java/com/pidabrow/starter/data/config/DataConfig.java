package com.pidabrow.starter.data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration for JPA and database access.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.pidabrow.starter")
@EnableTransactionManagement
public class DataConfig {
    // Additional data configuration can be added here
}
