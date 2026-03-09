package com.pidabrow.starter.infrastructure.outbox;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Configuration for the Transactional Outbox infrastructure.
 * <p>
 * Activates only when {@code outbox.enabled=true} is set.
 * Enables scheduling and ShedLock for distributed lock coordination.
 * Uses JDBC-based lock provider backed by the {@code shedlock} table.
 */
@Configuration
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class OutboxConfiguration {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
